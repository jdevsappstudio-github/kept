package com.jdevsappstudio.kept.data.ai

import android.os.SystemClock
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.InferenceMode
import com.google.firebase.ai.OnDeviceConfig
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerateContentResponse
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.PublicPreviewAPI
import com.google.firebase.ai.type.ThinkingLevel
import com.google.firebase.ai.type.Tool
import com.google.firebase.ai.type.UrlRetrievalStatus
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import com.google.firebase.ai.type.thinkingConfig
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.delay

sealed interface FillResult {
    data class Success(val card: CardDraft, val model: String, val millis: Long) : FillResult

    /**
     * The model answered but couldn't open the page (sign-in wall, paywall, YouTube, unsafe, or it never tried).
     * The answer would only repeat the caption, so it is thrown away and nothing is counted (H59).
     */
    data class PageUnreachable(val status: String) : FillResult

    data class Failure(val reason: ReadFailure, val detail: String?) : FillResult
}

/** Fill in with cloud (H2: `ONLY_IN_CLOUD` + URL context, on tap). */
interface PageFiller {
    suspend fun fill(prompt: String): FillResult
}

/**
 * Gemini with the URL-context tool, answering in the card schema. Model from Remote Config `fill_*` (H59: as Ask,
 * 3.5 Flash-Lite low). Busy policy as reads (H47): same model once more, then the cheaper fallback.
 * Only a `SUCCESS` retrieval counts as filled: docs/ai-logic/url-context lists sign-in pages, paywalls and YouTube
 * as unsupported.
 */
@OptIn(PublicPreviewAPI::class)
class FirebasePageFiller(
    private val firebaseConfigured: Boolean,
    private val config: ReadModelConfig,
) : PageFiller {

    private val ai by lazy { Firebase.ai(backend = GenerativeBackend.googleAI()) }
    private val models = ConcurrentHashMap<CloudChoice, GenerativeModel>()

    private fun model(choice: CloudChoice): GenerativeModel = models.getOrPut(choice) {
        ai.generativeModel(
            modelName = choice.model,
            generationConfig = generationConfig { thinkingConfig = thinkingConfig { thinkingLevel = choice.effort.sdk } },
            tools = listOf(Tool.urlContext()),
            onDeviceConfig = OnDeviceConfig(mode = InferenceMode.ONLY_IN_CLOUD),
        )
    }

    override suspend fun fill(prompt: String): FillResult {
        if (!firebaseConfigured) return FillResult.Failure(ReadFailure.NOT_SET_UP, "Firebase is not configured in this build")
        val main = config.main()
        var result = attempt(main, prompt)
        if (result.isBusy()) {
            delay(BUSY_RETRY_MS)
            result = attempt(main, prompt)
        }
        if (result.isBusy()) {
            val fallback = config.fallback()
            if (fallback.model != main.model) result = attempt(fallback, prompt)
        }
        return result
    }

    private fun FillResult.isBusy() = this is FillResult.Failure && reason == ReadFailure.BUSY

    private suspend fun attempt(choice: CloudChoice, prompt: String): FillResult {
        val started = SystemClock.elapsedRealtime()
        return try {
            val response = model(choice).generateObject(CardDraft.firebaseAISchema(), content { text(prompt) })
            val millis = SystemClock.elapsedRealtime() - started
            val raw = response.response
            val statuses = retrievals(raw)
            val u = raw.usageMetadata
            Log.i(
                TAG,
                "fill ${choice.model} ${millis}ms urls=${statuses.map { it.name }} tokens in=${u?.promptTokenCount} " +
                    "tool=${u?.toolUsePromptTokenCount} out=${u?.candidatesTokenCount} thoughts=${u?.thoughtsTokenCount}",
            )
            if (UrlRetrievalStatus.SUCCESS !in statuses) {
                return FillResult.PageUnreachable(statuses.joinToString { it.name }.ifEmpty { "NOT_FETCHED" })
            }
            val card = response.getObject() ?: return FillResult.Failure(ReadFailure.UNREADABLE, "Empty answer")
            if (card.title.isBlank()) FillResult.Failure(ReadFailure.UNREADABLE, "Blank title")
            else FillResult.Success(card, choice.model, millis)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val reason = AiErrors.classify(e)
            Log.w(TAG, "fill ${choice.model} failed $reason: ${e::class.simpleName}: ${e.message}")
            FillResult.Failure(reason, e.message ?: e::class.simpleName)
        }
    }

    private fun retrievals(response: GenerateContentResponse): List<UrlRetrievalStatus> =
        response.candidates.flatMap { c -> c.urlContextMetadata?.urlMetadata.orEmpty().mapNotNull { it.urlRetrievalStatus } }

    private val Effort.sdk: ThinkingLevel
        get() = when (this) {
            Effort.MINIMAL -> ThinkingLevel.MINIMAL
            Effort.LOW -> ThinkingLevel.LOW
            Effort.MEDIUM -> ThinkingLevel.MEDIUM
            Effort.HIGH -> ThinkingLevel.HIGH
        }

    private companion object {
        const val BUSY_RETRY_MS = 2_000L
        const val TAG = "KeptFill"
    }
}
