package com.jdevsappstudio.kept.feature.home

import com.jdevsappstudio.kept.core.model.SaveType
import com.jdevsappstudio.kept.data.ai.DeviceModelDownloads
import com.jdevsappstudio.kept.data.ai.DeviceModelState
import com.jdevsappstudio.kept.data.settings.SettingsStore
import com.jdevsappstudio.kept.feature.onboarding.OnboardingRules
import com.jdevsappstudio.kept.feature.onboarding.PhoneLine
import com.jdevsappstudio.kept.feature.onboarding.ShareTip
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapLatest

/** k-ob-ready: an empty Home before the first save, in the person's order (B1 chips, B2 share tip). */
data class HomeReady(val tip: ShareTip, val types: List<SaveType>, val phone: PhoneLine?)

/** Null once the first save has shown on Home: an empty Home after that (Delete everything) is plain k-empty. */
@OptIn(ExperimentalCoroutinesApi::class)
fun homeReady(settings: SettingsStore, deviceModel: suspend () -> DeviceModelState, downloads: DeviceModelDownloads): Flow<HomeReady?> {
    val phone = downloads.state.mapLatest { it to runCatching { deviceModel() }.getOrDefault(DeviceModelState.UNAVAILABLE) }
    return combine(settings.firstSaveSeen, settings.preferredTypes, settings.shareApps, settings.cloudHelp, phone) { seen, types, apps, cloud, (dl, device) ->
        if (seen) null
        else HomeReady(OnboardingRules.shareTip(apps), types.ifEmpty { OnboardingRules.DEFAULT_TYPES }, OnboardingRules.phoneLine(device, dl, cloud))
    }
}
