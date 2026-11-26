package com.jdevsappstudio.kept.feature.quota

import com.jdevsappstudio.kept.data.quota.CloudMeter

/**
 * k-cloud-out copy. Two words, never mixed (review-product #12): answers = questions to the cloud,
 * reads = cloud reading of saves on phones without Nano.
 */
object CloudOutCopy {
    fun title(meter: CloudMeter, total: Int): String = "That's today's $total cloud ${noun(meter, total)}"

    fun body(meter: CloudMeter): String = when (meter) {
        CloudMeter.ANSWERS ->
            "More at midnight. Search and tags keep working. Reading your saves is counted on its own, not here."
        CloudMeter.READS ->
            "More at midnight. The rest are read tomorrow by themselves. Questions (Ask) are counted on their own."
    }

    fun watchAd(meter: CloudMeter, perAd: Int): String = "Watch a short ad: +$perAd ${noun(meter, perAd)}"

    const val NO_AD = "No ad right now"
    const val WAIT = "Wait till midnight"

    private fun noun(meter: CloudMeter, n: Int): String = when (meter) {
        CloudMeter.ANSWERS -> if (n == 1) "answer" else "answers"
        CloudMeter.READS -> if (n == 1) "read" else "reads"
    }
}
