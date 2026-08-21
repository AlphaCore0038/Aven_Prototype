package com.aven.ai.engine.remote

import okhttp3.OkHttpClient

/**
 * OpenCode Zen (OpenAI-compatible). Model and display name are supplied by
 * the [com.aven.ai.engine.ModelRegistry] catalogue.
 */
class ZenEngine(
    apiKey: () -> String?,
    client: OkHttpClient,
    modelId: String = DEFAULT_MODEL,
    baseUrl: String = DEFAULT_BASE_URL,
    engineDisplayName: String = DEFAULT_DISPLAY_NAME
) : OpenAiCompatibleEngine(
    id = "zen/$modelId",
    displayName = engineDisplayName,
    baseUrl = baseUrl,
    apiKey = apiKey,
    client = client
) {
    override val providerName = "OpenCode Zen"
    override val modelId = modelId

    companion object {
        const val PROVIDER_ID = "zen"
        const val DEFAULT_MODEL = "nemotron-3-ultra-free"
        const val DEFAULT_DISPLAY_NAME = "Nemotron 3 Ultra"
        const val DEFAULT_BASE_URL = "https://opencode.ai/zen/v1"
    }
}