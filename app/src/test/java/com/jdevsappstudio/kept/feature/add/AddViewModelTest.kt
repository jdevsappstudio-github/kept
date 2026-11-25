package com.jdevsappstudio.kept.feature.add

import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isTrue
import com.jdevsappstudio.kept.core.presentation.SnackbarMessage
import com.jdevsappstudio.kept.core.presentation.UiText
import com.jdevsappstudio.kept.data.capture.ClipPeek
import com.jdevsappstudio.kept.data.capture.ClipboardLinks
import com.jdevsappstudio.kept.data.capture.FakeCopier
import com.jdevsappstudio.kept.data.capture.FakeSaveStore
import com.jdevsappstudio.kept.data.capture.Keeper
import com.jdevsappstudio.kept.feature.library.MainDispatcherExtension
import com.jdevsappstudio.kept.feature.library.testSnackbars
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

class FakeClipboard(var peek: ClipPeek = ClipPeek.NONE, var text: String? = null) : ClipboardLinks {
    var reads = 0
    override fun peek() = peek
    override fun readText(): String? {
        reads++
        return text
    }
}

@ExtendWith(MainDispatcherExtension::class)
class AddViewModelTest {
    private val store = FakeSaveStore()
    private val keeper = Keeper(store, FakeCopier())
    private val snackbars = testSnackbars()
    private val clipboard = FakeClipboard()
    private val importScope = TestScope(UnconfinedTestDispatcher())
    private val imports = PickedImports(keeper, snackbars, importScope)
    private val vm = AddViewModel(keeper, imports, clipboard, snackbars)

    @Test fun `opening only peeks the clipboard, never reads it`() = runTest {
        clipboard.peek = ClipPeek.LINK
        clipboard.text = "https://a.com"
        vm.onAction(AddAction.OnShown)
        assertThat(vm.state.value.clip).isEqualTo(ClipPeek.LINK)
        assertThat(clipboard.reads).isEqualTo(0)
    }

    @Test fun `pick asks for up to 100, a pick goes to Just kept, cancel closes`() = runTest {
        vm.events.test {
            vm.onAction(AddAction.OnPickScreenshots)
            assertThat(awaitItem()).isEqualTo(AddEvent.LaunchPicker(100))
            vm.onAction(AddAction.OnPicked(listOf("img:a", "img:b")))
            assertThat(awaitItem()).isEqualTo(AddEvent.OpenReading)
            vm.onAction(AddAction.OnPicked(emptyList()))
            assertThat(awaitItem()).isEqualTo(AddEvent.Close)
        }
        assertThat(store.saves.size).isEqualTo(2)
    }

    @Test fun `copied link is added at once`() = runTest {
        clipboard.text = "Check this https://www.instagram.com/reel/abc/"
        vm.events.test {
            vm.onAction(AddAction.OnPasteLink)
            assertThat(awaitItem()).isEqualTo(AddEvent.OpenReading)
        }
        assertThat(store.saves.single().url).isEqualTo("https://www.instagram.com/reel/abc/")
        assertThat(vm.state.value.field).isNull()
    }

    @Test fun `nothing copied shows the field, a non-link there says why, a link adds`() = runTest {
        clipboard.text = "buy oat milk"
        vm.onAction(AddAction.OnPasteLink)
        assertThat(vm.state.value.field).isEqualTo(PasteField())
        vm.onAction(AddAction.OnPasteTextChange("hello"))
        vm.onAction(AddAction.OnAddPasted)
        assertThat(vm.state.value.field?.error).isNotNull()
        vm.events.test {
            vm.onAction(AddAction.OnPasteTextChange("youtube.com/watch?v=x"))
            assertThat(vm.state.value.field?.error).isNull()
            vm.onAction(AddAction.OnAddPasted)
            assertThat(awaitItem()).isEqualTo(AddEvent.OpenReading)
        }
        assertThat(store.notes.size).isEqualTo(0)
        assertThat(vm.state.value.field).isNull()
    }

    @Test fun `a link already kept opens it with a snackbar`() = runTest {
        keeper.keepPastedLink("https://a.com/x")
        clipboard.text = "https://a.com/x"
        val snack = snackbars.messages
        vm.events.test {
            vm.onAction(AddAction.OnPasteLink)
            assertThat(awaitItem()).isEqualTo(AddEvent.OpenSave(1))
        }
        val msg: SnackbarMessage = snack.first()
        assertThat(msg.text is UiText.Res).isTrue()
    }
}
