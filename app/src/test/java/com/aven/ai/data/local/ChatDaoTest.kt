package com.aven.ai.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ChatDaoTest {

    private lateinit var db: AvenDatabase
    private lateinit var dao: ChatDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AvenDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.chatDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun conversation(
        id: Long = 0,
        title: String = "Conversation",
        isPrivate: Boolean = false,
        isPinned: Boolean = false,
        lastMessageAt: Long = 0L
    ) = ConversationEntity(
        id = id,
        title = title,
        isPrivate = isPrivate,
        isPinned = isPinned,
        createdAt = lastMessageAt,
        lastMessageAt = lastMessageAt
    )

    private fun message(conversationId: Long, role: String, content: String, at: Long) =
        MessageEntity(
            conversationId = conversationId,
            role = role,
            content = content,
            createdAt = at
        )

    @Test
    fun `createConversationWithFirstMessage persists both rows`() = runTest {
        val conversationId = dao.createConversationWithFirstMessage(
            conversation(lastMessageAt = 1000L),
            message(0, "user", "Hello Aven", 1000L)
        )

        assertNotNull(dao.getConversation(conversationId))
        assertEquals(1, dao.getMessages(conversationId).size)
        assertEquals("Hello Aven", dao.getMessages(conversationId).first().content)
    }

    @Test
    fun `visible conversations ordered by pinned then lastMessageAt desc`() = runTest {
        dao.upsertConversation(conversation(lastMessageAt = 100L))
        dao.upsertConversation(conversation(lastMessageAt = 300L, isPinned = true))
        dao.upsertConversation(conversation(lastMessageAt = 200L))

        val visible = dao.observeVisibleConversations().first()
        assertEquals(listOf(300L, 200L, 100L), visible.map { it.lastMessageAt })
    }

    @Test
    fun `private conversations never appear in visible or search results`() = runTest {
        dao.upsertConversation(conversation(title = "Public chat", lastMessageAt = 100L))
        dao.upsertConversation(
            conversation(title = "Secret chat", isPrivate = true, lastMessageAt = 200L)
        )

        val visible = dao.observeVisibleConversations().first()
        assertEquals(listOf("Public chat"), visible.map { it.title })

        val search = dao.searchVisibleConversations("Secret").first()
        assertTrue(search.isEmpty())
        val searchAll = dao.searchVisibleConversations("chat").first()
        assertEquals(listOf("Public chat"), searchAll.map { it.title })
    }

    @Test
    fun `updating lastMessageAt reorders the list`() = runTest {
        val a = dao.upsertConversation(conversation(title = "A", lastMessageAt = 100L))
        val b = dao.upsertConversation(conversation(title = "B", lastMessageAt = 200L))

        dao.updateConversation(
            id = a,
            title = "A",
            isPinned = false,
            providerId = null,
            modelId = null,
            lastMessageAt = 300L
        )

        val visible = dao.observeVisibleConversations().first()
        assertEquals(listOf("A", "B"), visible.map { it.title })

        // ensure b is untouched
        assertNotNull(dao.getConversation(b))
    }

    @Test
    fun `deleting a conversation cascades to its messages`() = runTest {
        val conversationId = dao.createConversationWithFirstMessage(
            conversation(lastMessageAt = 100L),
            message(0, "user", "Hi", 100L)
        )
        dao.upsertMessage(message(conversationId, "assistant", "Hello!", 200L))
        assertEquals(2, dao.getMessages(conversationId).size)

        dao.deleteConversation(conversationId)

        assertNull(dao.getConversation(conversationId))
        assertTrue(dao.getMessages(conversationId).isEmpty())
    }

    @Test
    fun `renameConversation and setPinned update fields`() = runTest {
        val id = dao.upsertConversation(conversation(title = "Old", lastMessageAt = 100L))
        dao.renameConversation(id, "New title")
        dao.setPinned(id, true)

        val updated = dao.getConversation(id)!!
        assertEquals("New title", updated.title)
        assertTrue(updated.isPinned)
    }
}