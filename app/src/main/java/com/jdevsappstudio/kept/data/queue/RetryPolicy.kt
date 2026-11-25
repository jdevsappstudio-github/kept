package com.jdevsappstudio.kept.data.queue

import com.jdevsappstudio.kept.data.ai.ReadFailure
import com.jdevsappstudio.kept.data.ai.ReadFailure.Retry

/** What the queue does after a failed read (BUILD-SPEC §4). Pure, unit-tested. */
sealed interface RetryDecision {
    /** Keep the save QUEUED until [at]; [attempts] is what to store (waits that aren't the save's fault don't count). */
    data class Wait(val at: Long, val attempts: Int, val pauseQueue: Boolean) : RetryDecision
    /** "Needs a look" with the reason; the raw save is kept. [stopQueue]: every other read would fail the same way. */
    data class GiveUp(val attempts: Int, val stopQueue: Boolean) : RetryDecision
}

object RetryPolicy {
    const val MAX_ATTEMPTS = 3
    private const val BACKOFF_START_MS = 2_000L
    private const val BACKOFF_MAX_MS = 60_000L
    private const val LATER_MS = 60 * 60_000L

    /** [attempts] = tries before this one. */
    fun decide(reason: ReadFailure, attempts: Int, now: Long): RetryDecision {
        val tried = attempts + 1
        return when (reason.retry) {
            Retry.WHEN_ONLINE -> RetryDecision.Wait(now, attempts, pauseQueue = true)
            Retry.LATER -> RetryDecision.Wait(now + LATER_MS, attempts, pauseQueue = true)
            Retry.NEVER -> RetryDecision.GiveUp(
                tried,
                stopQueue = reason == ReadFailure.NOT_SET_UP || reason == ReadFailure.MODEL_GONE,
            )
            Retry.ONCE -> if (tried >= 2) RetryDecision.GiveUp(tried, false) else RetryDecision.Wait(now, tried, false)
            Retry.BACKOFF ->
                if (tried >= MAX_ATTEMPTS) RetryDecision.GiveUp(tried, false)
                else RetryDecision.Wait(now + backoff(tried), tried, pauseQueue = true)
        }
    }

    /** 2 s, 4 s, 8 s … capped at 60 s. */
    fun backoff(tried: Int): Long =
        (BACKOFF_START_MS shl (tried - 1).coerceIn(0, 10)).coerceAtMost(BACKOFF_MAX_MS)
}
