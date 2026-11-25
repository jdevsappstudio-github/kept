package com.jdevsappstudio.kept.core.model

/**
 * One search result: the save plus a short piece of its text around the match ("…banana pancakes, North Goa…"),
 * matched words wrapped in [HIT_START] / [HIT_END] for the UI to bold.
 */
data class SearchHit(val save: Save, val snippet: String) {
    companion object {
        const val HIT_START = '\u0002'
        const val HIT_END = '\u0003'
    }
}
