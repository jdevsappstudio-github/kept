package com.jdevsappstudio.kept.data.ai

/** One prompt for both sides, short enough for Nano's 4000-token window. */
internal object ReaderPrompt {
    fun build(text: String?): String = buildString {
        append("Someone saved this to look at later. Read what is shown and fill in the card. ")
        append("Use only what is visible or written here; never invent details. ")
        append("If the screenshot shows several separate things (for example a list of movies), put their names in items.")
        if (!text.isNullOrBlank()) {
            append("\n\nShared text:\n")
            append(text.take(MAX_TEXT_CHARS))
        }
    }

    private const val MAX_TEXT_CHARS = 2000
}
