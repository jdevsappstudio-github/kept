package com.jdevsappstudio.kept.data.quota

import com.jdevsappstudio.kept.data.config.RemoteValues

/** Daily allowances and ad sizes. Code defaults = BUILD-SPEC §11 (H17, H45, H46); Remote Config can change them. */
data class QuotaLimits(
    val answersPerDay: Int = ANSWERS_PER_DAY,
    val readsPerDay: Int = READS_PER_DAY,
    val answersPerAd: Int = ANSWERS_PER_AD,
    val readsPerAd: Int = READS_PER_AD,
) {
    fun perDay(m: CloudMeter) = if (m == CloudMeter.ANSWERS) answersPerDay else readsPerDay
    fun perAd(m: CloudMeter) = if (m == CloudMeter.ANSWERS) answersPerAd else readsPerAd

    companion object {
        /** H66: 20 a day while judging, as there is no ad in the MVP (until 16 Dec); afterwards publish quota_answers_per_day = 5, no release. */
        const val ANSWERS_PER_DAY = 20
        /** H46: 100 a day while judging (until 16 Dec); afterwards publish quota_reads_per_day = 30 (H32), no release. */
        const val READS_PER_DAY = 100
        const val ANSWERS_PER_AD = 3
        const val READS_PER_AD = 10
    }
}

fun interface QuotaLimitsSource {
    suspend fun get(): QuotaLimits
}

/**
 * Keys: quota_answers_per_day, quota_reads_per_day, quota_answers_per_ad, quota_reads_per_ad. Not published → code default.
 * A daily limit may be 0 (a kill switch); an ad always gives at least 1; anything above [MAX] is treated as a typo.
 */
class RemoteQuotaLimits(private val remote: RemoteValues) : QuotaLimitsSource {
    override suspend fun get() = QuotaLimits(
        answersPerDay = read(KEY_ANSWERS_PER_DAY, QuotaLimits.ANSWERS_PER_DAY, min = 0),
        readsPerDay = read(KEY_READS_PER_DAY, QuotaLimits.READS_PER_DAY, min = 0),
        answersPerAd = read(KEY_ANSWERS_PER_AD, QuotaLimits.ANSWERS_PER_AD, min = 1),
        readsPerAd = read(KEY_READS_PER_AD, QuotaLimits.READS_PER_AD, min = 1),
    )

    private suspend fun read(key: String, default: Int, min: Int): Int =
        remote.long(key)?.takeIf { it in min..MAX }?.toInt() ?: default

    companion object {
        const val KEY_ANSWERS_PER_DAY = "quota_answers_per_day"
        const val KEY_READS_PER_DAY = "quota_reads_per_day"
        const val KEY_ANSWERS_PER_AD = "quota_answers_per_ad"
        const val KEY_READS_PER_AD = "quota_reads_per_ad"
        const val MAX = 10_000L
    }
}
