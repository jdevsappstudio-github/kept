package com.jdevsappstudio.kept.data.queue

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.jdevsappstudio.kept.MainActivity
import com.jdevsappstudio.kept.R

/**
 * The one "Reading" notice (BUILD-SPEC §4): waiting (Nano, app left the screen), progress (cloud worker), done.
 * One id, so there is never more than one Kept notification. All quiet (low importance, no sound).
 * Binder calls: call from a background thread. If notifications are off, nothing is posted and the in-app strip still works.
 */
class ReadNotifications(private val context: Context) {
    private val manager = NotificationManagerCompat.from(context)
    private var channelMade = false

    /** Settings' Reading updates switch (H68), mirrored here by KeptApplication; off = nothing is posted. */
    @Volatile var readingUpdates = true

    fun canPost(): Boolean =
        manager.areNotificationsEnabled() &&
            (Build.VERSION.SDK_INT < 33 ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)

    fun waiting(count: Int) = post(
        context.resources.getQuantityString(R.plurals.notif_waiting, count, count),
        context.getString(R.string.notif_waiting_body),
    )

    fun progress(done: Int, total: Int) = post(
        context.getString(R.string.notif_progress, done, total),
        context.getString(R.string.notif_progress_body),
    ) { setProgress(total, done, false).setOngoing(true) }

    fun finished(read: Int) = post(
        context.resources.getQuantityString(R.plurals.notif_read, read, read),
        context.getString(R.string.notif_read_body),
    )

    fun cancel() = manager.cancel(ID)

    @SuppressLint("MissingPermission") // checked in canPost()
    private fun post(title: String, body: String, extra: NotificationCompat.Builder.() -> Unit = {}) {
        if (!readingUpdates || !canPost()) return
        if (!channelMade) {
            manager.createNotificationChannel(
                NotificationChannelCompat.Builder(CHANNEL, NotificationManagerCompat.IMPORTANCE_LOW)
                    .setName(context.getString(R.string.notif_channel_reading))
                    .setDescription(context.getString(R.string.notif_channel_reading_desc))
                    .build(),
            )
            channelMade = true
        }
        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_kept)
            .setContentTitle(title)
            .setContentText(body)
            .setContentIntent(open)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .apply(extra)
            .build()
        manager.notify(ID, notification)
    }

    private companion object {
        const val CHANNEL = "reading"
        const val ID = 1
    }
}
