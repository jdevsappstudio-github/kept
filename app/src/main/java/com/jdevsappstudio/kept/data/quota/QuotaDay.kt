package com.jdevsappstudio.kept.data.quota

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** The local day as yyyyMMdd. Limits reset at local midnight (H17). */
interface QuotaDay {
    fun today(): Int
    /** Today's day, then again whenever it changes. */
    fun ticks(): Flow<Int>
}

/**
 * Wall-clock day. The ticker wakes at midnight but at least every [MAX_SLEEP_MS]: coroutine delays use the monotonic
 * clock, which stops in deep sleep, and the user may change the time zone. Changing the phone's clock is not guarded
 * (there are no Kept servers to check against).
 */
class SystemQuotaDay : QuotaDay {
    override fun today(): Int = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE).toInt()

    override fun ticks(): Flow<Int> = flow {
        while (true) {
            emit(today())
            val now = LocalDateTime.now()
            val toMidnight = Duration.between(now, now.toLocalDate().plusDays(1).atStartOfDay()).toMillis()
            delay((toMidnight + 1_000).coerceAtMost(MAX_SLEEP_MS))
        }
    }.distinctUntilChanged()

    private companion object {
        const val MAX_SLEEP_MS = 15 * 60_000L
    }
}
