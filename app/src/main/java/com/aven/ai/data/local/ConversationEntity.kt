package com.aven.ai.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A conversation in Aven. Private conversations are excluded from the
 * drawer's recent-chat history and are never listed by the search query.
 */
@Entity(
    tableName = "conversations",
    indices = [Index(value = ["isPrivate", "lastMessageAt"])]
)
data class ConversationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val title: String,
    val isPrivate: Boolean = false,
    val isPinned: Boolean = false,
    val providerId: String? = null,
    val modelId: String? = null,
    val createdAt: Long,
    val lastMessageAt: Long
)