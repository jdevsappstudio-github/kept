package com.jdevsappstudio.kept.data.wipe

import android.content.Context
import androidx.work.WorkManager
import com.jdevsappstudio.kept.data.db.KeptDatabase
import com.jdevsappstudio.kept.data.files.ImageLoader
import com.jdevsappstudio.kept.data.files.SaveFiles
import com.jdevsappstudio.kept.data.queue.CloudReadWorker
import com.jdevsappstudio.kept.data.queue.ReadNotifications
import com.jdevsappstudio.kept.data.settings.SettingsStore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.io.File

/** Delete everything (H69). An interface so the Privacy ViewModel can be tested with a fake. */
fun interface ContentWiper {
    suspend fun wipeContent()
}

/**
 * Content only (H69): every table (saves, tags, items, shelves, collections, search, Ask chat, sent log), the copied
 * images and thumbnails, recent searches and dismissed suggestions. Settings and today's quota counts stay.
 *
 * The read queue needs no stop: once the tables are empty its pending count drops to 0, which cancels a read in
 * flight, and its requeue / failure writes on a missing row change nothing. Runs to the end even if the screen goes.
 */
class AndroidContentWiper(
    private val context: Context,
    private val db: KeptDatabase,
    private val settings: SettingsStore,
    private val notices: ReadNotifications,
    private val io: CoroutineDispatcher,
) : ContentWiper {

    override suspend fun wipeContent() = withContext(NonCancellable + io) {
        WorkManager.getInstance(context).cancelUniqueWork(CloudReadWorker.NAME)
        notices.cancel()
        db.clearAllTables()
        listOf(SaveFiles.DIR, ImageLoader.THUMB_DIR).forEach { File(context.filesDir, it).deleteRecursively() }
        settings.clearContent()
    }
}
