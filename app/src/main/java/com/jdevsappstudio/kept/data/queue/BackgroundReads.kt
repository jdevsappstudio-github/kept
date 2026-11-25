package com.jdevsappstudio.kept.data.queue

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/** What the read queue asks of the system when Kept leaves the screen (BUILD-SPEC §4). Faked in tests. */
interface BackgroundReads {
    /** Cloud path: make sure [CloudReadWorker] is queued, so reads go on even if Android kills the process. */
    fun keepCloudReading()

    /** Nano path: reads wait for the app (AICore is foreground-only); one quiet notice says so. */
    fun showWaiting(count: Int)

    /** Back on screen: the in-app strip takes over. */
    fun clearNotice()
}

class WorkBackgroundReads(
    private val context: Context,
    private val notices: ReadNotifications,
) : BackgroundReads {

    override fun keepCloudReading() {
        val request = OneTimeWorkRequestBuilder<CloudReadWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        // KEEP: a running worker is left alone; a finished one is replaced.
        WorkManager.getInstance(context).enqueueUniqueWork(CloudReadWorker.NAME, ExistingWorkPolicy.KEEP, request)
    }

    override fun showWaiting(count: Int) = notices.waiting(count)

    override fun clearNotice() = notices.cancel()
}
