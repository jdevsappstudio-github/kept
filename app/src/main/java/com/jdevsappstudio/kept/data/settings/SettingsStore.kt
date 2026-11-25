package com.jdevsappstudio.kept.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
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

    /** k-search empty state: the last [RECENT_SEARCHES] searches, newest first (H54: kept on tap or Enter). */
    val recentSearches: Flow<List<String>>
    suspend fun addRecentSearch(text: String)

    /** H26 / H55: tags whose collection suggestion got Not now; never suggested again. */
    val dismissedSuggestions: Flow<Set<String>>
    suspend fun dismissSuggestion(tag: String)

    companion object {
        const val RECENT_SEARCHES = 5

        /** Newest first, no repeats (case-insensitive), at most [RECENT_SEARCHES]. */
        fun pushRecent(list: List<String>, text: String): List<String> {
            val clean = text.trim().replace(Regex("\\s+"), " ")
            if (clean.isEmpty()) return list
            return (listOf(clean) + list.filterNot { it.equals(clean, ignoreCase = true) }).take(RECENT_SEARCHES)
        }
    }
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

    override val recentSearches: Flow<List<String>> = context.settings.data
        .map { p -> p[RECENT]?.split(RECENT_SEP)?.filter { it.isNotEmpty() }.orEmpty() }
        .distinctUntilChanged()

    override suspend fun addRecentSearch(text: String) {
        context.settings.edit { p ->
            val now = p[RECENT]?.split(RECENT_SEP)?.filter { it.isNotEmpty() }.orEmpty()
            p[RECENT] = SettingsStore.pushRecent(now, text).joinToString(RECENT_SEP)
        }
    }

    override val dismissedSuggestions: Flow<Set<String>> = context.settings.data
        .map { it[DISMISSED].orEmpty() }
        .distinctUntilChanged()

    override suspend fun dismissSuggestion(tag: String) {
        context.settings.edit { it[DISMISSED] = it[DISMISSED].orEmpty() + tag }
    }

    private companion object {
        val DISMISSED = stringSetPreferencesKey("dismissed_suggestions")
        val RECENT = stringPreferencesKey("recent_searches")
        /** Searches are trimmed to single spaces, so a newline never occurs inside one. */
        const val RECENT_SEP = "\n"
        val NOTIF_ASKED = booleanPreferencesKey("notifications_asked")
        val CLOUD_HELP = booleanPreferencesKey("cloud_help")
        val READS_DAY = intPreferencesKey("cloud_reads_day")
        val READS_COUNT = intPreferencesKey("cloud_reads_count")
        val LIST_LAYOUT = stringPreferencesKey("list_layout")
    }
}
