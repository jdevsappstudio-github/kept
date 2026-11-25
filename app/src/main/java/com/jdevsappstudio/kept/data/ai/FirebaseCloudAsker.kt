package com.jdevsappstudio.kept.data.ai

import android.os.SystemClock
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.InferenceMode
import com.google.firebase.ai.OnDeviceConfig
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.PublicPreviewAPI
import com.google.firebase.ai.type.ThinkingLevel
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import com.google.firebase.ai.type.thinkingConfig
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.delay

/**
 * Ask in the cloud (H2: `ONLY_IN_CLOUD`; H58: 3.5 Flash-Lite low from Remote Config, whole JSON answer).
 * Busy policy as reads (H47): same model once more after [BUSY_RETRY_MS], then one try on the cheaper fallback.
 */
@OptIn(PublicPreviewAPI::class)
class FirebaseCloudAsker(
    private val firebaseConfigured: Boolean,
    private val config: ReadModelConfig,
) : CloudAsker {

    private val ai by lazy { Firebase.ai(backend = GenerativeBackend.googleAI()) }
    private val models = ConcurrentHashMap<CloudChoice, GenerativeModel>()

    private fun model(choice: CloudChoice): GenerativeModel = models.getOrPut(choice) {
        ai.generativeModel(
            modelName = choice.model,
            generationConfig = generationConfig { thinkingConfig = thinkingConfig { thinkingLevel = choice.effort.sdk } },
            onDeviceConfig = OnDeviceConfig(mode = InferenceMode.ONLY_IN_CLOUD),
        )
    }

    override suspend fun ask(prompt: String): AskResult {
        if (!firebaseConfigured) return AskResult.Failure(ReadFailure.NOT_SET_UP, "Firebase is not configured in this build")
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

    private fun AskResult.isBusy() = this is AskResult.Failure && reason == ReadFailure.BUSY

    private suspend fun attempt(choice: CloudChoice, prompt: String): AskResult {
        val started = SystemClock.elapsedRealtime()
        return try {
            val response = model(choice).generateObject(AskAnswer.firebaseAISchema(), content { text(prompt) })
            val answer = response.getObject() ?: return AskResult.Failure(ReadFailure.UNREADABLE, "Empty answer")
            val millis = SystemClock.elapsedRealtime() - started
            val u = response.response.usageMetadata
            Log.i(TAG, "ask ${choice.model} ${millis}ms tokens in=${u?.promptTokenCount} out=${u?.candidatesTokenCount} thoughts=${u?.thoughtsTokenCount}")
            if (answer.answer.isBlank()) AskResult.Failure(ReadFailure.UNREADABLE, "Blank answer")
            else AskResult.Success(answer.copy(answer = answer.answer.trim()), choice.model, millis)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val reason = AiErrors.classify(e)
            Log.w(TAG, "ask ${choice.model} failed $reason: ${e::class.simpleName}: ${e.message}")
            AskResult.Failure(reason, e.message ?: e::class.simpleName)
        }
    }

    private val Effort.sdk: ThinkingLevel
        get() = when (this) {
            Effort.MINIMAL -> ThinkingLevel.MINIMAL
            Effort.LOW -> ThinkingLevel.LOW
            Effort.MEDIUM -> ThinkingLevel.MEDIUM
            Effort.HIGH -> ThinkingLevel.HIGH
        }

    private companion object {
        const val BUSY_RETRY_MS = 2_000L
        const val TAG = "KeptAsk"
    }
}
