package com.jdevsappstudio.kept.core.presentation

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.jdevsappstudio.kept.feature.ask.AskCopy
import com.jdevsappstudio.kept.feature.ask.ReportEmail

/** H40: the person's mail app, prefilled; nothing goes until they press Send there (Report on Ask, Send feedback). */
fun Context.openEmail(email: ReportEmail) {
    val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:"))
        .putExtra(Intent.EXTRA_EMAIL, arrayOf(email.to))
        .putExtra(Intent.EXTRA_SUBJECT, email.subject)
        .putExtra(Intent.EXTRA_TEXT, email.body)
    try {
        startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(this, AskCopy.NO_MAIL_APP, Toast.LENGTH_SHORT).show()
    }
}

/** A web page (privacy policy, GitHub) in the browser. */
fun Context.openUrl(url: String) {
    try {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(this, "No browser found on this phone.", Toast.LENGTH_SHORT).show()
    }
}
