package com.jdevsappstudio.kept.data.ai

import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.remoteconfig.remoteConfig
import com.google.firebase.remoteconfig.remoteConfigSettings
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Read model from Remote Config (owner, 25 Nov; the Firebase docs recommend it for swapping models without a release).
 * Keys: read_model, read_effort, read_fallback_model, read_fallback_effort. The in-app defaults are the [CloudPreset]
 * code defaults, so nothing needs publishing for the app to work. Fetched once, lazily, on the first read (≤ 3 s);
 * a failed fetch just keeps the defaults. Changing values = a console publish, which needs the owner's yes.
 */
class RemoteReadModelConfig(private val firebaseConfigured: Boolean) : ReadModelConfig {

    private val mutex = Mutex()
    private var ready = false

    override suspend fun main() = choice(KEY_MODEL, KEY_EFFORT, CloudPreset.DEFAULT.choice)

    override suspend fun fallback() = choice(KEY_FALLBACK_MODEL, KEY_FALLBACK_EFFORT, CloudPreset.BUSY_FALLBACK.choice)

    private suspend fun choice(modelKey: String, effortKey: String, default: CloudChoice): CloudChoice {
        if (!firebaseConfigured) return default
        ensureFetched()
        val rc = Firebase.remoteConfig
        val model = rc.getString(modelKey).trim().ifEmpty { default.model }
        val effort = Effort.entries.firstOrNull { it.name.equals(rc.getString(effortKey).trim(), ignoreCase = true) }
            ?: default.effort
        return CloudChoice(model, effort)
    }

    private suspend fun ensureFetched() = mutex.withLock {
        if (ready) return@withLock
        ready = true // one attempt per process; defaults stand if it fails
        val rc = Firebase.remoteConfig
        rc.setConfigSettingsAsync(remoteConfigSettings { minimumFetchIntervalInSeconds = FETCH_INTERVAL_S }).awaitDone()
        rc.setDefaultsAsync(
            mapOf(
                KEY_MODEL to CloudPreset.DEFAULT.choice.model,
                KEY_EFFORT to CloudPreset.DEFAULT.choice.effort.name,
                KEY_FALLBACK_MODEL to CloudPreset.BUSY_FALLBACK.choice.model,
                KEY_FALLBACK_EFFORT to CloudPreset.BUSY_FALLBACK.choice.effort.name,
            ),
        ).awaitDone()
        val ok = withTimeoutOrNull(FETCH_TIMEOUT_MS) { rc.fetchAndActivate().awaitDone() }
        Log.i(TAG, "remote config fetch ${if (ok == true) "ok" else "skipped (defaults)"}")
    }

    /** Task → suspend without pulling in kotlinx-coroutines-play-services. True if it succeeded. */
    private suspend fun <T> com.google.android.gms.tasks.Task<T>.awaitDone(): Boolean =
        suspendCancellableCoroutine { cont -> addOnCompleteListener { cont.resume(it.isSuccessful) } }

    companion object {
        const val KEY_MODEL = "read_model"
        const val KEY_EFFORT = "read_effort"
        const val KEY_FALLBACK_MODEL = "read_fallback_model"
        const val KEY_FALLBACK_EFFORT = "read_fallback_effort"
        private const val FETCH_INTERVAL_S = 3_600L
        private const val FETCH_TIMEOUT_MS = 3_000L
        private const val TAG = "KeptReader"
    }
}
