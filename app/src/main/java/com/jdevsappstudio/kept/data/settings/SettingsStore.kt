package com.jdevsappstudio.kept.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.settings by preferencesDataStore("settings")

/** User settings. DataStore does its file IO on its own IO scope, so every call here is main-safe. */
class SettingsStore(private val context: Context) {

    /** "Cloud help" (BUILD-SPEC §3): off until the user says yes. Without it, no-Nano phones don't send anything. */
    val cloudHelp: Flow<Boolean> = context.settings.data.map { it[CLOUD_HELP] ?: false }.distinctUntilChanged()

    suspend fun cloudHelpNow(): Boolean = cloudHelp.first()

    suspend fun setCloudHelp(on: Boolean) {
        context.settings.edit { it[CLOUD_HELP] = on }
    }

    /** Cloud reads done on [day] (yyyyMMdd); resets when the day changes. */
    suspend fun cloudReadsOn(day: Int): Int {
        val prefs = context.settings.data.first()
        return if (prefs[READS_DAY] == day) prefs[READS_COUNT] ?: 0 else 0
    }

    suspend fun countCloudRead(day: Int) {
        context.settings.edit {
            val same = it[READS_DAY] == day
            it[READS_DAY] = day
            it[READS_COUNT] = (if (same) it[READS_COUNT] ?: 0 else 0) + 1
        }
    }

    /** True only the first time: POST_NOTIFICATIONS is asked once, after the first share (BUILD-SPEC §4). */
    suspend fun claimNotificationAsk(): Boolean {
        var first = false
        context.settings.edit {
            if (it[NOTIF_ASKED] != true) {
                it[NOTIF_ASKED] = true
                first = true
            }
        }
        return first
    }

    private companion object {
        val NOTIF_ASKED = booleanPreferencesKey("notifications_asked")
        val CLOUD_HELP = booleanPreferencesKey("cloud_help")
        val READS_DAY = intPreferencesKey("cloud_reads_day")
        val READS_COUNT = intPreferencesKey("cloud_reads_count")
    }
}
