package com.aven.ai.engine.remote

import okhttp3.OkHttpClient

/**
 * Z.AI (Zhipu AI, OpenAI-compatible). Default model: GLM-4.7 Flash.
 */
class ZaiEngine(
    apiKey: () -> String?,
    client: OkHttpClient,
    modelId: String = DEFAULT_MODEL,
    baseUrl: String = DEFAULT_BASE_URL
) : OpenAiCompatibleEngine(
    id = "zai/$modelId",
    displayName = "GLM-4.7 Flash",
    baseUrl = baseUrl,
    apiKey = apiKey,
    client = client
) {
    override val providerName = "Z.AI"
    override val modelId = modelId

    companion object {
        const val PROVIDER_ID = "zai"
        const val DEFAULT_MODEL = "glm-4.7-flash"
        const val DEFAULT_BASE_URL = "https://api.z.ai/api/paas/v4"
    }
}