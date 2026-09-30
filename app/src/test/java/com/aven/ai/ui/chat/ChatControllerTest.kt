package com.aven.ai.ui.chat

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.aven.ai.data.local.AvenDatabase
import com.aven.ai.data.local.ChatDao
import com.aven.ai.engine.AiChatEngine
import com.aven.ai.engine.AiChatException
import com.aven.ai.engine.GenerateRequest
import com.aven.ai.engine.UnavailableEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private class FakeEngine(
    private val chunk: String = "Hello there!",
    private val failFirstCall: Boolean = false
) : AiChatEngine {
    override val id = "fake"
    override val displayName = "Fake"
    private var calls = 0
    override fun generate(request: GenerateRequest): Flow<String> = flow {
        if (failFirstCall && calls++ == 0) throw AiChatException("First call fails")
        emit(chunk)
    }
}

private class SlowEngine : AiChatEngine {
    override val id = "slow"
    override val displayName = "Slow"
    override fun generate(request: GenerateRequest): Flow<String> = flow {
        emit("Part")
        withContext(Dispatchers.IO) { Thread.sleep(300) }
        emit("Two")
    }
}

/** Streams a partial reply, then fails mid-stream. */
private class PartialThenFailEngine : AiChatEngine {
    override val id = "partial-fail"
    override val displayName = "PartialFail"
    override fun generate(request: GenerateRequest): Flow<String> = flow {
        emit("Kept text")
        withContext(Dispatchers.IO) { Thread.sleep(150) }
        throw AiChatException("Connection lost while generating.")
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ChatControllerTest {

    private lateinit var db: AvenDatabase
    private lateinit var dao: ChatDao
    private lateinit var scope: TestScope

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AvenDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.chatDao()
        scope = TestScope(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun controller(engine: AiChatEngine = FakeEngine()) =
        ChatController(chatDao = dao, engineProvider = { engine }, scope = scope)

    private fun settle(timeoutMs: Long = 5000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!condition() && System.currentTimeMillis() < deadline) {
            Thread.sleep(10)
        }
        assertTrue("condition not met within $timeoutMs ms", condition())
    }

    private fun persistedMessages(conversationId: Long) =
        runBlocking { dao.getMessages(conversationId) }

    @Test
    fun `start on a new chat shows the welcome state`() {
        val controller = controller()
        controller.start(0L, false)

        assertFalse(controller.uiState.value.hasMessages)
        assertTrue(controller.uiState.value.messages.isEmpty())
        assertFalse(controller.uiState.value.isGenerating)
    }

    @Test
    fun `send persists the user message and creates the conversation`() {
        val controller = controller(engine = UnavailableEngine())
        controller.start(0L, false)
        controller.send("Hello Aven")

        val conversationId = runBlocking { dao.observeVisibleConversations().first() }
            .firstOrNull()?.id ?: runBlocking { dao.getConversation(1L) }?.id

        settle {
            runBlocking { dao.visibleConversationCount() } == 1 &&
                controller.uiState.value.lastError != null
        }

        val messages = persistedMessages(conversationId ?: 1L)
        assertEquals(1, messages.size)
        assertEquals("user", messages.first().role)
        assertEquals("Hello Aven", messages.first().content)
        assertEquals("Hello Aven", runBlocking { dao.getConversation(conversationId ?: 1L) }?.title)
        assertEquals("Aven's AI isn't connected yet.", controller.uiState.value.lastError)
        assertFalse(controller.uiState.value.isGenerating)
    }

    @Test
    fun `successful generation streams and persists the assistant reply`() {
        val controller = controller(engine = FakeEngine(chunk = "Hello there!"))
        controller.start(0L, false)
        controller.send("Hi")

        settle {
            controller.uiState.value.messages.size == 2 &&
                !controller.uiState.value.isGenerating
        }

        val conversationId = runBlocking { dao.observeVisibleConversations().first() }.first().id
        val messages = persistedMessages(conversationId)
        assertEquals(2, messages.size)
        assertEquals("user", messages[0].role)
        assertEquals("assistant", messages[1].role)
        assertEquals("Hello there!", messages[1].content)
        assertFalse(controller.uiState.value.isGenerating)
    }

    @Test
    fun `stop persists the partial reply`() {
        val controller = controller(engine = SlowEngine())
        controller.start(0L, false)
        controller.send("Hi")
        Thread.sleep(150)
        controller.stop()

        settle { !controller.uiState.value.isGenerating }

        val conversationId = runBlocking { dao.observeVisibleConversations().first() }.first().id
        val messages = persistedMessages(conversationId)
        assertEquals(2, messages.size)
        assertEquals("Part", messages[1].content)
    }

    @Test
    fun `loading an existing conversation shows its history`() {
        val controller = controller()
        val conversationId = runBlocking {
            dao.createConversationWithFirstMessage(
                com.aven.ai.data.local.ConversationEntity(
                    title = "Old chat",
                    createdAt = 100L,
                    lastMessageAt = 100L
                ),
                com.aven.ai.data.local.MessageEntity(
                    conversationId = 0L,
                    role = "user",
                    content = "Question from the past",
                    createdAt = 100L
                )
            )
        }

        controller.start(conversationId, false)
        settle { controller.uiState.value.messages.size == 1 }

        assertEquals("Question from the past", controller.uiState.value.messages.first().content)
        assertEquals(MessageRole.USER, controller.uiState.value.messages.first().role)
        assertFalse(controller.uiState.value.isLoading)
    }

    @Test
    fun `private chat conversations are excluded from visible history`() {
        val controller = controller()
        controller.start(0L, true)
        controller.send("Secret question")

        settle {
            controller.uiState.value.messages.size == 2 ||
                controller.uiState.value.lastError != null
        }

        assertEquals(0, runBlocking { dao.visibleConversationCount() })
        val conversationId = runBlocking { dao.getConversation(1L) }!!.id
        assertTrue(runBlocking { dao.getConversation(conversationId) }!!.isPrivate)
    }

    @Test
    fun `error mid-stream keeps the partial reply and surfaces the failure`() {
        val controller = controller(engine = PartialThenFailEngine())
        controller.start(0L, false)
        controller.send("Hi")

        settle {
            controller.uiState.value.lastError != null &&
                !controller.uiState.value.isGenerating
        }

        val conversationId = runBlocking { dao.observeVisibleConversations().first() }.first().id
        val messages = persistedMessages(conversationId)
        assertEquals(2, messages.size)
        assertEquals("user", messages[0].role)
        assertEquals("assistant", messages[1].role)
        assertEquals("Kept text", messages[1].content)
        assertEquals("Connection lost while generating.", controller.uiState.value.lastError)
    }

    @Test
    fun `error clears on the next send`() {
        val controller = controller(engine = FakeEngine(chunk = "Recovered", failFirstCall = true))
        controller.start(0L, false)
        controller.send("First")
        settle { controller.uiState.value.lastError != null }
        assertTrue(controller.uiState.value.lastError!!.contains("First call fails"))
        assertEquals(1, persistedMessages(1L).size)

        controller.send("Second")
        settle {
            controller.uiState.value.messages.size == 3 &&
                controller.uiState.value.lastError == null
        }

        val messages = persistedMessages(1L)
        assertEquals(3, messages.size)
        assertEquals("assistant", messages[2].role)
        assertEquals("Recovered", messages[2].content)
    }
}