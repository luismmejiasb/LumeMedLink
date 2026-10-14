package com.luismejias.lumemedlink.core.networking

import io.ktor.client.engine.HttpClientEngine

/**
 * The production engine, one per platform (ADR-0008: expect/actual lives in core/ only).
 * Tests never touch this — they inject MockEngine into [lumeHttpClient] directly.
 */
internal expect fun platformHttpEngine(): HttpClientEngine

/**
 * The engine for the platform on the developer's computer only (ADR-0062), built by `LocalPatientBackend` and nothing
 * else. [platformHttpEngine] stays TLS-only underneath the stack, as a second refusal of plain http; this one lets the
 * local backend's plain http through, toward the hosts the stack's [TransportPolicy.LocalCleartext] already narrowed.
 */
internal expect fun localBackendHttpEngine(): HttpClientEngine
