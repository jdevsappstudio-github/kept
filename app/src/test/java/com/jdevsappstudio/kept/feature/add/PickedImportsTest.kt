package com.jdevsappstudio.kept.feature.add

import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import com.jdevsappstudio.kept.R
import com.jdevsappstudio.kept.core.presentation.UiText
import com.jdevsappstudio.kept.data.capture.FakeCopier
import com.jdevsappstudio.kept.data.capture.FakeSaveStore
import com.jdevsappstudio.kept.data.capture.Keeper
import com.jdevsappstudio.kept.feature.library.testSnackbars
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class PickedImportsTest {
    private val store = FakeSaveStore()
    private val keeper = Keeper(store, FakeCopier())
    private val snackbars = testSnackbars()

    @Test fun `progress adds up over two picks and clears at the end`() = runTest {
        val imports = PickedImports(keeper, snackbars, this)
        imports.start(listOf("img:a", "img:b"))
        imports.start(listOf("img:c"))
        assertThat(imports.progress.value).isEqualTo(AddProgress(0, 3))
        advanceUntilIdle()
        assertThat(imports.progress.value).isNull()
        assertThat(store.saves.size).isEqualTo(3)
    }

    @Test fun `what wasn't added is told`() = runTest {
        keeper.keepImages(listOf("img:a"), com.jdevsappstudio.kept.core.model.SaveSource.SHARE_IMAGE)
        val imports = PickedImports(keeper, snackbars, this)
        snackbars.messages.test {
            imports.start(listOf("img:a", "bad:x", "img:b"))
            advanceUntilIdle()
            assertThat((awaitItem().text as UiText.Plural).id).isEqualTo(R.plurals.add_dupes)
            assertThat((awaitItem().text as UiText.Plural).id).isEqualTo(R.plurals.add_unreadable)
        }
    }

    @Test fun `all duplicates gets its own line`() = runTest {
        keeper.keepImages(listOf("img:a"), com.jdevsappstudio.kept.core.model.SaveSource.SHARE_IMAGE)
        val imports = PickedImports(keeper, snackbars, this)
        snackbars.messages.test {
            imports.start(listOf("img:a"))
            advanceUntilIdle()
            assertThat((awaitItem().text as UiText.Plural).id).isEqualTo(R.plurals.add_all_dupes)
        }
    }
}
