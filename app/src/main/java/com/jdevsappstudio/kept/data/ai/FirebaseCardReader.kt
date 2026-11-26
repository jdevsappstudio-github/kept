@file:OptIn(PublicPreviewAPI::class)

package com.jdevsappstudio.kept.data.ai

import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.ai.DownloadStatus
import com.google.firebase.ai.GenerativeModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import com.google.firebase.ai.InferenceMode
import com.google.firebase.ai.InferenceSource
import com.google.firebase.ai.OnDeviceConfig
import com.google.firebase.ai.OnDeviceModelOption
import com.google.firebase.ai.OnDeviceModelStatus
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Content
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
 * Reads a save into a [CardDraft] through Firebase AI Logic (hackathon rule H3).
 * The inference mode is per model, not per request, so the device and each cloud preset get their own instance.
 *
 * Cloud overload policy (owner, 25 Nov): retry the same model once after [BUSY_RETRY_MS], then one try on the
 * cheaper [CloudPreset.BUSY_FALLBACK] (never a pricier model). Anything left goes back to the queue's backoff.
 */
class FirebaseCardReader(
    private val firebaseConfigured: Boolean,
    private val config: ReadModelConfig,
) : CardReader {

    private val ai by lazy { Firebase.ai(backend = GenerativeBackend.googleAI()) }

    private val deviceModel: GenerativeModel by lazy {
        ai.generativeModel(
            modelName = CloudPreset.DEFAULT.choice.model, // required even for ONLY_ON_DEVICE; never called in the cloud
            onDeviceConfig = OnDeviceConfig(
                mode = InferenceMode.ONLY_ON_DEVICE,
                maxOutputTokens = DEVICE_MAX_OUTPUT_TOKENS,
                temperature = 0.2f,
                modelOption = DEVICE_MODEL_OPTION,
            ),
        )
    }

    private val cloudModels = ConcurrentHashMap<CloudChoice, GenerativeModel>()

    /** 3.x cloud models ignore temperature (docs FAQ); only the thinking level is set. */
    private fun cloudModel(choice: CloudChoice): GenerativeModel = cloudModels.getOrPut(choice) {
        ai.generativeModel(
            modelName = choice.model,
            generationConfig = generationConfig { thinkingConfig = thinkingConfig { thinkingLevel = choice.effort.sdk } },
            onDeviceConfig = OnDeviceConfig(mode = InferenceMode.ONLY_IN_CLOUD),
        )
    }

    override suspend fun deviceModelState(): DeviceModelState {
        if (!firebaseConfigured) return DeviceModelState.UNAVAILABLE
        return try {
            when (deviceModel.onDeviceExtension?.checkStatus()) {
                OnDeviceModelStatus.AVAILABLE -> DeviceModelState.AVAILABLE
                OnDeviceModelStatus.DOWNLOADABLE -> DeviceModelState.DOWNLOADABLE
                OnDeviceModelStatus.DOWNLOADING -> DeviceModelState.DOWNLOADING
                else -> DeviceModelState.UNAVAILABLE
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            DeviceModelState.UNAVAILABLE
        }
    }

    override fun downloadDeviceModel(): Flow<ModelDownloadStep> {
        if (!firebaseConfigured) return emptyFlow()
        val extension = deviceModel.onDeviceExtension ?: return emptyFlow()
        return extension.download().map { status ->
            when (status) {
                is DownloadStatus.DownloadStarted -> ModelDownloadStep.Started(status.bytesToDownload)
                is DownloadStatus.DownloadInProgress -> ModelDownloadStep.Progress(status.totalBytesDownloaded)
                is DownloadStatus.DownloadCompleted -> ModelDownloadStep.Done
                is DownloadStatus.DownloadFailed -> ModelDownloadStep.Failed(status.exception.message)
                else -> ModelDownloadStep.Progress(0)
            }
        }
    }

    override suspend fun read(image: Bitmap?, text: String?, engine: ReadEngine, override: CloudChoice?): ReadResult {
        if (!firebaseConfigured) return ReadResult.Failure(ReadFailure.NOT_SET_UP, "Firebase is not configured in this build")
        if (image == null && text.isNullOrBlank()) return ReadResult.Failure(ReadFailure.UNREADABLE, "Nothing to read")
        val prompt = content {
            if (image != null) image(image)
            text(ReaderPrompt.build(text))
        }
        if (engine == ReadEngine.DEVICE) return attempt(deviceModel, "device", prompt)

        val main = override ?: config.main()
        var result = attempt(cloudModel(main), main.model, prompt)
        if (result.isBusy()) {
            delay(BUSY_RETRY_MS)
            result = attempt(cloudModel(main), main.model, prompt)
        }
        if (result.isBusy()) {
            val fallback = config.fallback()
            if (fallback.model != main.model) result = attempt(cloudModel(fallback), fallback.model, prompt)
        }
        return result
    }

    private fun ReadResult.isBusy() = this is ReadResult.Failure && reason == ReadFailure.BUSY

    private suspend fun attempt(model: GenerativeModel, label: String, prompt: Content): ReadResult {
        val started = SystemClock.elapsedRealtime()
        return try {
            val response = model.generateObject(CardDraft.firebaseAISchema(), prompt)
            val card = response.getObject() ?: return ReadResult.Failure(ReadFailure.UNREADABLE, "Empty answer")
            val readBy = when (response.response.inferenceSource) {
                InferenceSource.ON_DEVICE -> ReadEngine.DEVICE
                else -> ReadEngine.CLOUD
            }
            val millis = SystemClock.elapsedRealtime() - started
            val u = response.response.usageMetadata
            Log.i(TAG, "read $label ${millis}ms tokens in=${u?.promptTokenCount} out=${u?.candidatesTokenCount} thoughts=${u?.thoughtsTokenCount}")
            ReadResult.Success(card.cleaned(), readBy, millis, label)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val reason = AiErrors.classify(e)
            Log.w(TAG, "read $label failed $reason after ${SystemClock.elapsedRealtime() - started}ms: ${e::class.simpleName}: ${e.message}")
            ReadResult.Failure(reason, e.message ?: e::class.simpleName)
        }
    }

    private fun CardDraft.cleaned() = copy(
        title = title.trim().take(120),
        kind = kind.trim().lowercase().take(40),
        tags = tags.map { it.trim().lowercase() }.filter { it.isNotEmpty() }.distinct().take(5),
        items = items.map { it.trim() }.filter { it.isNotEmpty() }.distinct().take(8),
        place = place.trim(),
        date = date.trim(),
        link = link.trim(),
        summary = summary.trim(),
    )

    private val Effort.sdk: ThinkingLevel
        get() = when (this) {
            Effort.MINIMAL -> ThinkingLevel.MINIMAL
            Effort.LOW -> ThinkingLevel.LOW
            Effort.MEDIUM -> ThinkingLevel.MEDIUM
            Effort.HIGH -> ThinkingLevel.HIGH
        }

    companion object {
        /** STABLE = nano-v3 (widest support). Switch to PREVIEW (nano-v4) once the Nano 4 phone is here (H4). */
        val DEVICE_MODEL_OPTION: OnDeviceModelOption = OnDeviceModelOption.STABLE
        private const val DEVICE_MAX_OUTPUT_TOKENS = 256
        private const val BUSY_RETRY_MS = 2_000L
        private const val TAG = "KeptReader"
    }
}
