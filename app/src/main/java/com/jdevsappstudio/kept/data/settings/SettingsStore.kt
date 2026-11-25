package com.jdevsappstudio.kept.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.jdevsappstudio.kept.core.model.ListLayout
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.settings by preferencesDataStore("settings")

/** User settings. Every call is main-safe. An interface so ViewModel tests can use an in-memory fake. */
interface SettingsStore {
    /** "Cloud help" (BUILD-SPEC §3): off until the user says yes. Without it, no-Nano phones don't send anything. */
    val cloudHelp: Flow<Boolean>
    suspend fun cloudHelpNow(): Boolean
    suspend fun setCloudHelp(on: Boolean)

    /** Cloud reads done on [day] (yyyyMMdd); resets when the day changes. */
    suspend fun cloudReadsOn(day: Int): Int
    suspend fun countCloudRead(day: Int)

    /** H50: rows (default) or grid, one choice for every save list. */
    val listLayout: Flow<ListLayout>
    suspend fun setListLayout(layout: ListLayout)

    /** True only the first time: POST_NOTIFICATIONS is asked once, after the first share (BUILD-SPEC §4). */
    suspend fun claimNotificationAsk(): Boolean
}

/** DataStore does its file IO on its own IO scope. */
class DataStoreSettingsStore(private val context: Context) : SettingsStore {

    override val cloudHelp: Flow<Boolean> = context.settings.data.map { it[CLOUD_HELP] ?: false }.distinctUntilChanged()

    override suspend fun cloudHelpNow(): Boolean = cloudHelp.first()

    override suspend fun setCloudHelp(on: Boolean) {
        context.settings.edit { it[CLOUD_HELP] = on }
    }

    override suspend fun cloudReadsOn(day: Int): Int {
        val prefs = context.settings.data.first()
        return if (prefs[READS_DAY] == day) prefs[READS_COUNT] ?: 0 else 0
    }

    override suspend fun countCloudRead(day: Int) {
        context.settings.edit {
            val same = it[READS_DAY] == day
            it[READS_DAY] = day
            it[READS_COUNT] = (if (same) it[READS_COUNT] ?: 0 else 0) + 1
        }
    }

    override val listLayout: Flow<ListLayout> = context.settings.data
        .map { p -> ListLayout.entries.firstOrNull { it.name == p[LIST_LAYOUT] } ?: ListLayout.ROWS }
        .distinctUntilChanged()

    override suspend fun setListLayout(layout: ListLayout) {
        context.settings.edit { it[LIST_LAYOUT] = layout.name }
    }

    override suspend fun claimNotificationAsk(): Boolean {
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
        val LIST_LAYOUT = stringPreferencesKey("list_layout")
    }
}
