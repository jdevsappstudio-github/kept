package com.jdevsappstudio.kept.core.presentation

import android.app.SearchManager
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.CalendarContract
import android.widget.Toast
import com.jdevsappstudio.kept.core.model.ItemAction.Target

/** Opens an [com.jdevsappstudio.kept.core.model.ItemAction] target. No permission needed for any of them. */
object ItemActionLauncher {

    fun launch(context: Context, target: Target) {
        when (target) {
            is Target.Copy -> {
                val clipboard = context.getSystemService(ClipboardManager::class.java)
                clipboard.setPrimaryClip(ClipData.newPlainText("Kept", target.text))
                // Android 13+ shows its own copied notice.
                if (android.os.Build.VERSION.SDK_INT < 33) Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
            }
            is Target.WebSearch -> start(
                context,
                Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, target.query),
                fallback = view("https://www.google.com/search?q=${Uri.encode(target.query)}"),
            )
            is Target.Maps -> start(context, view("geo:0,0?q=${Uri.encode(target.query)}"),
                fallback = view("https://www.google.com/maps/search/?api=1&query=${Uri.encode(target.query)}"))
            is Target.PlayStore -> start(context, view("market://search?q=${Uri.encode(target.query)}&c=apps"),
                fallback = view("https://play.google.com/store/search?q=${Uri.encode(target.query)}&c=apps"))
            is Target.OpenLink -> start(context, view(target.url), fallback = null)
            is Target.Calendar -> start(
                context,
                Intent(Intent.ACTION_INSERT, CalendarContract.Events.CONTENT_URI).apply {
                    putExtra(CalendarContract.Events.TITLE, target.title)
                    target.location?.let { putExtra(CalendarContract.Events.EVENT_LOCATION, it) }
                    target.notes?.let { putExtra(CalendarContract.Events.DESCRIPTION, it) }
                    target.beginMillis?.let { putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, it) }
                    if (target.allDay) putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, true)
                },
                fallback = null,
            )
        }
    }

    private fun view(uri: String) = Intent(Intent.ACTION_VIEW, Uri.parse(uri))

    private fun start(context: Context, intent: Intent, fallback: Intent?) {
        val flags = if (context is android.app.Activity) 0 else Intent.FLAG_ACTIVITY_NEW_TASK
        try {
            context.startActivity(intent.addFlags(flags))
        } catch (_: ActivityNotFoundException) {
            try {
                if (fallback != null) context.startActivity(fallback.addFlags(flags)) else nothing(context)
            } catch (_: ActivityNotFoundException) {
                nothing(context)
            }
        }
    }

    private fun nothing(context: Context) =
        Toast.makeText(context, "No app on this phone can open that", Toast.LENGTH_SHORT).show()
}
