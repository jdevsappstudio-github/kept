package com.jdevsappstudio.kept.data.ads

import android.app.Activity

/** How a rewarded ad ended (k-ad-rewarded exits in flows_mvp). */
enum class AdOutcome { REWARDED, CLOSED_EARLY, NO_AD }

/**
 * Rewarded ads, started only from the user's tap (H45). Unity LevelPlay plugs in here later (H66; the AdMob account can't be used); the ViewModels only see
 * [AdOutcome], so nothing else changes when they do.
 */
interface RewardedAds {
    /** False = no ad provider in this build: k-cloud-out shows only 'Wait till midnight' (H66). */
    val available: Boolean
    suspend fun show(activity: Activity): AdOutcome
}

/** The MVP (H66): no ad at all, in every build. Unity LevelPlay (Unity bidding, as in Clock Out) can replace it later. */
class NoRewardedAds : RewardedAds {
    override val available = false
    override suspend fun show(activity: Activity) = AdOutcome.NO_AD
}

/** Debug builds: pays out at once, so the bonus path can be checked on the phone without AdMob. */
class InstantRewardedAds : RewardedAds {
    override val available = true
    override suspend fun show(activity: Activity) = AdOutcome.REWARDED
}
