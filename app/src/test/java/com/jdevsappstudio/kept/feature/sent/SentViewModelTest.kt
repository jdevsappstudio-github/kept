package com.jdevsappstudio.kept.feature.sent

import androidx.lifecycle.SavedStateHandle
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import com.jdevsappstudio.kept.core.model.SentKind
import com.jdevsappstudio.kept.data.ask.FakeSentLog
import com.jdevsappstudio.kept.feature.library.MainDispatcherExtension
import com.jdevsappstudio.kept.feature.library.keepActive
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MainDispatcherExtension::class)
class SentViewModelTest {
    private val log = FakeSentLog()

    private fun TestScope.vm(openId: Long? = null) =
        SentViewModel(SavedStateHandle(openId?.let { mapOf(SentViewModel.ARG_OPEN_ID to it) } ?: emptyMap()), log)
            .also { keepActive(it.state) }

    @Test
    fun `empty says nothing was sent`() = runTest {
        assertThat(vm().state.value.empty).isTrue()
    }

    @Test
    fun `newest first, opened at the chip's request, rows toggle, images noted`() = runTest {
        val now = System.currentTimeMillis()
        log.log(SentKind.READ, "a link", images = 1, saveId = 4, now = now - 2)
        val ask = log.log(SentKind.ASK, "prompt", now = now - 1)
        val vm = vm(openId = ask)
        val s = vm.state.value
        assertThat(s.rows.map { it.kind }).containsExactly(SentKind.ASK, SentKind.READ)
        assertThat(s.expanded).isEqualTo(setOf(ask))
        assertThat(s.openId).isEqualTo(ask)
        assertThat(s.anyImages).isTrue()
        vm.onAction(SentAction.OnToggle(ask))
        assertThat(vm.state.value.expanded.contains(ask)).isFalse()
    }
}
