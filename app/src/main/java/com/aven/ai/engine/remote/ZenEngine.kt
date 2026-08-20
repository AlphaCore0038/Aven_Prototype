package com.aven.ai.engine.remote

import okhttp3.OkHttpClient

/**
 * OpenCode Zen (OpenAI-compatible). Default model: DeepSeek V4 Flash Free.
 */
class ZenEngine(
    apiKey: () -> String?,
    client: OkHttpClient,
    modelId: String = DEFAULT_MODEL,
    baseUrl: String = DEFAULT_BASE_URL
) : OpenAiCompatibleEngine(
    id = "zen/$modelId",
    displayName = "DeepSeek V4 Flash (Free)",
    baseUrl = baseUrl,
    apiKey = apiKey,
    client = client
) {
    override val providerName = "OpenCode Zen"
    override val modelId = modelId

    companion object {
        const val PROVIDER_ID = "zen"
        const val DEFAULT_MODEL = "deepseek-v4-flash-free"
        const val DEFAULT_BASE_URL = "https://opencode.ai/zen/v1"
    }
}