package com.jdevsappstudio.kept.data.capture

/** Links and note titles from shared or pasted text. Pure, so the rules live in one place. */
object CaptureText {
    private val URL = Regex("""https?://[^\s<>"']+""", RegexOption.IGNORE_CASE)

    /** A bare web address someone typed or copied without the scheme: "instagram.com/reel/abc", "www.x.in". */
    private val BARE = Regex("""^(www\.)?[a-z0-9-]+(\.[a-z0-9-]+)*\.[a-z]{2,}(/\S*)?$""", RegexOption.IGNORE_CASE)

    /** First web link in shared text ("Check this out https://…"), without trailing punctuation. */
    fun firstUrl(text: String?): String? =
        text?.let { URL.find(it)?.value?.trimEnd('.', ',', ')', ']', '!', '?', ';', ':') }

    /** Paste a link: a link anywhere in the text, or the whole text as a bare address (gets https://). */
    fun pastedUrl(text: String): String? {
        firstUrl(text)?.let { return it }
        val t = text.trim().trimEnd('.', ',', ')', ']', '!', '?', ';', ':')
        return if (BARE.matches(t)) "https://$t" else null
    }

    /** Title for a plain-text note: first non-blank line, at most [max] characters. */
    fun noteTitle(text: String, max: Int = 60): String {
        val line = text.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() }.orEmpty()
        return if (line.length <= max) line else line.take(max - 1).trimEnd() + "…"
    }
}
