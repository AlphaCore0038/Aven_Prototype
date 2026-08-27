package com.aven.ai.engine.remote

import com.aven.ai.engine.AiChatEngine
import com.aven.ai.engine.AiChatException
import com.aven.ai.engine.GenerateRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

/**
 * Base engine for OpenAI-compatible chat-completions APIs (OpenCode Zen,
 * OpenRouter, Z.AI). Streams SSE deltas; maps HTTP, provider, and network
 * errors into honest [AiChatException]s. Retries only transient HTTP 429s
 * with backoff; never falls back to another provider silently.
 */
abstract class OpenAiCompatibleEngine(
    override val id: String,
    override val displayName: String,
    private val baseUrl: String,
    private val apiKey: () -> String?,
    private val client: OkHttpClient,
    private val maxRateLimitRetries: Int = 3,
    private val rateLimitBackoffMs: Long = 2000,
    private val maxDisconnectRetries: Int = 1
) : AiChatEngine {

    protected abstract val providerName: String
    protected abstract val modelId: String

    override fun generate(request: GenerateRequest): Flow<String> = flow {
        val key = apiKey()
            ?: throw AiChatException(
                "No $providerName API key. Add it in Settings → API Keys."
            )

        val httpRequest = Request.Builder()
            .url("$baseUrl/chat/completions")
            .header("Authorization", "Bearer $key")
            .header("Content-Type", "application/json")
            .header("Accept", "text/event-stream")
            .post(buildBody(request).toRequestBody())
            .build()

        var attempt = 0
        var disconnectAttempt = 0
        while (true) {
            val response = try {
                client.newCall(httpRequest).execute()
            } catch (e: IOException) {
                if (disconnectAttempt < maxDisconnectRetries) {
                    disconnectAttempt++
                    delay(rateLimitBackoffMs * disconnectAttempt)
                    continue
                }
                throw AiChatException(
                    "Couldn't reach $providerName. Check your internet connection and try again."
                )
            }

            response.use {
                if (it.isSuccessful) {
                    var emittedAny = false
                    var sawDone = false
                    try {
                        val source = it.body?.source()
                            ?: throw AiChatException("Empty response from $providerName.")

                        SseParser.parse(source) { event ->
                            if (event == DONE_EVENT) {
                                sawDone = true
                                return@parse
                            }
                            val error = DeltaExtractor.extractError(event)
                            if (error != null) throw AiChatException(error)
                            val delta = DeltaExtractor.extractContent(event)
                            if (!delta.isNullOrEmpty()) {
                                emittedAny = true
                                emit(delta)
                            }
                        }

                        if (!sawDone && !emittedAny) {
                            if (disconnectAttempt < maxDisconnectRetries) {
                                disconnectAttempt++
                                delay(rateLimitBackoffMs * disconnectAttempt)
                                return@use
                            }
                            throw AiChatException(
                                "Couldn't reach $providerName — the connection was interrupted. " +
                                    "Check your internet connection and try again."
                            )
                        }
                        return@flow
                    } catch (e: IOException) {
                        if (!emittedAny && disconnectAttempt < maxDisconnectRetries) {
                            disconnectAttempt++
                            delay(rateLimitBackoffMs * disconnectAttempt)
                            return@use
                        }
                        throw AiChatException(
                            "Couldn't reach $providerName — the connection was interrupted. " +
                                "Check your internet connection and try again."
                        )
                    }
                }

                if (it.code == 429 && attempt < maxRateLimitRetries) {
                    attempt++
                    delay(rateLimitBackoffMs * attempt)
                    return@use
                }

                val message = extractErrorMessage(it.body?.string())
                throw AiChatException(message ?: mapHttpError(it.code))
            }
        }
    }.flowOn(Dispatchers.IO)

    protected open fun buildBody(request: GenerateRequest): String {
        val system = buildJsonObject {
            put("role", "system")
            put("content", SYSTEM_PROMPT)
        }
        val history = truncateHistory(request.history).map { message ->
            buildJsonObject {
                put("role", message.role)
                put("content", message.content)
            }
        }
        val user = buildJsonObject {
            put("role", "user")
            put("content", request.prompt)
        }

        return buildJsonObject {
            put("model", modelId)
            put("stream", true)
            put("stream_options", buildJsonObject { put("include_usage", true) })
            put(
                "messages",
                buildJsonArray {
                    add(system)
                    history.forEach { add(it) }
                    add(user)
                }
            )
        }.toString()
    }

    private fun truncateHistory(messages: List<com.aven.ai.data.local.MessageEntity>): List<com.aven.ai.data.local.MessageEntity> {
        val window = messages.takeLast(MAX_HISTORY_MESSAGES)
        val budget = MAX_HISTORY_CHARS
        val result = mutableListOf<com.aven.ai.data.local.MessageEntity>()
        var used = 0
        // Keep newest messages first within the char budget.
        for (message in window.asReversed()) {
            val cost = message.content.length
            if (used + cost > budget) break
            result.add(message)
            used += cost
        }
        result.reverse()
        return result
    }

    private fun extractErrorMessage(body: String?): String? = runCatching {
        val root = Json { ignoreUnknownKeys = true }.parseToJsonElement(body!!).jsonObject
        val error = root["error"]?.jsonObject
        val message = error?.get("message")?.jsonPrimitive?.contentOrNull
        val raw = error?.get("metadata")?.jsonObject?.get("raw")?.jsonPrimitive?.contentOrNull
            ?: error?.get("metadata")?.jsonObject?.get("message")?.jsonPrimitive?.contentOrNull
        val specific = if (message == "Provider returned error") raw ?: message else message
        specific?.takeIf { it.isNotBlank() }
    }.getOrNull()

    private fun mapHttpError(code: Int): String = when (code) {
        401, 403 -> "Authentication failed for $providerName. Check the API key in Settings → API Keys."
        404 -> "$providerName could not find the model. Try a different model."
        429 -> "$providerName is rate-limited. Wait a moment and try again."
        in 500..599 -> "$providerName is having trouble. Try again shortly."
        else -> "$providerName request failed ($code)."
    }

    private companion object {
        const val DONE_EVENT = "[DONE]"
        const val SYSTEM_PROMPT =
            "You are Aven, a helpful, honest, and concise AI assistant. Answer directly and clearly."
        const val MAX_HISTORY_MESSAGES = 12
        const val MAX_HISTORY_CHARS = 8000
    }
}