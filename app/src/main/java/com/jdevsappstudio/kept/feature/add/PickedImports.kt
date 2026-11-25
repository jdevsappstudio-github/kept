package com.jdevsappstudio.kept.feature.add

import com.jdevsappstudio.kept.data.capture.ImagesKept
import com.jdevsappstudio.kept.data.capture.Keeper

import com.jdevsappstudio.kept.R
import com.jdevsappstudio.kept.core.model.SaveSource
import com.jdevsappstudio.kept.core.presentation.Snackbars
import com.jdevsappstudio.kept.core.presentation.UiText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** "Adding 34 of 100" while picked screenshots are copied in; [done] and [total] add up over back-to-back picks. */
data class AddProgress(val done: Int, val total: Int)

/**
 * Copies photo-picker results in on the app scope (H56): the user lands on Just kept at once, and leaving that screen
 * never cuts a copy short. Picker grants last while the app process lives, so the activity may close mid-copy.
 * Picks run one after another; what wasn't added (duplicates, unreadable, over 100) is told in snackbars at the end.
 */
class PickedImports(
    private val keeper: Keeper,
    private val snackbars: Snackbars,
    private val scope: CoroutineScope,
) {
    private val mutex = Mutex()
    private val _progress = MutableStateFlow<AddProgress?>(null)

    /** null when nothing is being added. */
    val progress: StateFlow<AddProgress?> = _progress.asStateFlow()

    fun start(refs: List<String>) {
        val batch = refs.distinct()
        if (batch.isEmpty()) return
        _progress.update { AddProgress(it?.done ?: 0, (it?.total ?: 0) + batch.size) }
        scope.launch {
            mutex.withLock {
                val base = _progress.value?.done ?: 0
                try {
                    val result = keeper.keepImages(batch, SaveSource.PICKER) { done, _ ->
                        _progress.update { it?.copy(done = base + done) }
                    }
                    report(result)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    snackbars.show(UiText.Res(R.string.add_failed))
                } finally {
                    // Count the whole batch as done (over-cap and failed ones too), so the line always clears.
                    _progress.update { p -> p?.copy(done = base + batch.size)?.takeIf { it.done < it.total } }
                }
            }
        }
    }

    private fun report(r: ImagesKept) {
        if (r.duplicates > 0) {
            snackbars.show(
                if (r.kept == 0) UiText.Plural(R.plurals.add_all_dupes, r.duplicates, r.duplicates)
                else UiText.Plural(R.plurals.add_dupes, r.duplicates, r.duplicates),
            )
        }
        if (r.unreadable > 0) snackbars.show(UiText.Plural(R.plurals.add_unreadable, r.unreadable, r.unreadable))
        if (r.overCap > 0) snackbars.show(UiText.Res(R.string.add_over_cap, Keeper.MAX_PER_BATCH))
    }
}
