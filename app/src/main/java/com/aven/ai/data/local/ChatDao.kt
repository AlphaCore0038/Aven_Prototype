package com.aven.ai.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/**
 * Conversations and their messages. All history queries exclude private
 * conversations, which must never surface in the drawer or search.
 */
@Dao
interface ChatDao {

    // ---- conversations ----

    @Query("SELECT * FROM conversations WHERE isPrivate = 0 ORDER BY isPinned DESC, lastMessageAt DESC")
    fun observeVisibleConversations(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE isPrivate = 0 AND (title LIKE '%' || :query || '%') ORDER BY isPinned DESC, lastMessageAt DESC")
    fun searchVisibleConversations(query: String): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE id = :id")
    suspend fun getConversation(id: Long): ConversationEntity?

    @Query("SELECT COUNT(*) FROM conversations WHERE isPrivate = 0")
    suspend fun visibleConversationCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertConversation(conversation: ConversationEntity): Long

    @Query("UPDATE conversations SET title = :title, isPinned = :isPinned, providerId = :providerId, modelId = :modelId, lastMessageAt = :lastMessageAt WHERE id = :id")
    suspend fun updateConversation(
        id: Long,
        title: String,
        isPinned: Boolean,
        providerId: String?,
        modelId: String?,
        lastMessageAt: Long
    )

    @Query("UPDATE conversations SET isPinned = :pinned WHERE id = :id")
    suspend fun setPinned(id: Long, pinned: Boolean)

    @Query("UPDATE conversations SET title = :title WHERE id = :id")
    suspend fun renameConversation(id: Long, title: String)

    @Query("DELETE FROM conversations WHERE id = :id")
    suspend fun deleteConversation(id: Long)

    // ---- messages ----

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY createdAt ASC, id ASC")
    fun observeMessages(conversationId: Long): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY createdAt ASC, id ASC")
    suspend fun getMessages(conversationId: Long): List<MessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMessage(message: MessageEntity): Long

    @Query("DELETE FROM messages WHERE id = :id")
    suspend fun deleteMessage(id: Long)

    @Query("DELETE FROM messages WHERE conversationId = :conversationId")
    suspend fun deleteMessages(conversationId: Long)

    /** Creates a conversation and its first message atomically. */
    @Transaction
    suspend fun createConversationWithFirstMessage(
        conversation: ConversationEntity,
        firstMessage: MessageEntity
    ): Long {
        val conversationId = upsertConversation(conversation)
        upsertMessage(firstMessage.copy(conversationId = conversationId))
        return conversationId
    }
}