package com.audiocast.app.streaming

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReconnectPolicyTest {

    private val policy = ReconnectPolicy(maxAttempts = 4, baseDelayMs = 1000, maxDelayMs = 10_000)

    @Test
    fun `retry allowed only within max attempts`() {
        assertTrue(policy.shouldRetry(1))
        assertTrue(policy.shouldRetry(4))
        assertFalse(policy.shouldRetry(5))
        assertFalse(policy.shouldRetry(0))
    }

    @Test
    fun `delays grow exponentially`() {
        assertEquals(1000, policy.delayFor(1))
        assertEquals(2000, policy.delayFor(2))
        assertEquals(4000, policy.delayFor(3))
    }

    @Test
    fun `delay is capped at max`() {
        assertEquals(10_000, policy.delayFor(10))
        assertEquals(10_000, policy.delayFor(100))
    }

    @Test
    fun `zero attempts means never retry`() {
        val none = ReconnectPolicy(maxAttempts = 0)
        assertFalse(none.shouldRetry(1))
    }
}
