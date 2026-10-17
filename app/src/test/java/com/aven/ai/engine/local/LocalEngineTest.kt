package com.aven.ai.engine.local

import com.aven.ai.data.local.MessageEntity
import com.aven.ai.engine.GenerateRequest
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalEngineTest {

    private val engine = LocalEngine(null)

    private fun request(
        prompt: String = "hi",
        history: List<MessageEntity> = emptyList()
    ) = GenerateRequest(
        conversationId = 1L,
        prompt = prompt,
        history = history,
        isPrivate = false
    )

    private fun message(role: String, content: String) = MessageEntity(
        conversationId = 1L,
        role = role,
        content = content,
        createdAt = 0L
    )

    @Test
    fun `buildPrompt uses the ChatML template for the default model`() {
        val prompt = engine.buildPrompt(request("hello"))
        assertTrue("starts with the system turn", prompt.startsWith("<|im_start|>system\n"))
        assertTrue("contains system instructions", prompt.contains("You are Aven"))
        assertTrue("contains the user message", prompt.contains("<|im_start|>user\nhello<|im_end|>\n"))
        assertTrue("ends with the assistant generation prompt", prompt.endsWith("<|im_start|>assistant\n"))
    }

    @Test
    fun `buildPrompt includes history before the new prompt`() {
        val prompt = engine.buildPrompt(
            request("new", history = listOf(message("user", "old question")))
        )
        val userIndex = prompt.indexOf("old question")
        val assistantIndex = prompt.indexOf("new")
        assertTrue(userIndex in 0 until assistantIndex)
        assertTrue(prompt.contains("<|im_start|>user\n"))
    }

    @Test
    fun `buildPrompt truncates oversized history`() {
        val big = "x".repeat(6000)
        val history = listOf(
            message("user", big),
            message("assistant", big)
        )
        val prompt = engine.buildPrompt(request("new", history))
        assertTrue("first history message fits the budget", prompt.contains(big))
        assertTrue("second history message is truncated away", !prompt.contains(big + big))
        assertTrue("the new prompt is always present", prompt.contains("new"))
    }

    @Test
    fun `buildPrompt uses gemma 3 template for gemma model`() {
        val gemmaEngine = LocalEngine(null, LocalEngine.GEMMA_ID)
        val prompt = gemmaEngine.buildPrompt(request("hello"))
        assertTrue("gemma starts with bos", prompt.startsWith("<bos>"))
        assertTrue("gemma uses start_of_turn", prompt.contains("<start_of_turn>user"))
        assertTrue("gemma ends with model turn", prompt.trimEnd().endsWith("<start_of_turn>model"))
        assertTrue("gemma does not use chatml markers", !prompt.contains("<|im_start|>"))
    }

    @Test
    fun `default model and gemma use different templates`() {
        val gemmaEngine = LocalEngine(null, LocalEngine.GEMMA_ID)
        val defaultPrompt = engine.buildPrompt(request("hello"))
        val gemmaPrompt = gemmaEngine.buildPrompt(request("hello"))
        assertTrue(defaultPrompt != gemmaPrompt)
        assertTrue(defaultPrompt.contains("<|im_start|>"))
        assertTrue(gemmaPrompt.contains("<start_of_turn>"))
    }
}