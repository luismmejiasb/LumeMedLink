package com.luismejias.lumemedlink.core.networking

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

// All identifiers below are synthetic (§9).

class RedactPathTest {

    @Test
    fun theRouteThisAppWillActuallyCallLosesItsIdentifiers() {
        // The shape the backend already publishes. Logging it raw would put a patient identifier
        // in every log line — the leak the redacting sink exists to prevent.
        assertEquals(
            "/v1/orgs/{id}/patients/{id}/appointments",
            redactPath("/v1/orgs/7f3e9c21-4b6a-4d2f-9e18-5c7a0b1d2e3f/patients/01JQ8Z4KM9/appointments"),
        )
    }

    @Test
    fun plainRouteWordsSurviveSoTheLogStaysUseful() {
        assertEquals("/v1/me", redactPath("/v1/me"))
        assertEquals("/v1/security-events", redactPath("/v1/security-events"))
        assertEquals("/v1/app-availability", redactPath("/v1/app-availability"))
    }

    @Test
    fun anythingIdentifierShapedIsRedacted() {
        assertEquals("/v1/patients/{id}", redactPath("/v1/patients/11111111-1"), "a RUT")
        assertEquals("/v1/patients/{id}", redactPath("/v1/patients/4821"), "a numeric id")
        assertEquals("/v1/{id}", redactPath("/v1/Perez"), "a capitalised surname")
        assertEquals("/v1/files/{id}", redactPath("/v1/files/aGVsbG8gd29ybGQ="), "an opaque blob")
    }

    @Test
    fun theAllowlistFailsSafeOnSegmentsItDoesNotRecognise() {
        // An unknown segment is redacted rather than kept: a new identifier shape must not slip
        // through because nobody remembered to add it to a list of things to hide.
        assertEquals("/v1/{id}/{id}", redactPath("/v1/unknown_thing/Mixed123"))
    }

    @Test
    fun structureIsPreservedSoTheLogStillLocatesTheCall() {
        assertEquals("/", redactPath("/"))
        assertEquals("", redactPath(""))
        assertEquals("/v1/orgs/{id}/", redactPath("/v1/orgs/abc123/"))
    }

    @Test
    fun theEntryCannotBeBuiltWithARawPath() {
        // The constructor is private; `of` is the only door, and it redacts on the way in.
        val entry = NetworkLogEntry.of("GET", "/v1/patients/11111111-1", 200, "rid-synthetic")

        assertEquals("/v1/patients/{id}", entry.path)
        assertFalse(entry.toString().contains("11111111"))
    }
}
