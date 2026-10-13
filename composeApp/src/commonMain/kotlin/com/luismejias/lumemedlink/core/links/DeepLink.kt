package com.luismejias.lumemedlink.core.links

import com.luismejias.lumemedlink.shared.OpaqueRef
import io.ktor.http.URLProtocol
import io.ktor.http.Url

/**
 * A link the catalog recognised, with the one value it needs already validated (ADR-0053). There is no way to build
 * one with a malformed value: [ref] is an [OpaqueRef] or nothing.
 */
internal data class DeepLink(val entry: DeepLinkEntry, val ref: OpaqueRef? = null) {
    init {
        require((entry.requires == null) == (ref == null)) { "a ${entry.name} link carries exactly what it requires" }
    }

    /** The path and query this link is published as, relative to the app's domain. */
    val relative: String
        get() = entry.requires?.let { param -> "${entry.path}?${param.queryName}=${ref?.value}" } ?: entry.path

    override fun toString(): String = "DeepLink(${entry.name})"
}

/** Path segments that never belong to a catalogued path, whatever their encoding. */
private val TRAVERSAL = setOf(".", "..")

/**
 * Reads a universal link or App Link (ADR-0053 §1–2). Answers `null` for anything that is not, exactly, one of the
 * catalog's paths on one of [hosts] over https:
 *
 * - https only: no http, no custom scheme (§8.12), no other port;
 * - the host matched exactly against [hosts], which is empty until the production domain exists (task `0011`);
 * - no user info, so `https://lume.cl@evil.test/…` is not read as the app's host;
 * - the path matched exactly, with no traversal and no encoded slash smuggling a second segment;
 * - the required value present once, as an opaque id; any other query parameter (a campaign's `utm_*`) is ignored.
 *
 * `null` means "not ours": the caller lands nowhere, never somewhere near.
 */
internal fun parseDeepLink(url: String, hosts: Set<String>): DeepLink? {
    val parsed = runCatching { Url(url) }.getOrNull() ?: return null
    val acceptable = parsed.protocol == URLProtocol.HTTPS &&
        parsed.port == URLProtocol.HTTPS.defaultPort &&
        parsed.user == null &&
        parsed.host.lowercase() in hosts.map { it.lowercase() }
    if (!acceptable) return null
    return linkOf(parsed.encodedPath, parsed.parameters.names()) { name -> parsed.parameters.getAll(name) }
}

/**
 * Reads the link a push payload carries (ADR-0053 §5): the same catalog, relative (`citas/detalle?cita=…`) because
 * the payload comes over FCM or APNs, not from a browser. Same rules as [parseDeepLink] on the path and the query.
 */
internal fun parseRelativeLink(relative: String): DeepLink? {
    if (relative.contains("://") || relative.startsWith("//")) return null
    val parsed = runCatching { Url("https://relative.invalid/${relative.trimStart('/')}") }.getOrNull() ?: return null
    return linkOf(parsed.encodedPath, parsed.parameters.names()) { name -> parsed.parameters.getAll(name) }
}

private fun linkOf(encodedPath: String, names: Set<String>, values: (String) -> List<String>?): DeepLink? {
    // An encoded slash or dot would let one segment pose as two, or climb out of the path.
    if (encodedPath.contains("%2f", ignoreCase = true) || encodedPath.contains("%2e", ignoreCase = true)) return null
    val segments = encodedPath.trim('/').split('/')
    if (segments.any { it.isEmpty() || it in TRAVERSAL }) return null
    val entry = DeepLinkEntry.matching(segments.joinToString("/")) ?: return null
    val param = entry.requires ?: return DeepLink(entry)
    if (param.queryName !in names) return null
    val given = values(param.queryName).orEmpty()
    // Twice is ambiguous, and an ambiguous link opens nothing.
    val ref = given.singleOrNull()?.let(OpaqueRef::parseOrNull) ?: return null
    return DeepLink(entry, ref)
}
