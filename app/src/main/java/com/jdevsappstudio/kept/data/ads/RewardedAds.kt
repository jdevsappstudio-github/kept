package com.jdevsappstudio.kept.data.ads

import android.app.Activity

/** How a rewarded ad ended (k-ad-rewarded exits in flows_mvp). */
enum class AdOutcome { REWARDED, CLOSED_EARLY, NO_AD }

/**
 * Rewarded ads, started only from the user's tap (H45). AdMob + UMP plug in here later; the ViewModels only see
 * [AdOutcome], so nothing else changes when they do.
 */
interface RewardedAds {
    suspend fun show(activity: Activity): AdOutcome
}

/** Until AdMob lands: there is never an ad, so k-cloud-out shows 'No ad right now'. */
class NoRewardedAds : RewardedAds {
    override suspend fun show(activity: Activity) = AdOutcome.NO_AD
}

/** Debug builds: pays out at once, so the bonus path can be checked on the phone without AdMob. */
class InstantRewardedAds : RewardedAds {
    override suspend fun show(activity: Activity) = AdOutcome.REWARDED
}
