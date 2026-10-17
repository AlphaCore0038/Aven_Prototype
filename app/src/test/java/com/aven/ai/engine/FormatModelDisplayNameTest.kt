package com.aven.ai.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FormatModelDisplayNameTest {

    // ---- required examples -------------------------------------------------

    @Test
    fun `nemotron super drops parameter and architecture counts`() {
        assertEquals(
            "Nemotron 3 Super",
            formatModelDisplayName("nvidia/nemotron-3-super-120b-a12b:free")
        )
    }

    @Test
    fun `llama versions are kept, sizes and instruct dropped`() {
        assertEquals("Llama 3.3", formatModelDisplayName("meta-llama/llama-3.3-70b-instruct"))
        assertEquals("Llama 3.2", formatModelDisplayName("meta-llama/llama-3.2-1b-instruct"))
    }

    @Test
    fun `qwen fused versions split without losing the decimal`() {
        assertEquals("Qwen 3.5", formatModelDisplayName("qwen/qwen3.5-9b"))
        assertEquals("Qwen 3", formatModelDisplayName("qwen/qwen3-32b"))
        assertEquals("Qwen 2.5", formatModelDisplayName("qwen/qwen2.5-72b-instruct"))
    }

    @Test
    fun `gemma versions and activation counts`() {
        assertEquals("Gemma 3", formatModelDisplayName("google/gemma-3-27b-it"))
        assertEquals("Gemma 3n", formatModelDisplayName("google/gemma-3n-e4b-it"))
    }

    @Test
    fun `mistral and mixtral`() {
        assertEquals(
            "Mistral Small 3.2",
            formatModelDisplayName("mistralai/mistral-small-3.2-24b-instruct")
        )
        assertEquals("Mixtral", formatModelDisplayName("mistralai/mixtral-8x7b-instruct"))
    }

    @Test
    fun `deepseek distill truncates and versions are kept`() {
        assertEquals(
            "DeepSeek R1",
            formatModelDisplayName("deepseek-ai/DeepSeek-R1-Distill-Qwen-7B")
        )
        assertEquals("DeepSeek V3.2", formatModelDisplayName("deepseek-ai/DeepSeek-V3.2"))
        // Accepted deviation (approved): generic casing, no brand table.
        assertEquals(
            "Deepseek Coder V2",
            formatModelDisplayName("deepseek-ai/deepseek-coder-v2-lite-instruct")
        )
    }

    @Test
    fun `gpt versions and mini variant`() {
        assertEquals("GPT 5.6", formatModelDisplayName("openai/gpt-5.6"))
        assertEquals("GPT 5.6 Mini", formatModelDisplayName("openai/gpt-5.6-mini"))
        assertEquals("GPT 4.1", formatModelDisplayName("openai/gpt-4.1"))
        assertEquals("GPT 4.1 Mini", formatModelDisplayName("openai/gpt-4.1-mini"))
    }

    @Test
    fun `claude hyphenated versions join with a dot`() {
        assertEquals("Claude Sonnet 4.5", formatModelDisplayName("anthropic/claude-sonnet-4-5"))
        assertEquals("Claude Opus 4.1", formatModelDisplayName("anthropic/claude-opus-4-1"))
        assertEquals("Claude Haiku 4.5", formatModelDisplayName("anthropic/claude-haiku-4-5"))
    }

    @Test
    fun `nemotron phi command and grok variants`() {
        assertEquals("Nemotron 4", formatModelDisplayName("nvidia/nemotron-4-340b-instruct"))
        assertEquals("Phi 4 Mini", formatModelDisplayName("microsoft/phi-4-mini-instruct"))
        assertEquals("Command R Plus", formatModelDisplayName("cohere/command-r-plus"))
        assertEquals("Grok 4.1 Fast", formatModelDisplayName("x-ai/grok-4.1-fast"))
    }

    // ---- removal rules -----------------------------------------------------

    @Test
    fun `parameter counts are removed`() {
        assertEquals("Mistral", formatModelDisplayName("mistralai/mistral-7b-instruct"))
        assertEquals("Llama 3.2", formatModelDisplayName("meta-llama/llama-3.2-1b-instruct-gguf"))
        assertEquals("Llama 3", formatModelDisplayName("meta-llama/llama-3-8b"))
        assertEquals("Gemma 3", formatModelDisplayName("google/gemma-3-27b-it"))
    }

    @Test
    fun `quantization tokens are removed`() {
        assertEquals("Mistral", formatModelDisplayName("mistralai/mistral-7b-q4_k_m"))
        assertEquals("Llama 3.2", formatModelDisplayName("meta-llama/llama-3.2-instruct-q8_0"))
        assertEquals("Qwen 2.5", formatModelDisplayName("qwen/qwen2.5-gguf"))
        assertEquals("Gemma 3", formatModelDisplayName("google/gemma-3-fp8"))
        assertEquals("Llama 3", formatModelDisplayName("meta-llama/llama-3-safetensors"))
        assertEquals("Llama 3", formatModelDisplayName("meta-llama/llama-3-int4"))
    }

    @Test
    fun `technical suffixes are removed`() {
        assertEquals("Qwen 2.5", formatModelDisplayName("qwen/qwen2.5-72b-instruct"))
        assertEquals("Gemma 3", formatModelDisplayName("google/gemma-3-1b-it"))
        assertEquals("Mistral", formatModelDisplayName("mistralai/mistral-7b-chat"))
        assertEquals("Phi 4 Mini", formatModelDisplayName("microsoft/phi-4-mini-instruct"))
        assertEquals("Deepseek Coder V2", formatModelDisplayName("deepseek/deepseek-coder-v2-lite"))
    }

    @Test
    fun `provider prefixes and routing tags never leak`() {
        val ids = listOf(
            "nvidia/nemotron-3-super-120b-a12b:free",
            "meta-llama/llama-3.3-70b-instruct:latest",
            "qwen/qwen3.5-9b",
            "openai/gpt-5.6-mini",
            "x-ai/grok-4.1-fast"
        )
        for (id in ids) {
            val out = formatModelDisplayName(id)
            assertFalse("$id leaked prefix", out.contains('/'))
            assertFalse("$id leaked tag", out.contains(':'))
        }
        assertEquals("Grok 4.1 Fast", formatModelDisplayName("x-ai/grok-4.1-fast:latest"))
    }

    // ---- keep rules --------------------------------------------------------

    @Test
    fun `meaningful variants remain`() {
        assertEquals("GPT 5.6 Mini", formatModelDisplayName("openai/gpt-5.6-mini"))
        assertEquals("Mistral Small 3.2", formatModelDisplayName("mistralai/mistral-small-3.2-24b"))
        assertEquals("Phi 4 Mini", formatModelDisplayName("microsoft/phi-4-mini"))
        assertEquals("Grok 4.1 Fast", formatModelDisplayName("x-ai/grok-4.1-fast"))
        assertEquals("Command R Plus", formatModelDisplayName("cohere/command-r-plus"))
        assertEquals("Nemotron 3 Super", formatModelDisplayName("nvidia/nemotron-3-super-120b"))
        assertEquals("Claude Sonnet 4.5", formatModelDisplayName("anthropic/claude-sonnet-4-5"))
        assertEquals("Claude Opus 4.1", formatModelDisplayName("anthropic/claude-opus-4-1"))
        assertEquals("Claude Haiku 4.5", formatModelDisplayName("anthropic/claude-haiku-4-5"))
        assertEquals("Deepseek Coder V2", formatModelDisplayName("deepseek/deepseek-coder-v2"))
    }

    @Test
    fun `versions stay intact and never collapse`() {
        assertEquals("Qwen 3.5", formatModelDisplayName("qwen/qwen3.5-9b"))
        assertEquals("Qwen 2.5", formatModelDisplayName("qwen/qwen2.5-72b"))
        assertEquals("Llama 3.3", formatModelDisplayName("meta-llama/llama-3.3-70b"))
        assertEquals("DeepSeek V3.2", formatModelDisplayName("deepseek-ai/DeepSeek-V3.2"))
        assertEquals("GPT 4.1", formatModelDisplayName("openai/gpt-4.1"))
        assertEquals("Claude Sonnet 4.5", formatModelDisplayName("anthropic/claude-sonnet-4-5"))
        // Explicit guard against the known regression shape.
        assertEquals("Qwen 3.5", formatModelDisplayName("qwen/qwen3.5-9b"))
        assertEquals("Claude Sonnet 4.5", formatModelDisplayName("anthropic/claude-sonnet-4-5"))
    }

    @Test
    fun `digit-flanked separators become version dots`() {
        assertEquals("Claude Sonnet 4.5", formatModelDisplayName("claude-sonnet-4-5"))
        assertEquals("Claude Opus 4.1", formatModelDisplayName("claude-opus-4-1"))
        assertEquals("Grok 4.1 Fast", formatModelDisplayName("grok-4.1-fast"))
    }

    // ---- edge cases --------------------------------------------------------

    @Test
    fun `blank input falls back safely`() {
        assertEquals("", formatModelDisplayName(""))
        assertEquals("", formatModelDisplayName("   "))
        assertEquals("///", formatModelDisplayName("///"))
    }

    @Test
    fun `blank result falls back to the trimmed original`() {
        assertEquals("q4_k_m", formatModelDisplayName("q4_k_m"))
        assertEquals("gguf", formatModelDisplayName("gguf"))
    }

    @Test
    fun `mixed casing underscores and dotted versions`() {
        assertEquals("Mymodel", formatModelDisplayName("mymodel"))
        assertEquals("GPT", formatModelDisplayName("gpt"))
        assertEquals("GPT 4o", formatModelDisplayName("gpt-4o"))
        assertEquals("Qwen 2.5 Coder", formatModelDisplayName("qwen/qwen2.5-coder_32b"))
        assertEquals("DeepSeek V3.2", formatModelDisplayName("deepseek-ai/DeepSeek-V3.2"))
    }

    @Test
    fun `raw model id is never mutated`() {
        val raw = "nvidia/nemotron-3-super-120b-a12b:free"
        val before = raw
        val display = formatModelDisplayName(raw)
        assertEquals(before, raw)
        assertEquals("nvidia/nemotron-3-super-120b-a12b:free", raw)
        assertFalse(display == raw)

        val config = RemoteModelConfig(
            id = "cfg-1",
            providerName = "OpenRouter",
            baseUrl = "https://example.test/v1",
            modelId = raw
        )
        formatModelDisplayName(config.modelId)
        assertEquals(raw, config.modelId)
        assertEquals("cfg-1", config.id)
    }
}
