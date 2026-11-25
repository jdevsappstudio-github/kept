package com.jdevsappstudio.kept.data.capture

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import com.jdevsappstudio.kept.core.model.SaveSource
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class KeeperTest {
    private val store = FakeSaveStore()
    private val copier = FakeCopier()
    private val keeper = Keeper(store, copier)

    @Test fun `picked images are queued with the picker source`() = runTest {
        val r = keeper.keepImages(listOf("img:a", "img:b"), SaveSource.PICKER)
        assertThat(r.ids).hasSize(2)
        assertThat(store.saves.map { it.source }).containsExactly(SaveSource.PICKER, SaveSource.PICKER)
        assertThat(store.saves.map { it.contentHash }).containsExactly("a", "b")
    }

    @Test fun `same bytes twice in one pick or already kept are skipped and their copies deleted`() = runTest {
        keeper.keepImages(listOf("img:a"), SaveSource.SHARE_IMAGE)
        val r = keeper.keepImages(listOf("img:a", "img:b#1", "img:b#2"), SaveSource.PICKER)
        assertThat(r.ids).hasSize(1)
        assertThat(r.duplicates).isEqualTo(2)
        assertThat(r.firstDuplicate).isEqualTo(1L) // the save already in Kept, not the in-batch twin
        assertThat(copier.deleted).hasSize(2)
    }

    @Test fun `too big becomes a note, broken streams are counted`() = runTest {
        val r = keeper.keepImages(listOf("big:x", "bad:y", "img:a"), SaveSource.PICKER)
        assertThat(r.kept).isEqualTo(2)
        assertThat(r.noteIds).hasSize(1)
        assertThat(r.unreadable).isEqualTo(1)
        assertThat(store.notes.single().second).isEqualTo(Keeper.TOO_BIG_TITLE)
    }

    @Test fun `over 100 are never copied and progress starts at zero`() = runTest {
        val seen = mutableListOf<Pair<Int, Int>>()
        val r = keeper.keepImages((1..105).map { "img:$it" }, SaveSource.PICKER) { d, t -> seen += d to t }
        assertThat(r.ids).hasSize(100)
        assertThat(r.overCap).isEqualTo(5)
        assertThat(copier.copies).isEqualTo(100)
        assertThat(seen.first()).isEqualTo(0 to 100)
        assertThat(seen.last()).isEqualTo(100 to 100)
    }

    @Test fun `shared text with a link is queued, plain text is a note`() = runTest {
        assertThat(keeper.keepSharedText("look https://a.com/x", "com.instagram.android")).isInstanceOf(TextKept.Queued::class)
        assertThat(keeper.keepSharedText("look again https://a.com/x", null)).isInstanceOf(TextKept.Duplicate::class)
        assertThat(keeper.keepSharedText("buy oat milk", null)).isInstanceOf(TextKept.Note::class)
        assertThat(keeper.keepSharedText("   ", null)).isEqualTo(TextKept.Empty)
    }

    @Test fun `pasted link is queued as PASTE, text without a link is never kept`() = runTest {
        assertThat(keeper.keepPastedLink("instagram.com/reel/abc")).isInstanceOf(TextKept.Queued::class)
        assertThat(store.saves.single().source).isEqualTo(SaveSource.PASTE)
        assertThat(store.saves.single().url).isEqualTo("https://instagram.com/reel/abc")
        assertThat(keeper.keepPastedLink("https://instagram.com/reel/abc")).isEqualTo(TextKept.Duplicate(1))
        assertThat(keeper.keepPastedLink("buy oat milk")).isEqualTo(TextKept.NotALink)
        assertThat(store.notes).hasSize(0)
    }
}
