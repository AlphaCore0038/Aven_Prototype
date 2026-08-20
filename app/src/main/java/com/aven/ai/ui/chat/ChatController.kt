package com.aven.ai.ui.chat

import com.aven.ai.data.local.ChatDao
import com.aven.ai.data.local.ConversationEntity
import com.aven.ai.data.local.MessageEntity
import com.aven.ai.engine.AiChatEngine
import com.aven.ai.engine.AiChatException
import com.aven.ai.engine.GenerateRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class MessageRole { USER, ASSISTANT }

data class UiMessage(
    val id: Long,
    val conversationId: Long,
    val role: MessageRole,
    val content: String,
    val isError: Boolean = false,
    val createdAt: Long
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

                val targetConversationId = if (conversationId == 0L) {
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
                    val existing = chatDao.getConversation(conversationId)
                    chatDao.updateConversation(
                        id = conversationId,
                        title = existing?.title ?: prompt.take(MAX_TITLE_LENGTH),
                        isPinned = existing?.isPinned ?: false,
                        providerId = existing?.providerId ?: providerId,
                        modelId = existing?.modelId ?: modelId,
                        lastMessageAt = now
                    )
                    conversationId
                }

                if (conversationId == 0L && targetConversationId != 0L) {
                    conversationId = targetConversationId
                    observe(targetConversationId)
                }

                val history = chatDao.getMessages(targetConversationId)
                _uiState.update {
                    it.copy(isGenerating = true, streamingText = "", lastError = null)
                }

                generationJob = scope.launch {
                    try {
                        engine.generate(
                            GenerateRequest(
                                conversationId = targetConversationId,
                                prompt = prompt,
                                history = history,
                                isPrivate = _uiState.value.isPrivate
                            )
                        ).collect { delta ->
                            _uiState.update { it.copy(streamingText = it.streamingText + delta) }
                        }

                        val finalText = _uiState.value.streamingText
                        if (finalText.isNotBlank()) {
                            chatDao.upsertMessage(
                                assistantMessage(targetConversationId, finalText, System.currentTimeMillis())
                            )
                        }
                    } catch (e: CancellationException) {
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

    private fun assistantMessage(conversationId: Long, text: String, at: Long) = MessageEntity(
        conversationId = conversationId,
        role = "assistant",
        content = text,
        createdAt = at
    )

    private fun MessageEntity.toUiMessage() = UiMessage(
        id = id,
        conversationId = conversationId,
        role = if (role == "user") MessageRole.USER else MessageRole.ASSISTANT,
        content = content,
        createdAt = createdAt
    )

    private companion object {
        const val MAX_TITLE_LENGTH = 60
    }
}