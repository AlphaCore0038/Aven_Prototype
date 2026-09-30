package com.aven.ai.engine.remote

import com.aven.ai.engine.AiChatEngine
import com.aven.ai.engine.AiChatException
import com.aven.ai.engine.AiError
import com.aven.ai.engine.GenerateRequest
import com.aven.ai.engine.RemoteModelConfig
import com.aven.ai.engine.validationError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
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
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/**
 * The single remote engine for user-configured OpenAI-compatible gateways.
 * Streams SSE deltas and maps HTTP/provider/network failures into honest,
 * user-presentable [AiChatException]s with an [AiError] category. Retries only
 * transient HTTP 429s with backoff; never falls back to another provider.
 */
class OpenAiCompatibleEngine(
    private val config: RemoteModelConfig,
    private val apiKey: () -> String?,
    private val client: OkHttpClient,
    private val maxRateLimitRetries: Int = 3,
    private val rateLimitBackoffMs: Long = 2000,
    private val maxDisconnectRetries: Int = 1
) : AiChatEngine {

    override val id: String = "remote/${config.id}"
    override val displayName: String = config.modelId
    override val providerId: String = config.id
    override val modelId: String = config.modelId

    private val providerName: String get() = config.providerName
    private val baseUrl: String get() = config.baseUrl

    override fun generate(request: GenerateRequest): Flow<String> = flow {
        config.validationError()?.let { throw AiChatException(it, error = AiError.INVALID_CONFIGURATION) }

        val key = apiKey()?.takeIf { it.isNotBlank() }
            ?: throw AiChatException(
                "No API key configured for $providerName. Add it in Settings → Online models.",
                error = AiError.INVALID_CONFIGURATION
            )

        val httpRequest = Request.Builder()
            .url("${baseUrl.trimEnd('/')}/chat/completions")
            .header("Authorization", "Bearer $key")
            .header("Content-Type", "application/json")
            .header("Accept", "text/event-stream")
            .post(buildBody(request).toRequestBody())
            .build()

        var attempt = 0
        var disconnectAttempt = 0
        while (true) {
            currentCoroutineContext().ensureActive()
            val call = client.newCall(httpRequest)
            val response = try {
                suspendCancellableCoroutine<okhttp3.Response> { cont ->
                    cont.invokeOnCancellation { call.cancel() }
                    try {
                        cont.resume(call.execute())
                    } catch (e: CancellationException) {
                        cont.resumeWithException(e)
                    } catch (e: IOException) {
                        cont.resumeWithException(e)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                if (disconnectAttempt < maxDisconnectRetries) {
                    disconnectAttempt++
                    delay(rateLimitBackoffMs * disconnectAttempt)
                    continue
                }
                throw networkError(e)
            }

            response.use {
                if (it.isSuccessful) {
                    var emittedAny = false
                    var sawDone = false
                    try {
                        val source = it.body?.source()
                            ?: throw AiChatException(
                                "The provider returned an empty response.",
                                error = AiError.EMPTY_RESPONSE
                            )

                        SseParser.parse(source) { event ->
                            currentCoroutineContext().ensureActive()
                            if (event == DONE_EVENT) {
                                sawDone = true
                                return@parse
                            }
                            val (delta, error) = DeltaExtractor.extractContentAndError(event)
                            if (error != null) {
                                throw AiChatException(error, error = AiError.PROVIDER_ERROR)
                            }
                            if (!delta.isNullOrEmpty()) {
                                currentCoroutineContext().ensureActive()
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
                                "Connection lost while generating. Check your internet connection and try again.",
                                error = AiError.STREAM_INTERRUPTED
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
                            "Connection lost while generating. Check your internet connection and try again.",
                            cause = e,
                            error = AiError.STREAM_INTERRUPTED
                        )
                    }
                }

                if (it.code == 429 && attempt < maxRateLimitRetries) {
                    attempt++
                    delay(rateLimitBackoffMs * attempt)
                    return@use
                }

                val body = it.body?.string()
                val message = extractErrorMessage(body)
                throw AiChatException(
                    message ?: mapHttpError(it.code),
                    error = classifyHttp(it.code, body)
                )
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

    /** Category for a non-2xx response, refined by known context-limit wording. */
    private fun classifyHttp(code: Int, body: String?): AiError = when {
        code == 400 && looksLikeContextLimit(body) -> AiError.CONTEXT_LIMIT
        code == 400 -> AiError.BAD_REQUEST
        code == 401 -> AiError.AUTHENTICATION_FAILED
        code == 403 -> AiError.ACCESS_DENIED
        code == 404 -> AiError.MODEL_NOT_FOUND
        code == 429 -> AiError.RATE_LIMITED
        code in 500..599 -> AiError.PROVIDER_ERROR
        else -> AiError.UNKNOWN
    }

    private fun looksLikeContextLimit(body: String?): Boolean {
        val text = body?.lowercase() ?: return false
        return listOf("context length", "context_length", "too long", "maximum context", "token limit", "max_tokens")
            .any { it in text }
    }

    private fun mapHttpError(code: Int): String = when (code) {
        400 -> "The provider rejected the request. Check the model settings."
        401 -> "Authentication failed. The API key was rejected by the provider."
        403 -> "Access denied. This API key doesn't have permission for this model."
        404 -> "Model not found. The provider couldn't find this model. Check the Model ID."
        429 -> "Rate limit reached. The provider is temporarily limiting requests. Wait a moment and try again."
        in 500..599 -> "Provider unavailable. The model gateway returned a server error. Try again shortly."
        else -> "The request failed ($code). Try again."
    }

    private fun networkError(e: IOException): AiChatException = when (e) {
        is UnknownHostException -> AiChatException(
            "Connection failed. Aven couldn't reach the model gateway. Check the gateway URL and your internet connection.",
            cause = e, error = AiError.NETWORK_ERROR
        )
        is ConnectException -> AiChatException(
            "Connection failed. Aven couldn't reach the model gateway.",
            cause = e, error = AiError.NETWORK_ERROR
        )
        is SocketTimeoutException -> AiChatException(
            "The request timed out. The model gateway took too long to respond.",
            cause = e, error = AiError.TIMEOUT
        )
        is SSLException -> AiChatException(
            "Secure connection failed. Check the gateway URL and certificate.",
            cause = e, error = AiError.SSL_ERROR
        )
        else -> AiChatException(
            "Connection failed. Aven couldn't reach the model gateway. Check the gateway URL and your internet connection.",
            cause = e, error = AiError.NETWORK_ERROR
        )
    }

    private companion object {
        const val DONE_EVENT = "[DONE]"
        const val SYSTEM_PROMPT =
            "You are Aven, a helpful, honest, and concise AI assistant. Answer directly and clearly."
        const val MAX_HISTORY_MESSAGES = 12
        const val MAX_HISTORY_CHARS = 8000
    }
}