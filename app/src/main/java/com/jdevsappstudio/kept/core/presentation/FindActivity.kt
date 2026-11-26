package com.jdevsappstudio.kept.core.presentation

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper

/** The Activity behind a Compose context. Inside a dialog destination LocalContext is a ContextThemeWrapper. */
tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
