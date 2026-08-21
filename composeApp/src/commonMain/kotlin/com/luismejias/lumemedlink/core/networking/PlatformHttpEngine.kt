package com.luismejias.lumemedlink.core.networking

import io.ktor.client.engine.HttpClientEngine

/**
 * The production engine, one per platform (ADR-0008: expect/actual lives in core/ only).
 * Tests never touch this — they inject MockEngine into [lumeHttpClient] directly.
 */
internal expect fun platformHttpEngine(): HttpClientEngine
