package com.jdevsappstudio.kept.data.config

import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.remoteConfig
import com.google.firebase.remoteconfig.remoteConfigSettings
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/** Values published in Remote Config. Null = not published (or not a valid value): the caller uses its code default. */
interface RemoteValues {
    suspend fun string(key: String): String?
    suspend fun long(key: String): Long?
}

/**
 * One Remote Config fetch per process, shared by every reader (read model, quotas). Lazy, ≤ 3 s; a failed fetch keeps the
 * code defaults. Only values that came from the server count, so nothing has to be published for the app to work.
 * Changing a value = a console publish, which needs the owner's yes.
 */
class FirebaseRemoteValues(private val firebaseConfigured: Boolean) : RemoteValues {

    private val mutex = Mutex()
    private var ready = false

    override suspend fun string(key: String): String? =
        remote(key)?.asString()?.trim()?.ifEmpty { null }

    override suspend fun long(key: String): Long? =
        remote(key)?.asString()?.trim()?.toLongOrNull()

    private suspend fun remote(key: String): com.google.firebase.remoteconfig.FirebaseRemoteConfigValue? {
        if (!firebaseConfigured) return null
        ensureFetched()
        val value = Firebase.remoteConfig.getValue(key)
        return value.takeIf { it.source == FirebaseRemoteConfig.VALUE_SOURCE_REMOTE }
    }

    private suspend fun ensureFetched() = mutex.withLock {
        if (ready) return@withLock
        ready = true // one attempt per process; defaults stand if it fails
        val rc = Firebase.remoteConfig
        rc.setConfigSettingsAsync(remoteConfigSettings { minimumFetchIntervalInSeconds = FETCH_INTERVAL_S }).awaitDone()
        val ok = withTimeoutOrNull(FETCH_TIMEOUT_MS) { rc.fetchAndActivate().awaitDone() }
        Log.i(TAG, "remote config fetch ${if (ok == true) "ok" else "skipped (defaults)"}")
    }

    /** Task → suspend without pulling in kotlinx-coroutines-play-services. True if it succeeded. */
    private suspend fun <T> com.google.android.gms.tasks.Task<T>.awaitDone(): Boolean =
        suspendCancellableCoroutine { cont -> addOnCompleteListener { cont.resume(it.isSuccessful) } }

    private companion object {
        const val FETCH_INTERVAL_S = 3_600L
        const val FETCH_TIMEOUT_MS = 3_000L
        const val TAG = "KeptConfig"
    }
}
