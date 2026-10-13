package com.audiocast.app.streaming

import kotlin.math.min
import kotlin.math.pow

/**
 * Pure reconnect policy: bounded attempts with exponential backoff.
 *
 * [maxAttempts] <= 0 means "never retry".
 */
class ReconnectPolicy(
    val maxAttempts: Int,
    val baseDelayMs: Long = 1_000,
    val maxDelayMs: Long = 30_000,
    val factor: Double = 2.0,
) {
    /** Whether attempt number [attempt] (1-based) is allowed. */
    fun shouldRetry(attempt: Int): Boolean = attempt in 1..maxAttempts

    /** Delay before attempt number [attempt] (1-based). */
    fun delayFor(attempt: Int): Long {
        if (attempt <= 0) return 0
        val exp = baseDelayMs * factor.pow((attempt - 1).toDouble())
        return min(exp.toLong(), maxDelayMs)
    }
}
