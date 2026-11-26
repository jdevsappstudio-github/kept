package com.jdevsappstudio.kept.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.jdevsappstudio.kept.core.model.ListLayout
import com.jdevsappstudio.kept.core.model.ThemeMode
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

    /** k-appearance (H68): System (default), Light or Dark. */
    val themeMode: Flow<ThemeMode>
    suspend fun setThemeMode(mode: ThemeMode)

    /** k-settings 'Reading updates' (H43): the one Kept notification. On by default; Android's own switch still wins. */
    val readingUpdates: Flow<Boolean>
    suspend fun setReadingUpdates(on: Boolean)

    /** Delete everything (H69): forgets recent searches and dismissed suggestions; real settings stay. */
    suspend fun clearContent()

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

    override val themeMode: Flow<ThemeMode> = context.settings.data
        .map { p -> ThemeMode.entries.firstOrNull { it.name == p[THEME_MODE] } ?: ThemeMode.SYSTEM }
        .distinctUntilChanged()

    override suspend fun setThemeMode(mode: ThemeMode) {
        context.settings.edit { it[THEME_MODE] = mode.name }
    }

    override val readingUpdates: Flow<Boolean> = context.settings.data.map { it[READING_UPDATES] ?: true }.distinctUntilChanged()

    override suspend fun setReadingUpdates(on: Boolean) {
        context.settings.edit { it[READING_UPDATES] = on }
    }

    override suspend fun clearContent() {
        context.settings.edit {
            it.remove(RECENT)
            it.remove(DISMISSED)
        }
    }

    private companion object {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val READING_UPDATES = booleanPreferencesKey("reading_updates")
        val DISMISSED = stringSetPreferencesKey("dismissed_suggestions")
        val RECENT = stringPreferencesKey("recent_searches")
        /** Searches are trimmed to single spaces, so a newline never occurs inside one. */
        const val RECENT_SEP = "\n"
        val NOTIF_ASKED = booleanPreferencesKey("notifications_asked")
        val CLOUD_HELP = booleanPreferencesKey("cloud_help")
        val LIST_LAYOUT = stringPreferencesKey("list_layout")
    }
}
