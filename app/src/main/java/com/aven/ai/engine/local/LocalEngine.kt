package com.aven.ai.engine.local

import com.aven.ai.data.local.MessageEntity
import com.aven.ai.engine.AiChatEngine
import com.aven.ai.engine.AiChatException
import com.aven.ai.engine.GenerateRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * On-device inference via llama.cpp (Llama 3.2 1B). Generation runs on a
 * background thread and streams token pieces through the JNI callback; a
 * cancelled collector stops the native loop on its next token.
 */
class LocalEngine internal constructor(
    private val handle: Long?,
    override val modelId: String = MODEL_ID
) : AiChatEngine {

    override val id = "${PROVIDER_ID}/$modelId"
    override val displayName = when (modelId) {
        GEMMA_ID -> "Gemma 1B"
        else -> "Llama 3.2 1B"
    }

    private val loadedHandle: Long
        get() = handle ?: throw AiChatException("The local model is not loaded.")

    fun isLoaded(): Boolean = handle != null

    /**
     * One llama_context, one sampler, one thread at a time. Concurrent
     * decodes on the same native state corrupt the heap (random ggml-kernel
     * SIGSEGVs); overlapping callers queue here instead.
     */
    private val inferenceMutex = Mutex()

    override fun generate(request: GenerateRequest): Flow<String> = callbackFlow {
        val generationScope: CoroutineScope = this
        val listener = LlamaTokenListener { piece ->
            trySend(piece)
            generationScope.ensureActive()
        }
        // Structured chat for native template — single source of truth, no Kotlin manual template.
        val system = "You are Aven, a helpful, honest, and concise AI assistant. Answer directly and clearly."
        val history = truncateHistory(request.history)
        val historyRoles = history.map { if (modelId == GEMMA_ID && it.role == "assistant") "model" else it.role }.toTypedArray()
        val historyContents = history.map { it.content }.toTypedArray()
        val fullText = try {
            withContext(Dispatchers.Default) {
                inferenceMutex.withLock {
                    LlamaNative.nativeGenerateChat(
                        loadedHandle, system, historyRoles, historyContents, request.prompt, listener
                    )
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            // Catch Throwable, not Exception: native-layer failures surface
            // as UnsatisfiedLinkError/OutOfMemoryError and must become a
            // user-presentable error instead of killing the process.
            throw AiChatException("On-device generation failed: ${e.message ?: e::class.java.simpleName}")
        }
        close()
        awaitClose { LlamaNative.nativeCancel() }
    }

    /** Builds the chat template for the selected local model. */
    internal fun buildPrompt(request: GenerateRequest): String {
        val system = "You are Aven, a helpful, honest, and concise AI assistant. Answer directly and clearly."
        val history = truncateHistory(request.history)
        return if (modelId == GEMMA_ID) {
            // Gemma 3 Instruct: <bos><start_of_turn>user\n...<end_of_turn>\n<start_of_turn>model\n
            buildString {
                append(GEMMA_BOS)
                append(GEMMA_START + "user\n")
                append(system)
                append(GEMMA_END)
                history.forEach { message ->
                    val role = if (message.role == "assistant") "model" else "user"
                    append(GEMMA_START + role + "\n")
                    append(message.content)
                    append(GEMMA_END)
                }
                append(GEMMA_START + "user\n")
                append(request.prompt)
                append(GEMMA_END)
                append(GEMMA_START + "model\n")
            }
        } else {
            val conversation = buildString {
                history.forEach { message ->
                    append(header(message.role))
                    append(message.content)
                    append(EOT)
                }
            }
            buildString {
                append(BOS)
                append(header("system"))
                append(system)
                append(EOT)
                append(conversation)
                append(header("user"))
                append(request.prompt)
                append(EOT)
                append(header("assistant"))
                append("\n")
            }
        }
    }

    private fun header(role: String) = "<|start_header_id|>$role<|end_header_id|>\n\n"

    private fun truncateHistory(messages: List<MessageEntity>): List<MessageEntity> {
        val window = messages.takeLast(MAX_HISTORY_MESSAGES)
        val result = mutableListOf<MessageEntity>()
        var used = 0
        // Walk newest-first so budget keeps the most recent context.
        for (message in window.asReversed()) {
            val cost = message.content.length
            if (used + cost > MAX_HISTORY_CHARS) break
            result.add(message)
            used += cost
        }
        result.reverse()
        return result
    }

    companion object {
        const val PROVIDER_ID = "local"
        const val MODEL_ID = "llama-3.2-1b-instruct"
        const val MODEL_FILE_NAME = "Llama-3.2-1B-Instruct-Q4_K_M.gguf"
        const val GGUF_URL =
            "https://huggingface.co/bartowski/Llama-3.2-1B-Instruct-GGUF/resolve/main/Llama-3.2-1B-Instruct-Q4_K_M.gguf"

        const val GEMMA_ID = "gemma-3-1b-it"
        const val GEMMA_FILE_NAME = "gemma-3-1b-it-Q4_K_M.gguf"
        const val GEMMA_URL =
            "https://huggingface.co/ggml-org/gemma-3-1b-it-GGUF/resolve/main/gemma-3-1b-it-Q4_K_M.gguf"

        private const val BOS = "<|begin_of_text|>"
        private const val EOT = "<|eot_id|>"
        private const val GEMMA_BOS = "<bos>"
        private const val GEMMA_START = "<start_of_turn>"
        private const val GEMMA_END = "<end_of_turn>\n"
        private const val MAX_HISTORY_MESSAGES = 12
        private const val MAX_HISTORY_CHARS = 8000

        /** Loads the model from disk and returns a usable engine (0 = failure). */
        fun load(path: String, modelId: String = MODEL_ID): LocalEngine? {
            val handle = try {
                LlamaNative.nativeLoad(path)
            } catch (e: Throwable) {
                // UnsatisfiedLinkError and friends are Errors, not Exceptions;
                // letting them escape here would crash at app startup.
                return null
            }
            return if (handle == 0L) null else LocalEngine(handle, modelId)
        }

        fun unload(engine: LocalEngine?) {
            engine ?: return
            val handle = engine.handle ?: return
            try {
                LlamaNative.nativeUnload(handle)
            } catch (e: Exception) {
                // Native teardown failure is not recoverable; ignore.
            }
        }
    }
}