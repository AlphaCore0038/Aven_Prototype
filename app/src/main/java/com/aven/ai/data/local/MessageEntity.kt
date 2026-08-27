package com.aven.ai.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity = ConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversationId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("conversationId")]
)
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val conversationId: Long,
    val role: String,
    val content: String,
    val modelId: String? = null,
    val createdAt: Long,
    val promptTokens: Int? = null,
    val completionTokens: Int? = null,
    val tokensPerSecond: Float? = null,
    val timeToFirstTokenMs: Long? = null
)