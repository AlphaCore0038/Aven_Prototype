package com.aven.ai.ui.chat

import com.aven.ai.data.local.ChatDao
import com.aven.ai.data.local.ConversationEntity
import com.aven.ai.data.local.MessageEntity
import com.aven.ai.engine.AiChatEngine
import com.aven.ai.engine.AiChatException
import com.aven.ai.engine.GenerateRequest
import android.util.Log
import com.aven.ai.engine.local.LocalEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

enum class MessageRole { USER, ASSISTANT }

/**
 * Cleans a raw model-generated title into a short single-line label:
 * strips wrapping quotes and "Title:" prefixes, collapses whitespace,
 * removes a trailing period, and caps the length.
 */
internal fun sanitizeTitle(raw: String): String {
    var t = raw.trim()
    t = t.replace(Regex("^title\\s*[:\\-–—]\\s*", RegexOption.IGNORE_CASE), "")
    if (t.length >= 2) {
        val first = t.first()
        val last = t.last()
        if (first == last && (first == '"' || first == '\'' || first == '`')) {
            t = t.substring(1, t.length - 1).trim()
        }
    }
    t = t.lineSequence().firstOrNull { it.isNotBlank() }?.trim() ?: ""
    t = t.replace(Regex("\\s+"), " ")
    if (t.endsWith(".")) t = t.dropLast(1).trimEnd()
    if (t.length > MAX_GENERATED_TITLE_LENGTH) {
        t = t.take(MAX_GENERATED_TITLE_LENGTH).trimEnd()
    }
    return t
}

private const val MAX_GENERATED_TITLE_LENGTH = 48

data class UiMessage(
    val id: Long,
    val conversationId: Long,
    val role: MessageRole,
    val content: String,
    val isError: Boolean = false,
    val createdAt: Long,
    val tokensPerSecond: Float? = null,
    val timeToFirstTokenMs: Long? = null,
    val promptTokens: Int? = null,
    val completionTokens: Int? = null
)

data class ChatUiState(
    val messages: List<UiMessage> = emptyList(),
    val isGenerating: Boolean = false,
    val streamingText: String = "",
    val lastError: String? = null,
    val isPrivate: Boolean = false,
    val isLoading: Boolean = true
) {
    val hasMessages: Boolean get() = messages.isNotEmpty()
}

/**
 * The conversation engine. Room is the single source of truth for messages;
 * the streaming text lives only while a generation is in flight and is
 * persisted on completion (or as a partial reply when stopped).
 */
class ChatController(
    private val chatDao: ChatDao,
    private val engineProvider: suspend () -> AiChatEngine,
    private val scope: CoroutineScope
) {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var conversationId: Long = 0L
    private var generationJob: Job? = null
    private var observerJob: Job? = null
    private val titledConversations = mutableSetOf<Long>()

    fun start(conversationId: Long, isPrivate: Boolean) {
        if (observerJob?.isActive == true &&
            this.conversationId == conversationId &&
            _uiState.value.isPrivate == isPrivate
        ) return

        this.conversationId = conversationId
        _uiState.value = ChatUiState(isPrivate = isPrivate, isLoading = true)

        generationJob?.cancel()
        observerJob?.cancel()
        observerJob = scope.launch {
            chatDao.observeMessages(conversationId).collect { persisted ->
                _uiState.update { state ->
                    state.copy(
                        messages = persisted.map { it.toUiMessage() },
                        isLoading = false
                    )
                }
            }
        }
    }

    fun send(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || _uiState.value.isGenerating) return

        val prompt = trimmed
        val now = System.currentTimeMillis()

        scope.launch {
            try {
                val engine = engineProvider()
                val providerId = engine.id.substringBefore("/")
                val modelId = engine.id.substringAfter("/")

                // The saved conversation may have been wiped (e.g. "Clear Chat
                // history" while its screen is open); fall through to creating
                // a fresh one instead of writing into a dead foreign key.
                val existingConversation =
                    if (conversationId != 0L) chatDao.getConversation(conversationId) else null
                val isNewConversation = conversationId == 0L || existingConversation == null

                val targetConversationId = if (isNewConversation) {
                    chatDao.createConversationWithFirstMessage(
                        conversation = ConversationEntity(
                            title = prompt.take(MAX_TITLE_LENGTH),
                            isPrivate = _uiState.value.isPrivate,
                            providerId = providerId,
                            modelId = modelId,
                            createdAt = now,
                            lastMessageAt = now
                        ),
                        firstMessage = promptMessage(0L, prompt, now)
                    )
                } else {
                    chatDao.upsertMessage(promptMessage(conversationId, prompt, now))
                    chatDao.updateConversation(
                        id = conversationId,
                        title = existingConversation.title,
                        isPinned = existingConversation.isPinned,
                        providerId = existingConversation.providerId,
                        modelId = existingConversation.modelId,
                        lastMessageAt = now
                    )
                    conversationId
                }

                if (isNewConversation && targetConversationId != 0L) {
                    conversationId = targetConversationId
                    observe(targetConversationId)
                }

                val history = chatDao.getMessages(targetConversationId)
                _uiState.update {
                    it.copy(isGenerating = true, streamingText = "", lastError = null)
                }

                generationJob = scope.launch {
                    var ttftMs: Long? = null
                    val t0 = System.nanoTime()
                    var firstDelta = true
                    var uiUpdateCount = 0
                    val pendingBuffer = StringBuilder()
                    var lastFlushNs = t0
                    Log.d("AvenStream", "Request start provider=$providerId model=$modelId")
                    try {
                        engine.generate(
                            GenerateRequest(
                                conversationId = targetConversationId,
                                prompt = prompt,
                                history = history,
                                isPrivate = _uiState.value.isPrivate
                            )
                        ).collect { delta ->
                            if (firstDelta) {
                                ttftMs = (System.nanoTime() - t0) / 1_000_000
                                firstDelta = false
                                Log.d("AvenStream", "TTFT ${ttftMs}ms")
                            }
                            pendingBuffer.append(delta)
                            val now = System.nanoTime()
                            val elapsedSinceFlush = now - lastFlushNs
                            if (elapsedSinceFlush >= 40_000_000L || pendingBuffer.length > 120) {
                                val chunk = pendingBuffer.toString()
                                pendingBuffer.clear()
                                _uiState.update { it.copy(streamingText = it.streamingText + chunk) }
                                lastFlushNs = now
                                uiUpdateCount++
                            }
                        }
                        if (pendingBuffer.isNotEmpty()) {
                            _uiState.update { it.copy(streamingText = it.streamingText + pendingBuffer.toString()) }
                            uiUpdateCount++
                        }

                        val finalText = _uiState.value.streamingText
                        val totalMs = (System.nanoTime() - t0) / 1_000_000
                        Log.d("AvenStream", "Generation complete ${totalMs}ms TTFT ${ttftMs}ms updates $uiUpdateCount")
                        if (finalText.isNotBlank()) {
                            // Fallback token estimate: ~4 chars per token if native/usage not available.
                            val estTokens = (finalText.length / 4).coerceAtLeast(1)
                            val tps = if (totalMs > 0) (estTokens * 1000f / totalMs) else null
                            val msPerTok = if (tps != null && tps > 0) (1000f / tps) else null
                            // Store tps/msTok will be formatted as single line; keep tps for footer.
                            chatDao.upsertMessage(
                                assistantMessage(
                                    targetConversationId, finalText, System.currentTimeMillis(),
                                    tokensPerSecond = tps,
                                    timeToFirstTokenMs = ttftMs,
                                    promptTokens = null,
                                    completionTokens = estTokens
                                )
                            )
                        }
                        // The local engine is serialized behind one mutex;
                        // a queued title job would stall the user's next
                        // message, so offline chats keep their raw titles.
                        if (isNewConversation && finalText.isNotBlank() &&
                            providerId != LocalEngine.PROVIDER_ID
                        ) {
                            generateAndStoreTitle(targetConversationId, prompt, finalText)
                        }
                    } catch (e: CancellationException) {
                        if (pendingBuffer.isNotEmpty()) {
                            _uiState.update { it.copy(streamingText = it.streamingText + pendingBuffer.toString()) }
                            pendingBuffer.clear()
                        }
                        val partial = _uiState.value.streamingText
                        if (partial.isNotBlank()) {
                            chatDao.upsertMessage(
                                assistantMessage(targetConversationId, partial, System.currentTimeMillis())
                            )
                        }
                        throw e
                    } catch (e: AiChatException) {
                        _uiState.update { it.copy(lastError = e.message ?: "Something went wrong") }
                    } catch (e: Exception) {
                        _uiState.update { it.copy(lastError = "Something went wrong") }
                    } finally {
                        _uiState.update { it.copy(isGenerating = false, streamingText = "") }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(lastError = "Something went wrong") }
            }
        }
    }

    fun stop() {
        generationJob?.cancel()
    }

    /**
     * Asks the active engine for a short conversation title after the first
     * exchange and renames the Room row. Fire-and-forget: any failure keeps
     * the raw-text fallback title silently. Runs at most once per
     * conversation per controller lifetime.
     */
    private fun generateAndStoreTitle(conversationId: Long, userPrompt: String, assistantReply: String) {
        titledConversations.add(conversationId)
        scope.launch {
            try {
                val engine = engineProvider()
                val instruction = buildString {
                    append("Generate a short title (maximum 5 words, no quotes) ")
                    append("summarizing a conversation that starts with:\n")
                    append("User: ${userPrompt.take(TITLE_CONTEXT_CHARS)}\n")
                    append("Assistant: ${assistantReply.take(TITLE_CONTEXT_CHARS)}\n")
                    append("Reply with only the title.")
                }
                val raw = withTimeoutOrNull(TITLE_TIMEOUT_MS) {
                    val builder = StringBuilder()
                    engine.generate(
                        GenerateRequest(
                            conversationId = conversationId,
                            prompt = instruction,
                            history = emptyList(),
                            isPrivate = false
                        )
                    ).collect { builder.append(it) }
                    builder.toString()
                } ?: return@launch

                val title = sanitizeTitle(raw)
                if (title.isNotBlank()) {
                    chatDao.renameConversation(conversationId, title)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Title generation is cosmetic; the fallback title stays.
            }
        }
    }

    private fun observe(id: Long) {
        observerJob?.cancel()
        observerJob = scope.launch {
            chatDao.observeMessages(id).collect { persisted ->
                _uiState.update { state ->
                    state.copy(messages = persisted.map { it.toUiMessage() })
                }
            }
        }
    }

    private fun promptMessage(conversationId: Long, text: String, at: Long) = MessageEntity(
        conversationId = conversationId,
        role = "user",
        content = text,
        createdAt = at
    )

    private fun assistantMessage(
        conversationId: Long, text: String, at: Long,
        tokensPerSecond: Float? = null,
        timeToFirstTokenMs: Long? = null,
        promptTokens: Int? = null,
        completionTokens: Int? = null
    ) = MessageEntity(
        conversationId = conversationId,
        role = "assistant",
        content = text,
        createdAt = at,
        tokensPerSecond = tokensPerSecond,
        timeToFirstTokenMs = timeToFirstTokenMs,
        promptTokens = promptTokens,
        completionTokens = completionTokens
    )

    private fun MessageEntity.toUiMessage() = UiMessage(
        id = id,
        conversationId = conversationId,
        role = if (role == "user") MessageRole.USER else MessageRole.ASSISTANT,
        content = content,
        createdAt = createdAt,
        tokensPerSecond = tokensPerSecond,
        timeToFirstTokenMs = timeToFirstTokenMs,
        promptTokens = promptTokens,
        completionTokens = completionTokens
    )

    private companion object {
        const val MAX_TITLE_LENGTH = 60
        const val TITLE_TIMEOUT_MS = 20_000L
        const val TITLE_CONTEXT_CHARS = 400
    }
}