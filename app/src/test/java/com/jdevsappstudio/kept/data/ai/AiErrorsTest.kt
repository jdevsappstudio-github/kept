package com.jdevsappstudio.kept.data.ai

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import java.io.IOException
import org.junit.jupiter.api.Test

/** Message texts are quoted from docs/ai-logic/error-codes and from real failures seen in the spike. */
class AiErrorsTest {

    @Test fun `429 resource exhausted is busy`() =
        assertThat(AiErrors.classifyMessage("Resource exhausted, please try again later.")).isEqualTo(ReadFailure.BUSY)

    @Test fun `503 high demand is busy`() = assertThat(
        AiErrors.classifyMessage("This model is currently experiencing high demand. Spikes in demand are usually temporary. Please try again later."),
    ).isEqualTo(ReadFailure.BUSY)

    @Test fun `429 quota is quota`() = assertThat(
        AiErrors.classifyMessage("You exceeded your current quota, please check your plan and billing details"),
    ).isEqualTo(ReadFailure.QUOTA)

    /** The real free-tier text from the OnePlus 8 (25 Nov): a per-minute limit is busy (back off), not 'used up today'. */
    @Test fun `429 per-minute limit is busy`() = assertThat(
        AiErrors.classifyMessage(
            "You exceeded your current quota, please check your plan and billing details.\n" +
                "* Quota exceeded for metric: generativelanguage.googleapis.com/generate_content_free_tier_requests, limit: 15, " +
                "model: gemini-3.5-flash-lite, description: Generate content requests per minute limit exceeded on Free Tier.\n" +
                "Please retry in 25.966255884s.",
        ),
    ).isEqualTo(ReadFailure.BUSY)

    @Test fun `429 per-day limit is quota`() = assertThat(
        AiErrors.classifyMessage(
            "You exceeded your current quota.\n* Quota exceeded for metric: generate_content_free_tier_requests, " +
                "description: Generate content requests per day limit exceeded on Free Tier.\nPlease retry in 3h2m.",
        ),
    ).isEqualTo(ReadFailure.QUOTA)

    @Test fun `404 retired model is model gone`() = assertThat(
        AiErrors.classifyMessage("models/gemini-2.0-flash was not found or your project does not have access to it"),
    ).isEqualTo(ReadFailure.MODEL_GONE)

    @Test fun `403 app check is not set up`() = assertThat(
        AiErrors.classifyMessage("PERMISSION_DENIED: To access this model, you must enforce Firebase App Check"),
    ).isEqualTo(ReadFailure.NOT_SET_UP)

    @Test fun `400 bad key is not set up`() =
        assertThat(AiErrors.classifyMessage("API key not valid. Please pass a valid API key.")).isEqualTo(ReadFailure.NOT_SET_UP)

    @Test fun `wrong thinking level is not set up`() = assertThat(
        AiErrors.classifyMessage("Thinking level MINIMAL is not supported for this model. Please retry with other thinking level."),
    ).isEqualTo(ReadFailure.NOT_SET_UP)

    @Test fun `on-device battery quota`() =
        assertThat(AiErrors.classifyMessage("PER_APP_BATTERY_USE_QUOTA_EXCEEDED")).isEqualTo(ReadFailure.DEVICE_QUOTA)

    @Test fun `unknown text is null`() = assertThat(AiErrors.classifyMessage("something odd")).isNull()

    @Test fun `io exception is offline`() = assertThat(AiErrors.classify(IOException("timeout"))).isEqualTo(ReadFailure.OFFLINE)

    @Test fun `wrapped cause is unwrapped`() =
        assertThat(AiErrors.classify(RuntimeException("wrapper", IOException("x")))).isEqualTo(ReadFailure.OFFLINE)
}
