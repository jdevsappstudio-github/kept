package com.jdevsappstudio.kept.data.quota

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

/**
 * The two free cloud allowances (BUILD-SPEC §11). Never mixed in copy (review-product #12):
 * **answers** = questions to the cloud (Ask, Fill in), **reads** = cloud reading of saves on phones without Nano.
 * [lowAt]: the 'N of M left today' line shows once this many or fewer are left (k-ask / home strip).
 */
enum class CloudMeter(val lowAt: Int) { ANSWERS(2), READS(5) }

/** Today's numbers for one meter. [perAd] is what one rewarded ad adds (H57: no cap on ads). */
data class MeterView(val meter: CloudMeter, val used: Int, val bonus: Int, val limit: Int, val perAd: Int) {
    val total: Int get() = limit + bonus
    val left: Int get() = (total - used).coerceAtLeast(0)
    val isOut: Boolean get() = left == 0
    /** Show 'N of M left today' (M = [total], so an ad's bonus is counted in). */
    val showCounter: Boolean get() = left <= meter.lowAt
}

/**
 * Counts cloud use per local day and adds ad bonuses. What counts (k-ask, k-link notes): only a call that succeeded;
 * 'Cloud didn't answer' never costs. A new local day starts both meters again at 0 used, 0 bonus.
 * Every call is main-safe (the store is DataStore).
 */
class CloudQuota(
    private val store: QuotaStore,
    private val limits: QuotaLimitsSource,
    private val day: QuotaDay,
) {
    /** Live view; re-emits at local midnight (so a paused queue or a k-cloud-out sheet sees the reset). */
    fun observe(meter: CloudMeter): Flow<MeterView> = flow {
        val l = limits.get()
        emitAll(combine(day.ticks(), store.observe(meter)) { today, record -> view(meter, record.on(today), l) })
    }.distinctUntilChanged()

    suspend fun now(meter: CloudMeter): MeterView = view(meter, store.get(meter).on(day.today()), limits.get())

    suspend fun left(meter: CloudMeter): Int = now(meter).left

    /** After a successful cloud call. */
    suspend fun use(meter: CloudMeter) = store.add(meter, day.today(), used = 1, bonus = 0)

    /** After a rewarded ad paid out; returns what was added (+3 answers / +10 reads). */
    suspend fun grantAd(meter: CloudMeter): Int {
        val perAd = limits.get().perAd(meter)
        store.add(meter, day.today(), used = 0, bonus = perAd)
        return perAd
    }

    private fun view(meter: CloudMeter, r: QuotaRecord, l: QuotaLimits) =
        MeterView(meter, used = r.used, bonus = r.bonus, limit = l.perDay(meter), perAd = l.perAd(meter))
}

/** One meter's stored day. [on] gives today's record: the stored one, or a fresh one if the day has moved on. */
data class QuotaRecord(val day: Int, val used: Int, val bonus: Int) {
    fun on(today: Int) = if (day == today) this else QuotaRecord(today, 0, 0)

    companion object {
        val EMPTY = QuotaRecord(0, 0, 0)
    }
}

interface QuotaStore {
    fun observe(meter: CloudMeter): Flow<QuotaRecord>
    suspend fun get(meter: CloudMeter): QuotaRecord
    /** Atomic: starts the day again first if [today] is not the stored day. */
    suspend fun add(meter: CloudMeter, today: Int, used: Int, bonus: Int)

    companion object {
        /** Shared by every store so the day-rollover rule lives in one place. */
        fun added(old: QuotaRecord, today: Int, used: Int, bonus: Int): QuotaRecord {
            val base = old.on(today)
            return QuotaRecord(today, base.used + used, base.bonus + bonus)
        }
    }
}
