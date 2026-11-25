package com.jdevsappstudio.kept.data.capture

import android.net.Uri
import com.jdevsappstudio.kept.core.model.NewSave
import com.jdevsappstudio.kept.core.model.SaveSource
import com.jdevsappstudio.kept.data.files.CopyResult
import com.jdevsappstudio.kept.data.files.SaveFiles
import com.jdevsappstudio.kept.data.saves.SaveLocalDataSource

/**
 * Copies one shared or picked image into app storage. [ref] is the content URI as a string, so [Keeper] stays a plain
 * JVM class (tests use a fake).
 */
interface ImageCopier {
    suspend fun copyIn(ref: String): CopyResult
    suspend fun delete(path: String)
}

class SaveFilesCopier(private val files: SaveFiles) : ImageCopier {
    override suspend fun copyIn(ref: String): CopyResult = files.copyIn(Uri.parse(ref))
    override suspend fun delete(path: String) {
        files.delete(path)
    }
}

/** What happened to one batch of images (a share or a pick). */
data class ImagesKept(
    /** New QUEUED saves, in pick order. */
    val ids: List<Long> = emptyList(),
    /** Too big to read: kept as notes. */
    val noteIds: List<Long> = emptyList(),
    val duplicates: Int = 0,
    val firstDuplicate: Long? = null,
    /** Couldn't be opened (the other app revoked or gave a broken stream). */
    val unreadable: Int = 0,
    /** Over [Keeper.MAX_PER_BATCH]; never copied. */
    val overCap: Int = 0,
) {
    val kept get() = ids.size + noteIds.size
}

/** Add a screenshot to a caption-only link (k-link). */
enum class ImageAttached {
    /** Copied and back in line: the card is read again with the image, in place. */
    QUEUED,
    /** Over the size limit: nothing changed. */
    TOO_BIG,
    /** Couldn't open the picked image: nothing changed. */
    UNREADABLE,
    /** The save is gone or already has an image. */
    NOT_ALLOWED,
}

sealed interface TextKept {
    /** A link, queued to be read. */
    data class Queued(val id: Long) : TextKept
    data class Duplicate(val id: Long) : TextKept
    /** Shared plain text: kept as a finished note, never sent anywhere. */
    data class Note(val id: Long) : TextKept
    /** Paste only: there's text, but no link in it. */
    data object NotALink : TextKept
    data object Empty : TextKept
}

/**
 * The one way anything gets into Kept (share target, photo picker, Paste a link): copy raw bytes, skip duplicates,
 * insert QUEUED rows. The read queue sees the new rows through Room and starts on its own.
 */
class Keeper(private val store: SaveLocalDataSource, private val copier: ImageCopier) {

    /** [onProgress] gets (0, total) first, then one call per image. */
    suspend fun keepImages(
        refs: List<String>,
        source: SaveSource,
        sourceApp: String? = null,
        text: String? = null,
        onProgress: suspend (done: Int, total: Int) -> Unit = { _, _ -> },
    ): ImagesKept {
        val distinct = refs.distinct()
        val batch = distinct.take(MAX_PER_BATCH)
        onProgress(0, batch.size)
        val url = CaptureText.firstUrl(text)
        val fresh = mutableListOf<NewSave>()
        val seen = HashSet<String>()
        var firstDup: Long? = null
        var duplicates = 0
        var tooBig = 0
        var unreadable = 0
        batch.forEachIndexed { i, ref ->
            when (val copy = copier.copyIn(ref)) {
                is CopyResult.Copied -> {
                    // -1 = same bytes twice in this batch (nothing in the store to open yet)
                    val dup = if (copy.sha256 in seen) -1L else store.findDuplicate(copy.sha256, url = null)
                    if (dup != null) {
                        copier.delete(copy.path)
                        duplicates++
                        if (firstDup == null && dup > 0) firstDup = dup
                    } else {
                        seen += copy.sha256
                        fresh += NewSave(source, sourceApp, text, url, copy.path, copy.sha256)
                    }
                }
                CopyResult.TooBig -> tooBig++
                CopyResult.Failed -> unreadable++
            }
            onProgress(i + 1, batch.size)
        }
        val ids = if (fresh.isEmpty()) emptyList() else store.add(fresh)
        val noteTitle = text?.let(CaptureText::noteTitle)?.takeIf { it.isNotEmpty() } ?: TOO_BIG_TITLE
        val notes = List(tooBig) { store.addNote(NewSave(source, sourceApp, text, url, imagePath = null), noteTitle) }
        return ImagesKept(ids, notes, duplicates, firstDup, unreadable, overCap = distinct.size - batch.size)
    }

    /** Share target: a link is queued to be read; any other text becomes a note. */
    suspend fun keepSharedText(text: String?, sourceApp: String?): TextKept {
        val clean = text?.trim().orEmpty()
        if (clean.isEmpty()) return TextKept.Empty
        val url = CaptureText.firstUrl(clean)
            ?: return TextKept.Note(store.addNote(NewSave(SaveSource.SHARE_TEXT, sourceApp, clean, null, imagePath = null), CaptureText.noteTitle(clean)))
        return keepLink(NewSave(SaveSource.SHARE_TEXT, sourceApp, clean, url, imagePath = null))
    }

    /** Paste a link: only a link is kept ("instagram.com/reel/…" counts); anything else is [TextKept.NotALink]. */
    suspend fun keepPastedLink(text: String?): TextKept {
        val clean = text?.trim().orEmpty()
        if (clean.isEmpty()) return TextKept.Empty
        val url = CaptureText.pastedUrl(clean) ?: return TextKept.NotALink
        return keepLink(NewSave(SaveSource.PASTE, sourceApp = null, sharedText = clean, url = url, imagePath = null))
    }

    /**
     * k-link 'Add a screenshot': the picked image joins the link save (no duplicate check; it belongs to this save)
     * and the card is read again with it. The old card stays until the new one lands.
     */
    suspend fun attachImage(saveId: Long, ref: String): ImageAttached =
        when (val copy = copier.copyIn(ref)) {
            is CopyResult.Copied ->
                if (store.attachImage(saveId, copy.path, copy.sha256)) ImageAttached.QUEUED
                else {
                    copier.delete(copy.path)
                    ImageAttached.NOT_ALLOWED
                }
            CopyResult.TooBig -> ImageAttached.TOO_BIG
            CopyResult.Failed -> ImageAttached.UNREADABLE
        }

    private suspend fun keepLink(save: NewSave): TextKept {
        store.findDuplicate(contentHash = null, url = save.url)?.let { return TextKept.Duplicate(it) }
        return TextKept.Queued(store.add(listOf(save)).first())
    }

    companion object {
        /** Per share and per pick (H45). */
        const val MAX_PER_BATCH = 100
        const val TOO_BIG_TITLE = "Image (too big to read)"
    }
}
