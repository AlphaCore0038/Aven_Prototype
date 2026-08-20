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

/** Raised when generation fails for a concrete, user-presentable reason. */
class AiChatException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * The generation capability behind a chat. Implementations stream text deltas
 * and must cancel promptly when their collecting coroutine is cancelled.
 *
 * Phase 3 ships [UnavailableEngine]; local (llama.cpp) and remote (Zen /
 * OpenRouter) engines replace it in Phase 4/5.
 */
interface AiChatEngine {
    val id: String
    val displayName: String
    fun generate(request: GenerateRequest): Flow<String>
}