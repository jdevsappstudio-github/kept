package com.jdevsappstudio.kept.feature.onboarding

import com.jdevsappstudio.kept.data.settings.SettingsStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

/**
 * H70: onboarding shows on a fresh install only. A library that already has saves (an update, a restore) is marked
 * done silently on the very first open (flag never written); after that the flag alone decides, so a debug
 * Replay onboarding (flag false) survives a restart.
 */
class OnboardingGate(private val settings: SettingsStore, private val hasSaves: suspend () -> Boolean) {

    /** true = show onboarding. */
    val showOnboarding: Flow<Boolean> = flow {
        if (!settings.onboardingFlagWritten() && hasSaves()) settings.setOnboardingDone(true)
        emitAll(settings.onboardingDone.map { !it })
    }
}
