package com.aven.ai.engine

import com.aven.ai.data.local.MessageEntity
import kotlinx.coroutines.flow.Flow

/**
 * A request to generate an assistant reply. History is the persisted
 * conversation context in chronological order (Phase 4 truncates it to the
 * model's context window).
 */
data class GenerateRequest(
    val conversationId: Long,
    val prompt: String,
    val history: List<MessageEntity>,
    val isPrivate: Boolean
)

/**
 * A user-presentable category for a generation failure. Kept deliberately
 * small: it exists so the UI can explain what happened without exposing
 * raw HTTP codes or credentials.
 */
enum class AiError {
    INVALID_CONFIGURATION,
    AUTHENTICATION_FAILED,
    ACCESS_DENIED,
    MODEL_NOT_FOUND,
    BAD_REQUEST,
    RATE_LIMITED,
    PROVIDER_ERROR,
    NETWORK_ERROR,
    TIMEOUT,
    SSL_ERROR,
    STREAM_INTERRUPTED,
    EMPTY_RESPONSE,
    INVALID_RESPONSE,
    CONTEXT_LIMIT,
    UNKNOWN
}

/** Raised when generation fails for a concrete, user-presentable reason. */
class AiChatException(
    message: String,
    cause: Throwable? = null,
    val error: AiError = AiError.UNKNOWN
) : Exception(message, cause)

/**
 * The generation capability behind a chat. Implementations stream text deltas
 * and must cancel promptly when their collecting coroutine is cancelled.
 *
 * [providerId] and [modelId] are explicit so a model's identity never depends
 * on string parsing of [id].
 */
interface AiChatEngine {
    val id: String
    val displayName: String

    /** Stable provider/selection identity. Defaults to the `id` prefix. */
    val providerId: String get() = id.substringBefore("/")

    /** The concrete model identifier. Defaults to the `id` suffix. */
    val modelId: String get() = id.substringAfter("/")

    fun generate(request: GenerateRequest): Flow<String>
}