package com.jdevsappstudio.kept.feature.library

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import assertk.assertions.isTrue
import com.jdevsappstudio.kept.R
import com.jdevsappstudio.kept.core.model.SaveType
import com.jdevsappstudio.kept.core.model.TypeChip
import com.jdevsappstudio.kept.feature.library.edit.EditSaveAction
import com.jdevsappstudio.kept.feature.library.edit.EditSaveEvent
import com.jdevsappstudio.kept.feature.library.edit.EditSaveViewModel
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MainDispatcherExtension::class)
class EditSaveViewModelTest {
    private val library = FakeLibrary()
    private val snackbars = testSnackbars()
    private val collections = FakeCollections()

    private fun vm(vararg ids: Long, extra: Map<String, Any> = emptyMap()) =
        EditSaveViewModel(SavedStateHandle(mapOf(EditSaveViewModel.ARG_IDS to ids) + extra), library, collections, snackbars)

    @Test
    fun `one save - Done saves title, type and tags, says Saved, closes`() = runTest {
        library.put(save(1, title = "Paneer", tags = listOf("air-fryer", "dinner")))
        library.chips.value = listOf(TypeChip(SaveType.PLACE, 9), TypeChip(SaveType.RECIPE, 3))
        val vm = vm(1)
        keepActive(vm.state)
        assertThat(vm.state.value.typeOrder.take(3)).containsExactly(SaveType.PLACE, SaveType.RECIPE, SaveType.PRODUCT)

        vm.onAction(EditSaveAction.OnTitleChange("Paneer tikka"))
        vm.onAction(EditSaveAction.OnTypeClick(SaveType.TIP))
        vm.onAction(EditSaveAction.OnRemoveTag("dinner"))
        vm.onAction(EditSaveAction.OnAddTag(" #Quick "))
        vm.events.test {
            vm.onAction(EditSaveAction.OnDone)
            assertThat(awaitItem()).isEqualTo(EditSaveEvent.Close)
        }
        assertThat(library.log).containsExactly("edit 1 Paneer tikka tip [air-fryer, quick]")
        assertThat(snackbars.next().text.resId()).isEqualTo(R.string.snack_saved)
    }

    @Test
    fun `untouched sheet writes nothing (no hand-read mark), a blank title keeps the old one`() = runTest {
        library.put(save(1, title = "Paneer"))
        val vm = vm(1)
        keepActive(vm.state)
        vm.onAction(EditSaveAction.OnTitleChange("   "))
        vm.events.test {
            vm.onAction(EditSaveAction.OnBack)
            assertThat(awaitItem()).isEqualTo(EditSaveEvent.Close)
        }
        assertThat(library.log).containsExactly()
    }

    @Test
    fun `several saves - shared tags shown, adds and removes apply to all, mixed type stays`() = runTest {
        library.put(
            save(1, type = SaveType.RECIPE, tags = listOf("goa", "beach", "food")),
            save(2, type = SaveType.PLACE, tags = listOf("beach", "goa")),
        )
        val vm = vm(1, 2)
        keepActive(vm.state)
        assertThat(vm.state.value.many).isTrue()
        assertThat(vm.state.value.type).isNull()
        assertThat(vm.state.value.tags).containsExactly("goa", "beach")

        vm.onAction(EditSaveAction.OnRemoveTag("beach"))
        vm.onAction(EditSaveAction.OnAddTag("trip"))
        vm.onAction(EditSaveAction.OnBack)
        assertThat(library.log).containsExactly("editMany [1, 2] null +[trip] -[beach]")
    }

    @Test
    fun `suggestions match the typed start and skip tags already on`() = runTest {
        library.put(save(1, tags = listOf("air-fryer")))
        library.allTags.value = listOf("air-fryer", "airport", "dinner", "aim")
        val vm = vm(1)
        keepActive(vm.state)
        vm.onAction(EditSaveAction.OnTagQueryChange("#Ai"))
        assertThat(vm.state.value.suggestions).containsExactly("airport", "aim")
    }

    @Test
    fun `a draft survives process death`() = runTest {
        library.put(save(1, title = "Paneer"))
        val vm = vm(1, extra = mapOf("edit_title" to "Paneer tikka", "edit_tags" to arrayOf("quick")))
        keepActive(vm.state)
        assertThat(vm.state.value.title).isEqualTo("Paneer tikka")
        assertThat(vm.state.value.tags).containsExactly("quick")
    }

    @Test
    fun `collections tick like tags - one save joins and leaves, without a hand-read edit`() = runTest {
        library.put(save(1, title = "Café"))
        collections.collection(1, "Goa trip", setOf(1))
        collections.collection(2, "Gift ideas")
        val vm = vm(1)
        keepActive(vm.state)
        assertThat(vm.state.value.collections.map { it.name to it.ticked })
            .containsExactly("Gift ideas" to false, "Goa trip" to true)

        vm.onAction(EditSaveAction.OnCollectionToggle(1))
        vm.onAction(EditSaveAction.OnCollectionToggle(2))
        vm.events.test {
            vm.onAction(EditSaveAction.OnDone)
            assertThat(awaitItem()).isEqualTo(EditSaveEvent.Close)
        }
        assertThat(collections.log).containsExactly("add 2 [1]", "remove 1 [1]")
        assertThat(library.log).containsExactly()
        assertThat(snackbars.next().text.resId()).isEqualTo(R.string.snack_saved)
    }

    @Test
    fun `several saves - ticked only when all are in, tick adds all, untick removes all`() = runTest {
        library.put(save(1), save(2))
        collections.collection(1, "Goa trip", setOf(1, 2))
        collections.collection(2, "Gift ideas", setOf(1))
        val vm = vm(1, 2)
        keepActive(vm.state)
        assertThat(vm.state.value.collections.map { it.name to it.ticked })
            .containsExactly("Gift ideas" to false, "Goa trip" to true)

        vm.onAction(EditSaveAction.OnCollectionToggle(2))
        vm.onAction(EditSaveAction.OnCollectionToggle(1))
        vm.events.test {
            vm.onAction(EditSaveAction.OnBack)
            assertThat(awaitItem()).isEqualTo(EditSaveEvent.Close)
        }
        assertThat(collections.log).containsExactly("add 2 [1, 2]", "remove 1 [1, 2]")
    }

    @Test
    fun `a collection made from the sheet comes back ticked and the save joins it on Done`() = runTest {
        library.put(save(1))
        val vm = vm(1)
        keepActive(vm.state)
        val id = collections.create("Weekend brunch")!!
        vm.onAction(EditSaveAction.OnCollectionCreated(id))
        assertThat(vm.state.value.collections.single().ticked).isTrue()
        vm.events.test {
            vm.onAction(EditSaveAction.OnDone)
            awaitItem()
        }
        assertThat(collections.log.last()).isEqualTo("add $id [1]")
    }
}
