package com.jdevsappstudio.kept.data.ai

import com.google.firebase.ai.annotations.Generable
import com.google.firebase.ai.annotations.Guide
import kotlinx.serialization.Serializable

/**
 * What the reader returns for one save. Small on purpose: Nano answers in < 256 tokens (BUILD-SPEC §3).
 * The KSP processor turns this into a JSON schema that works on device (generateObject) and in the cloud.
 * Empty string = "not there"; nullable fields are avoided so both sides fill the same shape.
 */
@Serializable
@Generable(description = "A card describing one saved screenshot or link")
data class CardDraft(
    @Guide(description = "Short title, at most 8 words. The name of the thing, not a description of the screenshot.")
    val title: String,
    @Guide(
        description = "The one type that fits best. recipe only if ingredients or cooking steps are shown; an article about a dish is read.",
        enumValues = ["recipe", "place", "product", "movie_show", "video", "read", "event", "tip", "app", "idea", "other"],
    )
    val type: String,
    @Guide(description = "What kind of thing this is, as a short plural noun in lower case, e.g. movies, sneakers, cafes, workouts")
    val kind: String,
    @Guide(description = "Up to 5 short lower-case tags a person would search for", maxItems = 5)
    val tags: List<String>,
    @Guide(description = "Names of the separate things shown, if there are several (e.g. 5 movie titles). Empty if just one.", maxItems = 8)
    val items: List<String>,
    @Guide(description = "Place name or address if one is shown, else empty")
    val place: String,
    @Guide(description = "Date or date and time of an event if one is shown, as written, else empty")
    val date: String,
    @Guide(description = "A complete web link if one is shown in the content, else empty. Skip a cut-off browser address bar.")
    val link: String,
    @Guide(description = "One or two plain sentences on why this is worth keeping")
    val summary: String,
) {
    companion object
}
