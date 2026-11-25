package com.jdevsappstudio.kept.data.saves

/**
 * Merge key for items across saves (H52): lowercase, punctuation gone, a trailing year dropped.
 * "Gone Girl", "gone girl!" and "Gone Girl (2014)" are one shelf row.
 */
object ItemKey {
    // Only a bracketed or separated year: "Blade Runner 2049" keeps its number.
    private val trailingYear = Regex("""\s*(?:[(\[]\s*(?:18|19|20)\d{2}\s*[)\]]|[,·\-–]\s*(?:18|19|20)\d{2})\s*$""")
    // \p{M} keeps Indic vowel signs: "अंधाधुन" must not split into letters.
    private val notWord = Regex("""[^\p{L}\p{M}\p{N}]+""")

    fun of(name: String): String {
        val lower = name.trim().lowercase()
        val noYear = lower.replace(trailingYear, "").ifBlank { lower } // "1917" stays a title
        return noYear.replace(notWord, " ").trim()
    }
}
