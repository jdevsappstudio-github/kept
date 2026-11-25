package com.jdevsappstudio.kept.data.capture

import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.view.textclassifier.TextClassifier

/** What's on the clipboard, judged without reading it. */
enum class ClipPeek {
    /** Android (12+) classified the clip as a link. */
    LINK,
    /** Text, but Android can't tell (or says it isn't a link). */
    TEXT,
    NONE,
}

/**
 * Paste a link (H56): [peek] when the + sheet opens (no "pasted from your clipboard" toast), [readText] only when the
 * person taps Paste a link. Call both while Kept has focus; Android hides the clipboard from apps in the background.
 */
interface ClipboardLinks {
    fun peek(): ClipPeek
    fun readText(): String?
}

class AndroidClipboard(private val context: Context) : ClipboardLinks {
    private val manager get() = context.getSystemService(ClipboardManager::class.java)

    override fun peek(): ClipPeek {
        val desc = runCatching { manager?.primaryClipDescription }.getOrNull() ?: return ClipPeek.NONE
        if (isSensitive(desc)) return ClipPeek.NONE
        if (desc.hasMimeType(ClipDescription.MIMETYPE_TEXT_URILIST)) return ClipPeek.LINK
        val isText = desc.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN) || desc.hasMimeType(ClipDescription.MIMETYPE_TEXT_HTML)
        if (!isText) return ClipPeek.NONE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            desc.classificationStatus == ClipDescription.CLASSIFICATION_COMPLETE &&
            desc.getConfidenceScore(TextClassifier.TYPE_URL) >= LINK_CONFIDENCE
        ) return ClipPeek.LINK
        return ClipPeek.TEXT
    }

    override fun readText(): String? = runCatching {
        val clip = manager?.primaryClip?.takeIf { it.itemCount > 0 } ?: return null
        clip.getItemAt(0).coerceToText(context)?.toString()
    }.getOrNull()

    /** Passwords and OTPs copied from a password manager: never offered. */
    private fun isSensitive(desc: ClipDescription): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && desc.extras?.getBoolean(ClipDescription.EXTRA_IS_SENSITIVE) == true

    private companion object {
        const val LINK_CONFIDENCE = 0.5f
    }
}
