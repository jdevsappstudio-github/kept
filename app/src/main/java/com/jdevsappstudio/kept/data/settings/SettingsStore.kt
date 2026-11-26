package com.jdevsappstudio.kept.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.jdevsappstudio.kept.core.model.ListLayout
import com.jdevsappstudio.kept.core.model.SaveType
import com.jdevsappstudio.kept.core.model.ShareApp
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

    /** H70: onboarding finished (or skipped, or an existing library was found). */
    val onboardingDone: Flow<Boolean>
    suspend fun setOnboardingDone(done: Boolean)
    /** False until the done flag is first written (either way): the gate's silent check runs only on that first open. */
    suspend fun onboardingFlagWritten(): Boolean

    /** B1 picks, at most 3, in pick order: they come first in Home's type chips. */
    val preferredTypes: Flow<List<SaveType>>
    suspend fun setPreferredTypes(types: List<SaveType>)

    /** B2 picks, in list order: the share tip and k-ob-first's app button follow the first one. */
    val shareApps: Flow<List<ShareApp>>
    suspend fun setShareApps(apps: List<ShareApp>)

    /** False until the first save shows on Home: an empty Home is k-ob-ready before it, k-empty after (H70). */
    val firstSaveSeen: Flow<Boolean>
    suspend fun markFirstSaveSeen()

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

    override val onboardingDone: Flow<Boolean> = context.settings.data.map { it[ONBOARDING_DONE] ?: false }.distinctUntilChanged()

    override suspend fun setOnboardingDone(done: Boolean) {
        context.settings.edit { it[ONBOARDING_DONE] = done }
    }

    override suspend fun onboardingFlagWritten(): Boolean = context.settings.data.first().contains(ONBOARDING_DONE)

    override val preferredTypes: Flow<List<SaveType>> = context.settings.data
        .map { p -> p[PREFERRED_TYPES].orEmpty().split(LIST_SEP).mapNotNull { w -> SaveType.entries.firstOrNull { it.wire == w } } }
        .distinctUntilChanged()

    override suspend fun setPreferredTypes(types: List<SaveType>) {
        context.settings.edit { it[PREFERRED_TYPES] = types.joinToString(LIST_SEP) { t -> t.wire } }
    }

    override val shareApps: Flow<List<ShareApp>> = context.settings.data
        .map { p -> p[SHARE_APPS].orEmpty().split(LIST_SEP).mapNotNull { n -> ShareApp.entries.firstOrNull { it.name == n } } }
        .distinctUntilChanged()

    override suspend fun setShareApps(apps: List<ShareApp>) {
        context.settings.edit { it[SHARE_APPS] = apps.joinToString(LIST_SEP) { a -> a.name } }
    }

    override val firstSaveSeen: Flow<Boolean> = context.settings.data.map { it[FIRST_SAVE_SEEN] ?: false }.distinctUntilChanged()

    override suspend fun markFirstSaveSeen() {
        context.settings.edit { it[FIRST_SAVE_SEEN] = true }
    }

    private companion object {
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
        val PREFERRED_TYPES = stringPreferencesKey("preferred_types")
        val SHARE_APPS = stringPreferencesKey("share_apps")
        val FIRST_SAVE_SEEN = booleanPreferencesKey("first_save_seen")
        const val LIST_SEP = ","
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
