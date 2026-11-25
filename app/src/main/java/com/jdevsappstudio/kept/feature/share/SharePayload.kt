package com.jdevsappstudio.kept.feature.share

import android.net.Uri

/** What another app shared, pulled out of the intent by [ShareActivity]. */
data class SharePayload(
    val images: List<Uri>,
    val text: String?,
    val sourceApp: String?,
)

internal object ShareText {
    private val URL = Regex("""https?://[^\s<>"']+""", RegexOption.IGNORE_CASE)

    /** First web link in shared text ("Check this out https://…"), without trailing punctuation. */
    fun firstUrl(text: String?): String? =
        text?.let { URL.find(it)?.value?.trimEnd('.', ',', ')', ']', '!', '?', ';', ':') }

    /** Title for a plain-text note: first non-blank line, at most [max] characters. */
    fun noteTitle(text: String, max: Int = 60): String {
        val line = text.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() }.orEmpty()
        return if (line.length <= max) line else line.take(max - 1).trimEnd() + "…"
    }
}
