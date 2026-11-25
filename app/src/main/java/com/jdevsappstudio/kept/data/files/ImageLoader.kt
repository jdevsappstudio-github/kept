package com.jdevsappstudio.kept.data.files

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File

/** Decodes images off the main thread, capped in size so 100 queued screenshots never blow memory. */
class ImageLoader(private val context: Context, private val dispatcher: CoroutineDispatcher) {

    /** Long edge ≤ [maxEdge] px: cheap power-of-two subsampling down to ≥ maxEdge, then one exact scale. */
    suspend fun decodeScaled(uri: Uri, maxEdge: Int = READ_EDGE): Bitmap? = withContext(dispatcher) {
        runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxEdge) sample *= 2
            val options = BitmapFactory.Options().apply { inSampleSize = sample }
            val decoded = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
                ?: return@runCatching null
            val edge = maxOf(decoded.width, decoded.height)
            if (edge <= maxEdge) return@runCatching decoded
            val scale = maxEdge.toFloat() / edge
            Bitmap.createScaledBitmap(decoded, (decoded.width * scale).toInt(), (decoded.height * scale).toInt(), true)
                .also { if (it !== decoded) decoded.recycle() }
        }.getOrNull()
    }

    /**
     * Writes the list thumbnail from an already-decoded [source] (no second decode): short edge [THUMB_EDGE] px,
     * uncropped (cards crop), never upscaled. Returns the file path, or null if it couldn't be written.
     * Does not recycle [source].
     */
    suspend fun writeThumb(source: Bitmap, saveId: Long): String? = withContext(dispatcher) {
        runCatching {
            val dir = File(context.filesDir, THUMB_DIR).apply { mkdirs() }
            val out = File(dir, "$saveId.jpg")
            val short = minOf(source.width, source.height)
            val scale = if (short > THUMB_EDGE) THUMB_EDGE.toFloat() / short else 1f
            val thumb = if (scale < 1f) {
                Bitmap.createScaledBitmap(source, (source.width * scale).toInt().coerceAtLeast(1),
                    (source.height * scale).toInt().coerceAtLeast(1), true)
            } else source
            try {
                out.outputStream().use { if (!thumb.compress(Bitmap.CompressFormat.JPEG, THUMB_QUALITY, it)) error("compress") }
            } finally {
                if (thumb !== source) thumb.recycle()
            }
            out.absolutePath
        }.getOrNull()
    }

    companion object {
        const val READ_EDGE = 1024
        /** Short edge: a 78×98 dp list card is ~275×345 px on a 3.5x phone; detail uses the full image. */
        const val THUMB_EDGE = 320
        const val THUMB_QUALITY = 85
        const val THUMB_DIR = "thumbs"
    }
}
