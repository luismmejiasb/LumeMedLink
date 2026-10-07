package com.luismejias.lumemedlink.core.networking

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.IntVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.sizeOf
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import platform.Foundation.NSURLRequestReloadIgnoringCacheData
import platform.Foundation.NSURLSessionAuthChallengeCancelAuthenticationChallenge
import platform.Foundation.NSURLSessionConfiguration
import platform.posix.AF_INET
import platform.posix.POLLIN
import platform.posix.SOCK_STREAM
import platform.posix.SOL_SOCKET
import platform.posix.SO_NOSIGPIPE
import platform.posix.accept
import platform.posix.bind
import platform.posix.getsockname
import platform.posix.listen
import platform.posix.poll
import platform.posix.pollfd
import platform.posix.recv
import platform.posix.send
import platform.posix.setsockopt
import platform.posix.sockaddr_in
import platform.posix.socket
import platform.posix.socklen_tVar
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import platform.posix.close as closeSocket

/** 127.0.0.1 as `s_addr` holds it: network byte order, read on a little-endian host. */
private const val LOOPBACK_S_ADDR: UInt = 0x0100007Fu
private const val WAIT_MILLIS = 10_000
private const val HEAD_LIMIT = 4096

private const val BASIC_CHALLENGE =
    "HTTP/1.1 401 Unauthorized\r\n" +
        "WWW-Authenticate: Basic realm=\"lume-posture-test\"\r\n" +
        "Content-Length: 0\r\n" +
        "Connection: close\r\n\r\n"

/**
 * The NSURLSession posture, measured on the session the REAL engine creates (task 0004).
 *
 * ## Why not just call `applyLumeSessionPosture()` on a configuration and read it back
 *
 * That would test a function this file can see, and miss the thing that can actually break it: the
 * session is assembled inside Ktor, which takes `defaultSessionConfiguration`, applies its own
 * settings, and THEN runs ours (`setupProxy`, `setHTTPCookieStorage`, our block — in that order in
 * Ktor 3.5.2's `createSession`). An upgrade that moved one of its own settings after our block would
 * pass every test of our function and silently undo it.
 *
 * So this asks the session itself. The only object Ktor hands back from the live `NSURLSession` is
 * the one it passes to a challenge handler — so a loopback server answers the request with an HTTP
 * Basic challenge, the handler receives the real session, records `session.configuration`, and
 * cancels. Nothing leaves the device: the server is on 127.0.0.1 and the request is plain HTTP to an
 * IP address, which ATS does not govern.
 *
 * ## What it can and cannot see
 *
 * Removing any of our assignments turns it red EXCEPT the cookie store, which Ktor already nulls
 * before our block runs — there our line is insurance, and this test is what notices the day the
 * insurance becomes the only thing standing. Seen red, one assignment at a time, when it was
 * written (bitácora 0037).
 */
class DarwinSessionPostureTest {

    @Test
    fun theSessionTheRealEngineCreatesCarriesNoSharedPersistentStore() = runTest {
        val configuration = sessionConfigurationProducedByTheRealEngine()

        assertNull(
            configuration.URLCache,
            "URLCache.shared writes the Authorization header to Library/Caches in plaintext (F12, ADR-0016)",
        )
        assertNull(
            configuration.URLCredentialStorage,
            "the shared NSURLCredentialStorage is a persistent system store nothing here should feed",
        )
        assertNull(
            configuration.HTTPCookieStorage,
            "a cookie store would outlive logout, which knows nothing about it (ADR-0014)",
        )
        assertFalse(
            configuration.HTTPShouldSetCookies,
            "cookies are not this stack's to send",
        )
        assertEquals(
            NSURLRequestReloadIgnoringCacheData,
            configuration.requestCachePolicy,
            "the read-side half of the cache posture",
        )
    }

    @OptIn(ExperimentalForeignApi::class)
    private suspend fun sessionConfigurationProducedByTheRealEngine(): NSURLSessionConfiguration =
        // OFF the main thread on purpose: Ktor picks the main queue as the session's delegate queue when
        // the engine is built on the main thread (read in its IR, not measured), and this test's thread
        // would be busy waiting for it.
        withContext(Dispatchers.Default) {
            val server = LoopbackChallengeServer()
            val captured = CompletableDeferred<NSURLSessionConfiguration>()
            try {
                val serving = async { server.answerOneWithBasicChallenge() }
                val client = HttpClient(
                    lumeDarwinEngine {
                        handleChallenge { session, _, _, completionHandler ->
                            captured.complete(session.configuration)
                            completionHandler(NSURLSessionAuthChallengeCancelAuthenticationChallenge, null)
                        }
                    },
                )
                try {
                    // The request is EXPECTED to fail: the challenge is cancelled once observed.
                    runCatching { client.get("http://127.0.0.1:${server.port}/posture") }
                } finally {
                    client.close()
                }
                assertTrue(serving.await(), "the loopback server never received the request")
                withTimeout(WAIT_MILLIS.toLong()) { captured.await() }
            } finally {
                server.close()
            }
        }
}

/** One-shot HTTP/1.1 server on 127.0.0.1 that answers a single request with a Basic challenge. */
@OptIn(ExperimentalForeignApi::class)
private class LoopbackChallengeServer {
    private val listener: Int = socket(AF_INET, SOCK_STREAM, 0)
    val port: Int

    init {
        check(listener >= 0) { "socket() failed" }
        port = memScoped {
            val address = alloc<sockaddr_in>()
            address.sin_len = sizeOf<sockaddr_in>().convert()
            address.sin_family = AF_INET.convert()
            address.sin_port = 0u
            address.sin_addr.s_addr = LOOPBACK_S_ADDR
            check(bind(listener, address.ptr.reinterpret(), sizeOf<sockaddr_in>().convert()) == 0) { "bind() failed" }
            check(listen(listener, 1) == 0) { "listen() failed" }
            val length = alloc<socklen_tVar>()
            length.value = sizeOf<sockaddr_in>().convert()
            check(getsockname(listener, address.ptr.reinterpret(), length.ptr) == 0) { "getsockname() failed" }
            networkToHostOrder(address.sin_port)
        }
    }

    /** Blocks for at most [WAIT_MILLIS] waiting for ONE connection; true when it was answered. */
    fun answerOneWithBasicChallenge(): Boolean = memScoped {
        val waiting = alloc<pollfd>()
        waiting.fd = listener
        waiting.events = POLLIN.convert()
        if (poll(waiting.ptr, 1u, WAIT_MILLIS) <= 0) return@memScoped false
        val client = accept(listener, null, null)
        if (client < 0) return@memScoped false
        // A client that hangs up early must cost this test an error code, not the whole process.
        val noSigPipe = alloc<IntVar>()
        noSigPipe.value = 1
        setsockopt(client, SOL_SOCKET, SO_NOSIGPIPE, noSigPipe.ptr, sizeOf<IntVar>().convert())
        readRequestHead(client)
        val answer = BASIC_CHALLENGE.encodeToByteArray()
        answer.usePinned { send(client, it.addressOf(0), answer.size.convert(), 0) }
        closeSocket(client)
        true
    }

    fun close() {
        closeSocket(listener)
    }

    private fun readRequestHead(fd: Int) {
        val buffer = ByteArray(HEAD_LIMIT)
        var total = 0
        buffer.usePinned { pinned ->
            while (total < buffer.size) {
                val read = recv(fd, pinned.addressOf(total), (buffer.size - total).convert(), 0)
                if (read <= 0) break
                total += read.toInt()
                if (buffer.decodeToString(0, total).contains("\r\n\r\n")) break
            }
        }
    }

    private fun networkToHostOrder(port: UShort): Int {
        val value = port.toInt()
        return ((value and 0xFF) shl 8) or ((value shr 8) and 0xFF)
    }
}
