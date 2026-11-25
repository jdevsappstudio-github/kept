package com.jdevsappstudio.kept.data.ai

import com.jdevsappstudio.kept.data.config.RemoteValues

/**
 * Read model from Remote Config (owner, 25 Nov; the Firebase docs recommend it for swapping models without a release).
 * Keys: read_model, read_effort, read_fallback_model, read_fallback_effort. Anything not published falls back to the
 * [CloudPreset] code defaults. The fetch itself is shared ([RemoteValues]).
 */
class RemoteReadModelConfig(private val remote: RemoteValues) : ReadModelConfig {

    override suspend fun main() = choice(KEY_MODEL, KEY_EFFORT, CloudPreset.DEFAULT.choice)

    override suspend fun fallback() = choice(KEY_FALLBACK_MODEL, KEY_FALLBACK_EFFORT, CloudPreset.BUSY_FALLBACK.choice)

    private suspend fun choice(modelKey: String, effortKey: String, default: CloudChoice): CloudChoice {
        val model = remote.string(modelKey) ?: default.model
        val effortName = remote.string(effortKey)
        val effort = Effort.entries.firstOrNull { it.name.equals(effortName, ignoreCase = true) } ?: default.effort
        return CloudChoice(model, effort)
    }

    companion object {
        const val KEY_MODEL = "read_model"
        const val KEY_EFFORT = "read_effort"
        const val KEY_FALLBACK_MODEL = "read_fallback_model"
        const val KEY_FALLBACK_EFFORT = "read_fallback_effort"
    }
}
