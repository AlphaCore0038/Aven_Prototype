package com.aven.ai.engine.remote

import com.aven.ai.data.local.MessageEntity
import com.aven.ai.engine.AiChatException
import com.aven.ai.engine.AiError
import com.aven.ai.engine.GenerateRequest
import com.aven.ai.engine.RemoteModelConfig
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class OpenAiCompatibleEngineTest {

    private lateinit var server: MockWebServer
    private lateinit var client: OkHttpClient
    private var apiKey: String? = "test-key"

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = OkHttpClient.Builder().build()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun engine(
        modelId: String = "deepseek-v4-flash",
        baseUrl: String = server.url("/v1").toString().trimEnd('/'),
        maxRateLimitRetries: Int = 3,
        rateLimitBackoffMs: Long = 1
    ) = OpenAiCompatibleEngine(
        config = RemoteModelConfig(
            id = "test-id",
            providerName = "TestProvider",
            baseUrl = baseUrl,
            modelId = modelId
        ),
        apiKey = { apiKey },
        client = client,
        maxRateLimitRetries = maxRateLimitRetries,
        rateLimitBackoffMs = rateLimitBackoffMs
    )

    private fun request(history: List<MessageEntity> = emptyList()) = GenerateRequest(
        conversationId = 1L,
        prompt = "Hello",
        history = history,
        isPrivate = false
    )

    private fun sseBody(vararg chunks: String): String = buildString {
        chunks.forEach { append("data: $it\n\n") }
        append("data: [DONE]\n\n")
    }

    @Test
    fun `streams deltas from a successful response`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                sseBody(
                    """{"choices":[{"delta":{"content":"Hel"}}]}""",
                    """{"choices":[{"delta":{"content":"lo"},"finish_reason":null}]}""",
                    """{"choices":[{"delta":{},"finish_reason":"stop"}]}"""
                )
            )
        )

        val deltas = engine().generate(request()).toList()
        assertEquals(listOf("Hel", "lo"), deltas)
    }

    @Test
    fun `ignores empty content deltas`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                sseBody(
                    """{"choices":[{"delta":{"content":""}}]}""",
                    """{"choices":[{"delta":{"content":"yes"}}]}"""
                )
            )
        )

        assertEquals(listOf("yes"), engine().generate(request()).toList())
    }

    @Test
    fun `missing api key fails without network`() = runTest {
        apiKey = null
        val error = runCatching { engine().generate(request()).toList() }.exceptionOrNull()
        assertTrue(error is AiChatException)
        assertTrue(error!!.message!!.contains("API key"))
        assertEquals(AiError.INVALID_CONFIGURATION, (error as AiChatException).error)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `401 surfaces the provider error message from the body`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(401).setBody(
                """{"type":"error","error":{"type":"CreditsError","message":"No payment method. Add a payment method here: https://opencode.ai/workspace/x/billing"}}"""
            )
        )
        val error = runCatching { engine().generate(request()).toList() }.exceptionOrNull()
        assertTrue(error is AiChatException)
        assertEquals(
            "No payment method. Add a payment method here: https://opencode.ai/workspace/x/billing",
            error!!.message
        )
    }

    @Test
    fun `non-json error body falls back to canned status message`() = runTest {
        server.enqueue(MockResponse().setResponseCode(401).setBody("gateway blew up"))
        val error = runCatching { engine().generate(request()).toList() }.exceptionOrNull()
        assertTrue(error is AiChatException)
        assertTrue(error!!.message!!.contains("Authentication failed"))
        assertEquals(AiError.AUTHENTICATION_FAILED, (error as AiChatException).error)
    }

    @Test
    fun `403 maps to access denied`() = runTest {
        server.enqueue(MockResponse().setResponseCode(403).setBody("nope"))
        val error = runCatching { engine().generate(request()).toList() }.exceptionOrNull()
        assertEquals(AiError.ACCESS_DENIED, (error as AiChatException).error)
        assertTrue(error!!.message!!.contains("Access denied"))
    }

    @Test
    fun `404 maps to model not found`() = runTest {
        server.enqueue(MockResponse().setResponseCode(404).setBody("missing"))
        val error = runCatching { engine().generate(request()).toList() }.exceptionOrNull()
        assertEquals(AiError.MODEL_NOT_FOUND, (error as AiChatException).error)
        assertTrue(error!!.message!!.contains("Model not found"))
    }

    @Test
    fun `400 maps to bad request`() = runTest {
        server.enqueue(MockResponse().setResponseCode(400).setBody("bad input"))
        val error = runCatching { engine().generate(request()).toList() }.exceptionOrNull()
        assertEquals(AiError.BAD_REQUEST, (error as AiChatException).error)
    }

    @Test
    fun `400 with context-limit wording maps to context limit`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(400).setBody(
                """{"error":{"message":"This model's maximum context length is 4096 tokens"}}"""
            )
        )
        val error = runCatching { engine().generate(request()).toList() }.exceptionOrNull()
        assertEquals(AiError.CONTEXT_LIMIT, (error as AiChatException).error)
        assertTrue(error!!.message!!.contains("maximum context length"))
    }

    @Test
    fun `5xx maps to provider error`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500).setBody("boom"))
        val error = runCatching { engine().generate(request()).toList() }.exceptionOrNull()
        assertEquals(AiError.PROVIDER_ERROR, (error as AiChatException).error)
        assertTrue(error!!.message!!.contains("Provider unavailable"))
    }

    @Test
    fun `429 maps to a rate-limit error after retries are exhausted`() = runTest {
        repeat(4) { server.enqueue(MockResponse().setResponseCode(429)) }
        val error = runCatching { engine().generate(request()).toList() }.exceptionOrNull()
        assertTrue(error is AiChatException)
        assertTrue(error!!.message!!.contains("Rate limit"))
        assertEquals(AiError.RATE_LIMITED, (error as AiChatException).error)
        assertEquals(4, server.requestCount)
    }

    @Test
    fun `recovers from a transient 429 with a retry`() = runTest {
        server.enqueue(MockResponse().setResponseCode(429))
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                sseBody("""{"choices":[{"delta":{"content":"ok"}}]}""")
            )
        )
        assertEquals(listOf("ok"), engine().generate(request()).toList())
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `connection failure surfaces an honest error`() = runTest {
        val dead = MockWebServer()
        dead.start()
        val deadUrl = dead.url("/v1").toString().trimEnd('/')
        dead.shutdown()
        val broken = OpenAiCompatibleEngine(
            config = RemoteModelConfig(
                id = "broken",
                providerName = "TestProvider",
                baseUrl = deadUrl,
                modelId = "x"
            ),
            apiKey = { "k" },
            client = client
        )
        val error = runCatching { broken.generate(request()).toList() }.exceptionOrNull()
        assertTrue(error is AiChatException)
        assertTrue(error!!.message!!.contains("reach"))
        assertEquals(AiError.NETWORK_ERROR, (error as AiChatException).error)
    }

    @Test
    fun `malformed gateway url fails before any request`() = runTest {
        val broken = OpenAiCompatibleEngine(
            config = RemoteModelConfig(
                id = "bad-url",
                providerName = "TestProvider",
                baseUrl = "not-a-url",
                modelId = "x"
            ),
            apiKey = { "k" },
            client = client
        )
        val error = runCatching { broken.generate(request()).toList() }.exceptionOrNull()
        assertTrue(error is AiChatException)
        assertEquals(AiError.INVALID_CONFIGURATION, (error as AiChatException).error)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `mid-stream disconnect surfaces an honest error after retries`() = runTest {
        repeat(2) {
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY)
                    .setBody(sseBody("""{"choices":[{"delta":{"content":"par"}}]}"""))
            )
        }
        val error = runCatching { engine().generate(request()).toList() }.exceptionOrNull()
        assertTrue(error is AiChatException)
        assertEquals(AiError.STREAM_INTERRUPTED, (error as AiChatException).error)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `recovers from a mid-stream disconnect before any content`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY)
                .setBody(sseBody("""{"choices":[{"delta":{"content":"par"}}]}"""))
        )
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                sseBody("""{"choices":[{"delta":{"content":"ok"}}]}""")
            )
        )
        assertEquals(listOf("ok"), engine().generate(request()).toList())
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `metadata raw surfaces the real provider reason`() = runTest {
        val body = """{"error":{"message":"Provider returned error","code":429,"metadata":{"raw":"glm-5.2:free is temporarily rate-limited upstream. Please retry shortly."}}}"""
        repeat(4) { server.enqueue(MockResponse().setResponseCode(429).setBody(body)) }
        val error = runCatching { engine().generate(request()).toList() }.exceptionOrNull()
        assertTrue(error is AiChatException)
        assertTrue(error!!.message!!.contains("rate-limited upstream"))
    }

    @Test
    fun `provider error chunk surfaces its message`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                sseBody("""{"error":{"message":"Insufficient credits"}}""")
            )
        )
        val error = runCatching { engine().generate(request()).toList() }.exceptionOrNull()
        assertTrue(error is AiChatException)
        assertEquals("Insufficient credits", error!!.message)
        assertEquals(AiError.PROVIDER_ERROR, (error as AiChatException).error)
    }

    @Test
    fun `request carries the bearer key and model`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(sseBody("""{"choices":[{"delta":{"content":"x"}}]}""")))
        engine(modelId = "custom-model").generate(request()).toList()

        val recorded = server.takeRequest()
        assertEquals("Bearer test-key", recorded.getHeader("Authorization"))
        val body = recorded.body.readUtf8()
        assertTrue(body.contains("\"model\":\"custom-model\""))
        assertTrue(body.contains("\"stream\":true"))
    }

    @Test
    fun `request includes system, history, and prompt messages`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(sseBody("""{"choices":[{"delta":{"content":"x"}}]}""")))
        val history = listOf(
            MessageEntity(conversationId = 1L, role = "user", content = "Who are you?", createdAt = 1),
            MessageEntity(conversationId = 1L, role = "assistant", content = "I am Aven.", createdAt = 2)
        )
        engine().generate(request(history)).toList()

        val body = server.takeRequest().body.readUtf8()
        assertTrue(body.contains("\"role\":\"system\""))
        assertTrue(body.contains("\"role\":\"user\",\"content\":\"Who are you?\""))
        assertTrue(body.contains("\"role\":\"assistant\",\"content\":\"I am Aven.\""))
        assertTrue(body.contains("\"role\":\"user\",\"content\":\"Hello\""))
    }
}