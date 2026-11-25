package com.jdevsappstudio.kept.data.ai

import com.google.firebase.ai.annotations.Generable
import com.google.firebase.ai.annotations.Guide
import kotlinx.serialization.Serializable

/** H58: Ask answers arrive whole, as JSON. [refs] are the numbers of the sources in the prompt ([1] = 1). */
@Serializable
@Generable(description = "An answer to a question about the person's own saved things")
data class AskAnswer(
    @Guide(
        description = "The answer in plain, friendly sentences, under 120 words. Use only the numbered saves given. " +
            "Name saves by their titles, never by their numbers. If the saves don't answer the question, say so in one sentence.",
    )
    val answer: String,
    @Guide(description = "Numbers of the saves the answer uses, most useful first. Empty if none.", maxItems = 8)
    val refs: List<Int>,
) {
    companion object
}

/** The Ask model call. One method so the repository can be tested without Firebase. */
interface CloudAsker {
    suspend fun ask(prompt: String): AskResult
}

sealed interface AskResult {
    data class Success(val answer: AskAnswer, val model: String, val millis: Long) : AskResult
    /** Same typed reasons as reads ([AiErrors]); the chat only needs 'failed, try again' vs 'offline'. */
    data class Failure(val reason: ReadFailure, val detail: String? = null) : AskResult
}
