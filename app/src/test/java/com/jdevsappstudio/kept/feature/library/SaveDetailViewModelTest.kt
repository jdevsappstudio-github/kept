package com.jdevsappstudio.kept.feature.library

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import com.jdevsappstudio.kept.R
import com.jdevsappstudio.kept.core.model.AddItemResult
import com.jdevsappstudio.kept.core.model.ReadBy
import com.jdevsappstudio.kept.core.model.SaveItem
import com.jdevsappstudio.kept.core.model.SentKind
import com.jdevsappstudio.kept.core.model.SentRequest
import com.jdevsappstudio.kept.data.ask.FakeSentLog
import com.jdevsappstudio.kept.core.model.SaveStatus
import com.jdevsappstudio.kept.core.model.SaveType
import com.jdevsappstudio.kept.data.capture.FakeCopier
import com.jdevsappstudio.kept.data.capture.FakeSaveStore
import com.jdevsappstudio.kept.data.capture.Keeper
import com.jdevsappstudio.kept.data.fill.FillOutcome
import com.jdevsappstudio.kept.data.quota.CloudMeter
import com.jdevsappstudio.kept.data.quota.testQuota
import com.jdevsappstudio.kept.feature.library.detail.DetailDialog
import com.jdevsappstudio.kept.feature.library.detail.SaveDetailAction
import com.jdevsappstudio.kept.feature.library.detail.SaveDetailEvent
import com.jdevsappstudio.kept.feature.library.detail.SaveDetailViewModel
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MainDispatcherExtension::class)
class SaveDetailViewModelTest {
    private val library = FakeLibrary()
    private val snackbars = testSnackbars()

    private val saveStore = FakeSaveStore()
    private val quota = testQuota()
    private val settings = FakeSettings().apply { cloudHelp.value = true }
    private val fill = FakeFillRepository(settings, quota)
    private val handle = SavedStateHandle(mapOf(SaveDetailViewModel.ARG_ID to 1L))

    private val sentLog = FakeSentLog()
    private fun vm() = SaveDetailViewModel(handle, library, snackbars, fill, Keeper(saveStore, FakeCopier()), quota, settings, sentLog)
    private fun link(id: Long, filledAt: Long? = null) =
        save(id, type = SaveType.VIDEO).copy(url = "https://instagram.com/reel/abc", sharedText = "Caption", filledAt = filledAt)
    private fun item(id: Long, name: String) = SaveItem(id, name, hidden = false, otherSaves = 0)

    @Test
    fun `delete with one item goes at once, closes once, Undo brings it back`() = runTest {
        library.put(save(1))
        library.items.value = mapOf(1L to listOf(item(10, "Prisoners")))
        val vm = vm()
        keepActive(vm.state)
        vm.events.test {
            vm.onAction(SaveDetailAction.OnDeleteClick)
            assertThat(awaitItem()).isEqualTo(SaveDetailEvent.Close)
            expectNoEvents()
        }
        val msg = snackbars.next()
        assertThat(msg.text.resId()).isEqualTo(R.plurals.snack_deleted)
        msg.action!!.onAction()
        assertThat(library.saves.value.keys).isEqualTo(setOf(1L))
    }

    @Test
    fun `delete with 2+ items asks first (H53)`() = runTest {
        library.put(save(1))
        library.items.value = mapOf(1L to listOf(item(10, "Prisoners"), item(11, "Gone Girl")))
        val vm = vm()
        keepActive(vm.state)

        vm.onAction(SaveDetailAction.OnDeleteClick)
        assertThat(vm.state.value.dialog).isEqualTo(DetailDialog.ConfirmDelete(2))
        assertThat(library.log).containsExactly()
        vm.onAction(SaveDetailAction.OnConfirmDialog)
        assertThat(library.log).containsExactly("delete [1]")
    }

    @Test
    fun `read again asks first only when the save was edited by hand`() = runTest {
        library.put(save(1, readBy = ReadBy.HAND))
        val vm = vm()
        keepActive(vm.state)
        vm.onAction(SaveDetailAction.OnReadAgainClick)
        assertThat(vm.state.value.dialog).isEqualTo(DetailDialog.ConfirmReadAgain)
        vm.onAction(SaveDetailAction.OnDismissDialog)
        assertThat(library.log).containsExactly()

        library.put(save(1, readBy = ReadBy.CLOUD))
        library.readAgainResult = false
        vm.onAction(SaveDetailAction.OnReadAgainClick)
        assertThat(vm.state.value.dialog).isNull()
        assertThat(library.log).containsExactly("readAgain 1")
        assertThat(snackbars.next().text.resId()).isEqualTo(R.string.snack_read_busy)
    }

    @Test
    fun `item sheet renames, deletes, and add tells when it is already on a shelf`() = runTest {
        library.put(save(1))
        library.items.value = mapOf(1L to listOf(item(10, "Prisoners")))
        val vm = vm()
        keepActive(vm.state)

        vm.onAction(SaveDetailAction.OnItemClick(item(10, "Prisoners")))
        vm.onAction(SaveDetailAction.OnRenameItem(" Prisoners (2013) "))
        assertThat(vm.state.value.editingItem).isNull()
        vm.onAction(SaveDetailAction.OnItemClick(item(10, "Prisoners")))
        vm.onAction(SaveDetailAction.OnDeleteItem)
        library.addResult = AddItemResult(added = true, alreadyOnShelf = "Movies")
        vm.onAction(SaveDetailAction.OnAddItem("Zodiac"))

        assertThat(library.log).containsExactly("renameItem 10 Prisoners (2013)", "deleteItem 10", "addItem 1 Zodiac")
        assertThat(snackbars.next().text.resId()).isEqualTo(R.string.snack_already_on_shelf)
    }

    @Test
    fun `a save deleted elsewhere closes the screen`() = runTest {
        library.put(save(1))
        val vm = vm()
        keepActive(vm.state)
        vm.events.test {
            library.saves.value = emptyMap()
            assertThat(awaitItem()).isEqualTo(SaveDetailEvent.Close)
        }
    }

    // ---- k-link: Fill in with cloud + Add a screenshot (H59) ----

    @Test
    fun `only a caption-only link offers Fill in, filled or waiting saves don't`() = runTest {
        library.put(save(1))
        val vm = vm()
        keepActive(vm.state)
        assertThat(vm.state.value.link).isNull()

        library.put(link(1))
        assertThat(vm.state.value.link!!.canFill).isEqualTo(true)
        assertThat(vm.state.value.link!!.showAnswersLeft).isEqualTo(false)
        library.put(link(1, filledAt = 9))
        assertThat(vm.state.value.link!!.canFill).isEqualTo(false)
        library.put(link(1).copy(status = SaveStatus.QUEUED))
        assertThat(vm.state.value.link!!.canFill).isEqualTo(false)
    }

    @Test
    fun `button shows answers left when 2 or fewer (review-ux #23)`() = runTest {
        library.put(link(1))
        repeat(3) { quota.use(CloudMeter.ANSWERS) }
        val vm = vm()
        keepActive(vm.state)
        assertThat(vm.state.value.link!!.answersLeft).isEqualTo(2)
        assertThat(vm.state.value.link!!.showAnswersLeft).isEqualTo(true)
    }

    @Test
    fun `filled says so`() = runTest {
        library.put(link(1))
        fill.outcomes += FillOutcome.Filled
        val vm = vm()
        keepActive(vm.state)
        vm.onAction(SaveDetailAction.OnFillInClick)
        assertThat(fill.calls).containsExactly(1L)
        assertThat(snackbars.next().text.resId()).isEqualTo(R.string.snack_fill_done)
    }

    @Test
    fun `cloud help off asks first, Turn on sends it, Not now sends nothing (H29)`() = runTest {
        library.put(link(1))
        settings.cloudHelp.value = false
        fill.outcomes += FillOutcome.Filled
        val vm = vm()
        keepActive(vm.state)

        vm.onAction(SaveDetailAction.OnFillInClick)
        assertThat(vm.state.value.dialog).isEqualTo(DetailDialog.CloudConsent)
        vm.onAction(SaveDetailAction.OnDismissDialog)
        assertThat(fill.calls).isEmpty()

        vm.onAction(SaveDetailAction.OnFillInClick)
        vm.onAction(SaveDetailAction.OnConfirmDialog)
        assertThat(settings.cloudHelp.value).isEqualTo(true)
        assertThat(fill.calls).containsExactly(1L)
    }

    @Test
    fun `0 answers left opens k-cloud-out, and the fill goes after an ad`() = runTest {
        library.put(link(1))
        repeat(quota.left(CloudMeter.ANSWERS)) { quota.use(CloudMeter.ANSWERS) }
        val vm = vm()
        keepActive(vm.state)
        vm.events.test {
            vm.onAction(SaveDetailAction.OnFillInClick)
            assertThat(awaitItem()).isEqualTo(SaveDetailEvent.OpenCloudOut)
            assertThat(fill.calls).isEmpty()

            quota.grantAd(CloudMeter.ANSWERS)
            fill.outcomes += FillOutcome.Filled
            vm.onAction(SaveDetailAction.OnAnswersGranted)
            assertThat(fill.calls).containsExactly(1L)
        }
    }

    @Test
    fun `page that couldn't be opened offers Add a screenshot, failure offers Try again`() = runTest {
        library.put(link(1))
        fill.outcomes += FillOutcome.PageUnreachable
        val vm = vm()
        keepActive(vm.state)
        vm.events.test {
            vm.onAction(SaveDetailAction.OnFillInClick)
            val msg = snackbars.next()
            assertThat(msg.text.resId()).isEqualTo(R.string.snack_fill_unreachable)
            assertThat(msg.action!!.label.resId()).isEqualTo(R.string.snack_add_screenshot)
            msg.action!!.onAction()
            assertThat(awaitItem()).isEqualTo(SaveDetailEvent.PickScreenshot)
        }

        fill.outcomes += FillOutcome.Failed(com.jdevsappstudio.kept.data.ai.ReadFailure.BUSY)
        fill.outcomes += FillOutcome.Filled
        vm.onAction(SaveDetailAction.OnFillInClick)
        val failed = snackbars.next()
        assertThat(failed.text.resId()).isEqualTo(R.string.snack_cloud_no_answer)
        failed.action!!.onAction()
        assertThat(fill.calls.size).isEqualTo(3)
        assertThat(snackbars.next().text.resId()).isEqualTo(R.string.snack_fill_done)
    }

    @Test
    fun `picked screenshot is attached and read again, cancel changes nothing`() = runTest {
        library.put(link(1))
        val vm = vm()
        keepActive(vm.state)
        vm.onAction(SaveDetailAction.OnScreenshotPicked(null))
        assertThat(saveStore.attached).isEqualTo(emptyMap())

        vm.onAction(SaveDetailAction.OnScreenshotPicked("img:abc"))
        assertThat(saveStore.attached.keys.toList()).containsExactly(1L)
        assertThat(snackbars.next().text.resId()).isEqualTo(R.string.snack_shot_reading)
    }

    @Test
    fun `cloud chip opens the save's newest sent request, or the list when it's gone`() = runTest {
        library.put(link(1, filledAt = 5))
        val vm = vm()
        keepActive(vm.state)
        vm.events.test {
            vm.onAction(SaveDetailAction.OnCloudChipClick)
            assertThat(awaitItem()).isEqualTo(SaveDetailEvent.OpenSent(null))
            sentLog.rows.value = listOf(
                SentRequest(7, 100, SentKind.READ, "x", 0, saveId = 1),
                SentRequest(8, 200, SentKind.FILL_IN, "y", 0, saveId = 1),
                SentRequest(9, 300, SentKind.ASK, "z", 0, saveId = null),
            )
            vm.onAction(SaveDetailAction.OnCloudChipClick)
            assertThat(awaitItem()).isEqualTo(SaveDetailEvent.OpenSent(8))
        }
    }
}
