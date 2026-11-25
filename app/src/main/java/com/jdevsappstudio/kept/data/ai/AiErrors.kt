@file:OptIn(PublicPreviewAPI::class)

package com.jdevsappstudio.kept.data.ai

import com.google.firebase.ai.type.PublicPreviewAPI
import com.google.firebase.ai.type.APINotConfiguredException
import com.google.firebase.ai.type.ContentBlockedException
import com.google.firebase.ai.type.FirebaseAIOnDeviceInvalidRequestException
import com.google.firebase.ai.type.FirebaseAIOnDeviceNotAvailableException
import com.google.firebase.ai.type.InvalidAPIKeyException
import com.google.firebase.ai.type.PermissionMissingException
import com.google.firebase.ai.type.PromptBlockedException
import com.google.firebase.ai.type.QuotaExceededException
import com.google.firebase.ai.type.RequestTimeoutException
import com.google.firebase.ai.type.ResponseStoppedException
import com.google.firebase.ai.type.SerializationException
import com.google.firebase.ai.type.ServiceDisabledException
import com.google.firebase.ai.type.UnsupportedUserLocationException
import java.io.IOException

/** Maps any exception from Firebase AI Logic to a [ReadFailure]. Typed classes first, then documented messages. */
internal object AiErrors {

    fun classify(e: Throwable): ReadFailure = when (e) {
        is QuotaExceededException -> classifyMessage(e.message) ?: ReadFailure.QUOTA
        is FirebaseAIOnDeviceNotAvailableException -> ReadFailure.NO_MODEL
        is RequestTimeoutException, is IOException -> ReadFailure.OFFLINE
        is PromptBlockedException, is ContentBlockedException -> ReadFailure.BLOCKED
        is SerializationException, is ResponseStoppedException, is FirebaseAIOnDeviceInvalidRequestException ->
            ReadFailure.UNREADABLE
        is APINotConfiguredException, is ServiceDisabledException, is InvalidAPIKeyException,
        is PermissionMissingException, is UnsupportedUserLocationException -> ReadFailure.NOT_SET_UP
        // ServerException, FirebaseAIOnDeviceUnknownException, UnknownException… carry the reason in the message.
        else -> classifyMessage(e.message) ?: (e.cause?.let { if (it !== e) classify(it) else null } ?: ReadFailure.UNKNOWN)
    }

    /** Message texts from docs/ai-logic/error-codes and hybrid get-started (plus 503 "high demand", seen 25 Nov). */
    fun classifyMessage(message: String?): ReadFailure? {
        val m = message?.lowercase() ?: return null
        return when {
            "per_app_battery_use_quota" in m -> ReadFailure.DEVICE_QUOTA
            // Free tier is 15 requests/minute (seen 25 Nov, OnePlus 8): same 429 text, but it clears in seconds.
            // Only a per-day limit means 'wait for tomorrow'.
            "per day" in m || "perday" in m -> ReadFailure.QUOTA
            "per minute" in m || "perminute" in m || "please retry in" in m -> ReadFailure.BUSY
            "exceeded your current quota" in m -> ReadFailure.QUOTA
            "resource exhausted" in m || "high demand" in m || "overloaded" in m ||
                "try again later" in m || "busy" in m || "unavailable" in m -> ReadFailure.BUSY
            "was not found or your project does not have access" in m -> ReadFailure.MODEL_GONE
            "api key not valid" in m || "app check" in m || "permission_denied" in m ||
                "genai config not found" in m || "has been deactivated" in m || "are blocked" in m -> ReadFailure.NOT_SET_UP
            "is not supported for this model" in m -> ReadFailure.NOT_SET_UP // wrong effort for the model: a code bug
            else -> null
        }
    }
}
