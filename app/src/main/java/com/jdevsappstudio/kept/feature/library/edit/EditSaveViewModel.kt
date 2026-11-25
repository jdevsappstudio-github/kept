package com.jdevsappstudio.kept.feature.library.edit

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jdevsappstudio.kept.R
import com.jdevsappstudio.kept.core.model.SaveType
import com.jdevsappstudio.kept.core.presentation.Snackbars
import com.jdevsappstudio.kept.core.presentation.UiText
import com.jdevsappstudio.kept.data.library.LibraryLocalDataSource
import com.jdevsappstudio.kept.data.saves.CardMapping
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * k-organize, "Edit save": one save (title, type, tags) or several from a list selection (type and tags
 * for all, no title). Collections join in H51 step 3.
 */
data class EditSaveState(
    val loading: Boolean = true,
    val many: Boolean = false,
    val count: Int = 1,
    val title: String = "",
    /** Null on a mixed selection until a type is picked. */
    val type: SaveType? = null,
    val tags: List<String> = emptyList(),
    val tagQuery: String = "",
    /** Existing tags that start with the query (or the most used when it is empty), minus the ones already on. */
    val suggestions: List<String> = emptyList(),
    /** Most used first (the sheet shows 5 + '+6 more'), then the rest. */
    val typeOrder: List<SaveType> = SaveType.entries,
)

sealed interface EditSaveAction {
    data class OnTitleChange(val title: String) : EditSaveAction
    data class OnTypeClick(val type: SaveType) : EditSaveAction
    data class OnRemoveTag(val tag: String) : EditSaveAction
    data class OnTagQueryChange(val query: String) : EditSaveAction
    /** The typed query (keyboard Done) or a tapped suggestion. */
    data class OnAddTag(val tag: String) : EditSaveAction
    /** Done: saves, snackbar 'Saved'. */
    data object OnDone : EditSaveAction
    /** Back or a tap outside: closes, changes kept. */
    data object OnBack : EditSaveAction
}

sealed interface EditSaveEvent {
    data object Close : EditSaveEvent
}

/** The picks that change as the user edits; saved so a process death keeps them. */
private data class Draft(val title: String, val type: SaveType?, val tags: List<String>, val query: String)

private data class Original(val title: String, val type: SaveType?, val tags: List<String>)

class EditSaveViewModel(
    private val savedStateHandle: SavedStateHandle,
    private val library: LibraryLocalDataSource,
    private val snackbars: Snackbars,
) : ViewModel() {

    private val ids: List<Long> = checkNotNull(savedStateHandle.get<LongArray>(ARG_IDS)).toList()
    private val many = ids.size > 1
    private val original = MutableStateFlow<Original?>(null)
    private val draft = MutableStateFlow<Draft?>(null)
    private var committed = false

    val state: StateFlow<EditSaveState> = combine(
        draft, library.observeAllTags(), library.observeTypeChips(),
    ) { d, allTags, chips ->
        if (d == null) return@combine EditSaveState(many = many, count = ids.size)
        val used = chips.map { it.type }
        EditSaveState(
            loading = false,
            many = many,
            count = ids.size,
            title = d.title,
            type = d.type,
            tags = d.tags,
            tagQuery = d.query,
            suggestions = suggest(allTags, d),
            typeOrder = used + SaveType.entries.filterNot { it in used },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EditSaveState(many = many, count = ids.size))

    private val _events = Channel<EditSaveEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val saves = ids.map { library.observeSave(it).first() }.filterNotNull()
        if (saves.isEmpty()) {
            _events.send(EditSaveEvent.Close)
            return
        }
        val orig = Original(
            title = if (many) "" else saves.first().title,
            type = saves.map { it.type }.distinct().singleOrNull(),
            // Several saves: show the tags they all share; adding or removing one applies to all.
            tags = saves.map { it.tags.toSet() }.reduce { a, b -> a intersect b }.let { common ->
                saves.first().tags.filter { it in common }
            },
        )
        original.value = orig
        draft.value = Draft(
            title = savedStateHandle[KEY_TITLE] ?: orig.title,
            type = savedStateHandle.get<String>(KEY_TYPE)?.let { SaveType.fromWire(it) } ?: orig.type,
            tags = savedStateHandle.get<Array<String>>(KEY_TAGS)?.toList() ?: orig.tags,
            query = "",
        )
    }

    fun onAction(action: EditSaveAction) {
        when (action) {
            is EditSaveAction.OnTitleChange -> edit { it.copy(title = action.title) }
            is EditSaveAction.OnTypeClick -> edit { it.copy(type = action.type) }
            is EditSaveAction.OnRemoveTag -> edit { it.copy(tags = it.tags - action.tag) }
            is EditSaveAction.OnTagQueryChange -> edit { it.copy(query = action.query) }
            is EditSaveAction.OnAddTag -> {
                val tag = CardMapping.cleanTag(action.tag) ?: return
                edit { if (tag in it.tags) it.copy(query = "") else it.copy(tags = it.tags + tag, query = "") }
            }
            EditSaveAction.OnDone -> finish(announce = true)
            EditSaveAction.OnBack -> finish(announce = false)
        }
    }

    private fun edit(change: (Draft) -> Draft) {
        draft.update { d -> d?.let(change) }
        draft.value?.let {
            savedStateHandle[KEY_TITLE] = it.title
            savedStateHandle[KEY_TYPE] = it.type?.wire
            savedStateHandle[KEY_TAGS] = it.tags.toTypedArray()
        }
    }

    private fun finish(announce: Boolean) {
        if (committed) return
        committed = true
        val d = draft.value
        val orig = original.value
        viewModelScope.launch {
            if (d != null && orig != null) {
                val changed = commit(d, orig)
                if (announce && changed) snackbars.show(UiText.Res(R.string.snack_saved))
            }
            _events.send(EditSaveEvent.Close)
        }
    }

    /** False when nothing changed, so an untouched sheet never marks a save as hand-read. */
    private suspend fun commit(d: Draft, orig: Original): Boolean {
        if (many) {
            val added = d.tags - orig.tags.toSet()
            val removed = orig.tags - d.tags.toSet()
            val type = d.type.takeIf { it != orig.type }
            if (added.isEmpty() && removed.isEmpty() && type == null) return false
            library.editMany(ids, type, added, removed)
        } else {
            val title = d.title.trim().ifEmpty { orig.title }
            val type = d.type ?: orig.type ?: SaveType.OTHER
            if (title == orig.title && type == orig.type && d.tags == orig.tags) return false
            library.edit(ids.single(), title, type, d.tags)
        }
        return true
    }

    private fun suggest(allTags: List<String>, d: Draft): List<String> {
        val q = d.query.trim().trimStart('#').lowercase()
        return allTags.asSequence()
            .filter { it !in d.tags && it.startsWith(q) }
            .take(SUGGESTIONS)
            .toList()
    }

    companion object {
        const val ARG_IDS = "ids"
        private const val KEY_TITLE = "edit_title"
        private const val KEY_TYPE = "edit_type"
        private const val KEY_TAGS = "edit_tags"
        private const val SUGGESTIONS = 8
    }
}
