package com.jdevsappstudio.kept.data.ai

import android.graphics.Bitmap
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/** Which side reads. Picked per call (BUILD-SPEC §3) so the routing chip is always truthful. */
enum class ReadEngine { DEVICE, CLOUD }

/** On-device model state, mapped from the SDK so the rest of the app doesn't import it. */
enum class DeviceModelState { AVAILABLE, DOWNLOADABLE, DOWNLOADING, UNAVAILABLE }

/** How hard the cloud model thinks. 3.x models can't turn thinking off; MINIMAL is the floor where allowed. */
enum class Effort { MINIMAL, LOW, MEDIUM, HIGH }

/** Any cloud model ID + effort. Remote Config can name a model that didn't exist when the app shipped. */
data class CloudChoice(val model: String, val effort: Effort)

/**
 * Known-good choices: the code defaults and the debug chips. Model IDs and the levels each accepts are from
 * firebase.google.com/docs/ai-logic/models + /thinking (checked 25 Nov 2026). All free tier (no billing, H38).
 * Measured on the OnePlus 8 (dosa / list screenshots, 1131 input tokens).
 */
enum class CloudPreset(val choice: CloudChoice, val label: String) {
    /** Default (owner, 25 Nov): 7.0 / 3.6 s, 0 thought tokens, 5/5 items. */
    LITE(CloudChoice("gemini-3.5-flash-lite", Effort.MINIMAL), "3.5 Flash-Lite · minimal"),
    /** 3.0 / 4.9 s, also 0 thought tokens: no gain over minimal on simple screenshots. */
    LITE_LOW(CloudChoice("gemini-3.5-flash-lite", Effort.LOW), "3.5 Flash-Lite · low"),
    /** Cheapest; the busy fallback (separate capacity). 11.4 s under load, 5/5 items. */
    LITE_OLD(CloudChoice("gemini-3.1-flash-lite", Effort.MINIMAL), "3.1 Flash-Lite · minimal"),
    /** LOW is its floor (MINIMAL → 400). Busy twice on 25 Nov. */
    FLASH_LOW(CloudChoice("gemini-3.8-flash", Effort.LOW), "3.8 Flash · low"),
    /** Its default effort: 36.5 s. */
    FLASH(CloudChoice("gemini-3.8-flash", Effort.MEDIUM), "3.8 Flash · medium");

    companion object {
        val DEFAULT = LITE
        val BUSY_FALLBACK = LITE_OLD
    }
}

/** Where the read model comes from at run time (Remote Config in the app, fixed values in tests). */
interface ReadModelConfig {
    suspend fun main(): CloudChoice
    suspend fun fallback(): CloudChoice
}

/**
 * Every failure is typed (BUILD-SPEC §4); the queue decides from [retry]: never crash, never lose the raw save.
 * Sources: docs/ai-logic/error-codes (400/403/404/429) + hybrid get-started (on-device BUSY / battery quota).
 */
enum class ReadFailure(val retry: Retry) {
    /** 429 "Resource exhausted, please try again later", 503 "high demand", on-device ErrorCode.BUSY. */
    BUSY(Retry.BACKOFF),
    /** 429 "You exceeded your current quota" (QuotaExceededException): wait for the daily reset. */
    QUOTA(Retry.LATER),
    /** On-device PER_APP_BATTERY_USE_QUOTA_EXCEEDED: long-duration quota, try again later (or cloud with consent). */
    DEVICE_QUOTA(Retry.LATER),
    /** No network / request timed out. */
    OFFLINE(Retry.WHEN_ONLINE),
    /** ONLY_ON_DEVICE but Nano isn't there (FirebaseAIOnDeviceNotAvailableException). */
    NO_MODEL(Retry.NEVER),
    /** 404 model "was not found or your project does not have access": retired or wrong ID. Needs a new model ID. */
    MODEL_GONE(Retry.NEVER),
    /** 400 API key / 403 App Check, permission, blocked API / 404 genai config / no google-services.json. */
    NOT_SET_UP(Retry.NEVER),
    /** Safety block on the prompt or answer: the save goes to "Needs a look", never re-sent. */
    BLOCKED(Retry.NEVER),
    /** Answer didn't fit the card schema, or nothing to read. One more try, then "Needs a look". */
    UNREADABLE(Retry.ONCE),
    UNKNOWN(Retry.BACKOFF);

    enum class Retry { BACKOFF, LATER, WHEN_ONLINE, ONCE, NEVER }
}

sealed interface ReadResult {
    data class Success(
        val card: CardDraft,
        val readBy: ReadEngine,
        val millis: Long,
        /** Model that answered, e.g. "gemini-3.5-flash-lite" or "device". */
        val model: String,
    ) : ReadResult

    data class Failure(val reason: ReadFailure, val detail: String?) : ReadResult
}

/** One step of the on-device model download, mapped from the SDK's DownloadStatus. */
sealed interface ModelDownloadStep {
    data class Started(val bytes: Long) : ModelDownloadStep
    data class Progress(val bytesDone: Long) : ModelDownloadStep
    data object Done : ModelDownloadStep
    data class Failed(val detail: String?) : ModelDownloadStep
}

interface CardReader {
    suspend fun deviceModelState(): DeviceModelState

    /** Asks Android for the on-device model (the SDK never downloads by itself). Empty where there is none to get. */
    fun downloadDeviceModel(): Flow<ModelDownloadStep> = emptyFlow()

    /**
     * [image] already scaled to ≤ 1024 px. [text] is shared text / URL / caption, if any.
     * [override] forces a cloud model (debug chips); null = what [ReadModelConfig] says.
     */
    suspend fun read(
        image: Bitmap?,
        text: String?,
        engine: ReadEngine,
        override: CloudChoice? = null,
    ): ReadResult
}
