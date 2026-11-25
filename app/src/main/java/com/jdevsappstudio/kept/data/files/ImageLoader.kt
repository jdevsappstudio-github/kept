package com.jdevsappstudio.kept.data.files

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

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

    companion object {
        const val READ_EDGE = 1024
    }
}
