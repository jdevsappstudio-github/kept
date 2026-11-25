package com.jdevsappstudio.kept.data.ai

import com.jdevsappstudio.kept.data.config.RemoteValues

/**
 * A cloud model from Remote Config (owner, 25 Nov; the Firebase docs recommend it for swapping models without a release).
 * Keys: `<prefix>_model`, `<prefix>_effort`, `<prefix>_fallback_model`, `<prefix>_fallback_effort`. Anything not
 * published falls back to the code defaults. The fetch itself is shared ([RemoteValues]).
 * Reads use prefix `read` (H47); Ask uses `ask` with 3.5 Flash-Lite low (H58); Fill in uses `fill`, same as Ask (H59).
 */
class RemoteReadModelConfig(
    private val remote: RemoteValues,
    private val prefix: String = PREFIX_READ,
    private val defaultMain: CloudChoice = CloudPreset.DEFAULT.choice,
    private val defaultFallback: CloudChoice = CloudPreset.BUSY_FALLBACK.choice,
) : ReadModelConfig {

    override suspend fun main() = choice("${prefix}_model", "${prefix}_effort", defaultMain)

    override suspend fun fallback() = choice("${prefix}_fallback_model", "${prefix}_fallback_effort", defaultFallback)

    private suspend fun choice(modelKey: String, effortKey: String, default: CloudChoice): CloudChoice {
        val model = remote.string(modelKey) ?: default.model
        val effortName = remote.string(effortKey)
        val effort = Effort.entries.firstOrNull { it.name.equals(effortName, ignoreCase = true) } ?: default.effort
        return CloudChoice(model, effort)
    }

    companion object {
        const val PREFIX_READ = "read"
        const val PREFIX_ASK = "ask"
        const val PREFIX_FILL = "fill"
        const val KEY_MODEL = "read_model"
        const val KEY_EFFORT = "read_effort"
        const val KEY_FALLBACK_MODEL = "read_fallback_model"
        const val KEY_FALLBACK_EFFORT = "read_fallback_effort"

        fun forAsk(remote: RemoteValues) = RemoteReadModelConfig(remote, PREFIX_ASK, CloudPreset.LITE_LOW.choice)
        fun forFill(remote: RemoteValues) = RemoteReadModelConfig(remote, PREFIX_FILL, CloudPreset.LITE_LOW.choice)
    }
}
