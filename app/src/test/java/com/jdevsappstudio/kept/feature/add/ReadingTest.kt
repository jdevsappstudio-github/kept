package com.jdevsappstudio.kept.feature.add

import androidx.lifecycle.SavedStateHandle
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import com.jdevsappstudio.kept.core.model.QueueCounts
import com.jdevsappstudio.kept.core.model.SaveStatus
import com.jdevsappstudio.kept.core.model.SaveType
import com.jdevsappstudio.kept.core.presentation.UiText
import com.jdevsappstudio.kept.data.queue.QueuePause
import com.jdevsappstudio.kept.data.queue.ReadRoute
import com.jdevsappstudio.kept.feature.home.QueueView
import com.jdevsappstudio.kept.feature.home.ReadsWith
import com.jdevsappstudio.kept.feature.library.FakeLibrary
import com.jdevsappstudio.kept.feature.library.FakeSettings
import com.jdevsappstudio.kept.feature.library.MainDispatcherExtension
import com.jdevsappstudio.kept.feature.library.keepActive
import com.jdevsappstudio.kept.feature.library.next
import com.jdevsappstudio.kept.feature.library.save
import com.jdevsappstudio.kept.feature.library.testSnackbars
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

private fun s(id: Long, status: SaveStatus, type: SaveType = SaveType.RECIPE, tags: List<String> = emptyList()) =
    save(id, type = type, tags = tags).copy(status = status)

class ReadingRulesTest {

    @Test
    fun `while picks are copied in the line says Adding`() {
        val line = ReadingRules.line(emptyList(), AddProgress(34, 100), QueuePause.NONE, ReadsWith.DEVICE)
        assertThat(line).isEqualTo(ReadingLine.Adding(34, 100))
    }

    @Test
    fun `reading counts this batch only`() {
        val batch = listOf(s(3, SaveStatus.QUEUED), s(2, SaveStatus.READING), s(1, SaveStatus.READ))
        assertThat(ReadingRules.line(batch, null, QueuePause.NONE, ReadsWith.DEVICE))
            .isEqualTo(ReadingLine.Reading(current = 2, total = 3, with = ReadsWith.DEVICE))
    }

    @Test
    fun `a paused queue waits, with the pause reason`() {
        val batch = listOf(s(2, SaveStatus.QUEUED), s(1, SaveStatus.READ))
        assertThat(ReadingRules.line(batch, null, QueuePause.OFFLINE, ReadsWith.CLOUD))
            .isEqualTo(ReadingLine.Waiting(1, QueuePause.OFFLINE))
    }

    @Test
    fun `done counts read and needs-a-look, an empty batch is nothing new`() {
        val batch = listOf(s(2, SaveStatus.NEEDS_LOOK), s(1, SaveStatus.READ))
        assertThat(ReadingRules.line(batch, null, QueuePause.NONE, ReadsWith.DEVICE)).isEqualTo(ReadingLine.Done(1, 1))
        assertThat(ReadingRules.line(emptyList(), null, QueuePause.NONE, ReadsWith.DEVICE)).isEqualTo(ReadingLine.NothingNew)
    }

    @Test
    fun `just read = waiting before, read now`() {
        val before = mapOf(1L to SaveStatus.READING, 2L to SaveStatus.READ, 3L to SaveStatus.QUEUED)
        val now = listOf(s(1, SaveStatus.READ), s(2, SaveStatus.READ), s(3, SaveStatus.NEEDS_LOOK), s(4, SaveStatus.READ))
        assertThat(ReadingRules.justRead(before, now).map { it.id }).containsExactly(1L)
    }

    @Test
    fun `edit prompt and snackbar words`() {
        assertThat(ReadingRules.editPrompt(SaveType.RECIPE)).isEqualTo("Not a recipe?")
        assertThat(ReadingRules.editPrompt(SaveType.EVENT)).isEqualTo("Not an event?")
        assertThat(ReadingRules.editPrompt(SaveType.MOVIE_SHOW)).isEqualTo("Not a movie or show?")
        assertThat(ReadingRules.editPrompt(SaveType.OTHER)).isEqualTo("Wrong type?")
        assertThat(ReadingRules.editPrompt(SaveType.READ)).isEqualTo("Not something to read?")
        assertThat(ReadingRules.taggedText(s(1, SaveStatus.READ, tags = listOf("air-fryer", "quick", "paneer"))))
            .isEqualTo("Tagged Recipe · air-fryer · quick")
    }
}

@ExtendWith(MainDispatcherExtension::class)
class ReadingViewModelTest {
    private val library = FakeLibrary()
    private val snackbars = testSnackbars()
    private val adding = MutableStateFlow<AddProgress?>(null)

    private fun vm(since: Long = 10) = ReadingViewModel(
        SavedStateHandle(mapOf(ReadingViewModel.ARG_SINCE to since)), library,
        flowOf(QueueView(QueueCounts.EMPTY, ReadRoute.DEVICE, QueuePause.NONE)), FakeSettings(), adding, snackbars,
    )

    @Test
    fun `lists only saves kept since the batch started, newest first`() = runTest {
        library.put(s(5, SaveStatus.READ), s(11, SaveStatus.READ), s(12, SaveStatus.READING))
        val vm = vm()
        keepActive(vm.state)
        assertThat(vm.state.value.saves.map { it.id }).containsExactly(12L, 11L)
        assertThat(vm.state.value.line).isEqualTo(ReadingLine.Reading(2, 2, ReadsWith.DEVICE))
    }

    @Test
    fun `a small batch gets a Tagged snackbar when a card finishes`() = runTest {
        library.put(s(11, SaveStatus.READING, tags = listOf("air-fryer")))
        val vm = vm()
        keepActive(vm.state)
        library.put(s(11, SaveStatus.READ, tags = listOf("air-fryer")))
        val msg = snackbars.next()
        assertThat(msg.text).isInstanceOf(UiText.Dynamic::class)
        assertThat((msg.text as UiText.Dynamic).value).isEqualTo("Tagged Recipe · air-fryer")
    }

    @Test
    fun `a big batch stays quiet`() = runTest {
        library.put(*(11L..14L).map { s(it, SaveStatus.QUEUED) }.toTypedArray())
        val vm = vm()
        keepActive(vm.state)
        library.put(s(11, SaveStatus.READ))
        assertThat(withTimeoutOrNull(50) { snackbars.next() }).isEqualTo(null)
    }
}
