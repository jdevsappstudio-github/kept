package com.jdevsappstudio.kept.feature.library

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.contains
import assertk.assertions.isEqualTo
import com.jdevsappstudio.kept.core.model.QueueCounts
import com.jdevsappstudio.kept.core.model.SaveType
import com.jdevsappstudio.kept.core.model.TypeChip
import com.jdevsappstudio.kept.data.queue.QueuePause
import com.jdevsappstudio.kept.feature.home.HomeAction
import com.jdevsappstudio.kept.feature.home.HomeViewModel
import com.jdevsappstudio.kept.feature.home.QueueView
import com.jdevsappstudio.kept.feature.library.shelves.ShelvesAction
import com.jdevsappstudio.kept.feature.library.shelves.ShelvesViewModel
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MainDispatcherExtension::class)
class HomeAndShelvesViewModelTest {
    private val library = FakeLibrary()
    private val settings = FakeSettings()
    private val collections = FakeCollections()
    private val snackbars = testSnackbars()

    private fun home() = HomeViewModel(
        flowOf(QueueView(QueueCounts(1, 0, 0, 9), null, QueuePause.NONE)), settings, library, collections, snackbars,
    )

    @Test
    fun `home shows chips, the newest save and the first 4 shown shelves`() = runTest {
        library.chips.value = listOf(TypeChip(SaveType.RECIPE, 12), TypeChip(SaveType.PLACE, 9))
        library.put(save(1), save(7, title = "Paneer tikka"))
        library.shelves.value = listOf(
            shelf("movies"), shelf("shoes", hidden = true), shelf("skincare"), shelf("decor"), shelf("plants"), shelf("sarees"),
        )
        val vm = home()
        keepActive(vm.state)

        val s = vm.state.value
        assertThat(s.total).isEqualTo(21)
        assertThat(s.hero?.title).isEqualTo("Paneer tikka")
        assertThat(s.shelves.map { it.kind }).containsExactly("movies", "skincare", "decor", "plants")
        assertThat(s.allShelves).isEqualTo(6)
        assertThat(s.counts.read).isEqualTo(9)
    }

    @Test
    fun `home open purges stale deletes and the cloud help switch writes the setting`() = runTest {
        val vm = home()
        assertThat(library.log).contains("purgeStale")
        vm.onAction(HomeAction.SetCloudHelp(true))
        assertThat(settings.cloudHelp.value).isEqualTo(true)
    }

    @Test
    fun `shelves marks the first 4 shown as on home, switch and drag write through`() = runTest {
        library.shelves.value = listOf(shelf("a"), shelf("b", hidden = true), shelf("c"), shelf("d"), shelf("e"), shelf("f"))
        val vm = ShelvesViewModel(library)
        keepActive(vm.state)
        assertThat(vm.state.value.onHome).isEqualTo(setOf("a", "c", "d", "e"))

        vm.onAction(ShelvesAction.OnShowOnHome("a", show = false))
        assertThat(vm.state.value.onHome).isEqualTo(setOf("c", "d", "e", "f"))

        vm.onAction(ShelvesAction.OnReorder(listOf("f", "a")))
        assertThat(library.log).containsExactly("hidden a true", "reorder [f, a]")
    }
}
