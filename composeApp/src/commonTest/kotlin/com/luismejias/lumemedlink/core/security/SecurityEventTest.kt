package com.luismejias.lumemedlink.core.security

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

class SecurityEventTest {

    @Test
    fun everyKindNamesAClassOfEventAndNotAnInstance() {
        // The shape check that keeps the channel opaque: an enum constant cannot carry a name, a
        // RUT or a record id, and this asserts nobody smuggles one in as a constant.
        SecurityEventKind.entries.forEach { kind ->
            assertTrue(
                Regex("^[A-Z][A-Z0-9_]*$").matches(kind.name),
                "a kind must be an opaque constant, got '${kind.name}'",
            )
            assertTrue(kind.name.length <= 40, "a kind long enough to hold a sentence is a leak: ${kind.name}")
        }
    }

    @Test
    fun theStandInReportsNothingAndDoesNotThrow() = runTest {
        // Both halves matter. Nothing is reported — that is the declared gap — and it does not
        // throw, because §8.16's fail direction is that a caller applying a protection must never
        // be interrupted by this channel.
        SecurityEventKind.entries.forEach { NoOpSecurityEventReporter.report(it) }
    }
}
