package com.aven.ai.engine.remote

import okhttp3.OkHttpClient

/**
 * OpenRouter (OpenAI-compatible gateway). Default model: DeepSeek V4 Flash.
 */
class OpenRouterEngine(
    apiKey: () -> String?,
    client: OkHttpClient,
    modelId: String = DEFAULT_MODEL,
    baseUrl: String = DEFAULT_BASE_URL
) : OpenAiCompatibleEngine(
    id = "openrouter/$modelId",
    displayName = "DeepSeek V4 Flash",
    baseUrl = baseUrl,
    apiKey = apiKey,
    client = client
) {
    override val providerName = "OpenRouter"
    override val modelId = modelId

    companion object {
        const val PROVIDER_ID = "openrouter"
        const val DEFAULT_MODEL = "deepseek/deepseek-v4-flash"
        const val DEFAULT_BASE_URL = "https://openrouter.ai/api/v1"
    }
}