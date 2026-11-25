package com.jdevsappstudio.kept.core.model

/** The 11 types (H28). [wire] is what the model answers with; keep the two lists in sync. */
enum class SaveType(val wire: String, val label: String) {
    RECIPE("recipe", "Recipe"),
    PLACE("place", "Place"),
    PRODUCT("product", "Product"),
    MOVIE_SHOW("movie_show", "Movie · Show"),
    VIDEO("video", "Video"),
    READ("read", "Read"),
    EVENT("event", "Event"),
    TIP("tip", "Tip"),
    APP("app", "App"),
    IDEA("idea", "Idea"),
    OTHER("other", "Other");

    companion object {
        fun fromWire(value: String?): SaveType =
            entries.firstOrNull { it.wire.equals(value?.trim(), ignoreCase = true) } ?: OTHER
    }
}
