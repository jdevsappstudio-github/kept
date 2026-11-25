package com.jdevsappstudio.kept.data.files

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.util.UUID

/** Result of copying one shared image into app storage. */
sealed interface CopyResult {
    data class Copied(val path: String, val sha256: String) : CopyResult
    /** Over [SaveFiles.MAX_BYTES]: kept as a note instead (k-shared-cant). */
    data object TooBig : CopyResult
    data object Failed : CopyResult
}

/**
 * Copies shared bytes raw (no decode) into `files/saves/`, hashing them in the same pass (BUILD-SPEC §4).
 * Runs on the image dispatcher (≤ 2 at once). Must finish while the share activity is alive: the URI grant ends with it.
 */
class SaveFiles(private val context: Context, private val dispatcher: CoroutineDispatcher) {

    suspend fun copyIn(uri: Uri): CopyResult = withContext(dispatcher) {
        val dir = File(context.filesDir, DIR).apply { mkdirs() }
        val ext = when (context.contentResolver.getType(uri)) {
            "image/png" -> "png"
            "image/webp" -> "webp"
            "image/gif" -> "gif"
            "image/heic", "image/heif" -> "heic"
            else -> "jpg"
        }
        val out = File(dir, "${UUID.randomUUID()}.$ext")
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            var total = 0L
            val input = context.contentResolver.openInputStream(uri) ?: return@withContext CopyResult.Failed
            input.use { inp ->
                out.outputStream().use { o ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = inp.read(buf)
                        if (n < 0) break
                        total += n
                        if (total > MAX_BYTES) {
                            o.close(); out.delete()
                            return@withContext CopyResult.TooBig
                        }
                        digest.update(buf, 0, n)
                        o.write(buf, 0, n)
                        ensureActive()
                    }
                }
            }
            if (total == 0L) {
                out.delete()
                CopyResult.Failed
            } else {
                CopyResult.Copied(out.absolutePath, digest.digest().joinToString("") { "%02x".format(it) })
            }
        } catch (e: Exception) {
            out.delete()
            if (e is kotlinx.coroutines.CancellationException) throw e
            CopyResult.Failed
        }
    }

    suspend fun delete(path: String) = withContext(dispatcher) { runCatching { File(path).delete() } }

    companion object {
        const val DIR = "saves"
        /** k-shared-cant: an image over 20 MB is kept as a note, not read. */
        const val MAX_BYTES = 20L * 1024 * 1024
    }
}
