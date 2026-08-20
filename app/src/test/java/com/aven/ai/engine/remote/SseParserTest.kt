package com.aven.ai.engine.remote

import kotlinx.coroutines.test.runTest
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Test

class SseParserTest {

    private suspend fun parse(stream: String): List<String> {
        val events = mutableListOf<String>()
        SseParser.parse(Buffer().writeUtf8(stream)) { events.add(it) }
        return events
    }

    @Test
    fun `parses multiple data events separated by blank lines`() = runTest {
        val stream = buildString {
            append("data: {\"a\":1}\n\n")
            append("data: {\"a\":2}\n\n")
            append("data: [DONE]\n\n")
        }
        assertEquals(listOf("{\"a\":1}", "{\"a\":2}", "[DONE]"), parse(stream))
    }

    @Test
    fun `multi-line data payloads are joined with newlines`() = runTest {
        val stream = "data: first line\ndata: second line\n\n"
        assertEquals(listOf("first line\nsecond line"), parse(stream))
    }

    @Test
    fun `comments and non-data fields are ignored`() = runTest {
        val stream = buildString {
            append(": keep-alive comment\n")
            append("event: message\n")
            append("retry: 1000\n")
            append("id: 42\n")
            append("data: {\"ok\":true}\n\n")
        }
        assertEquals(listOf("{\"ok\":true}"), parse(stream))
    }

    @Test
    fun `trailing event without blank line is still delivered`() = runTest {
        val stream = "data: {\"a\":1}\n\ndata: {\"a\":2}\n"
        assertEquals(listOf("{\"a\":1}", "{\"a\":2}"), parse(stream))
    }

    @Test
    fun `crlf line endings are handled`() = runTest {
        val stream = "data: {\"a\":1}\r\n\r\ndata: {\"a\":2}\r\n\r\n"
        assertEquals(listOf("{\"a\":1}", "{\"a\":2}"), parse(stream))
    }

    @Test
    fun `crlf multi-line payloads are joined with newlines`() = runTest {
        val stream = "data: first line\r\ndata: second line\r\n\r\n"
        assertEquals(listOf("first line\nsecond line"), parse(stream))
    }

    @Test
    fun `empty stream produces no events`() = runTest {
        assertEquals(emptyList<String>(), parse(""))
    }
}

class DeltaExtractorTest {

    @Test
    fun `extracts content delta`() {
        val event = """{"choices":[{"delta":{"content":"Hello"},"index":0}]}"""
        assertEquals("Hello", DeltaExtractor.extractContent(event))
    }

    @Test
    fun `null content yields null delta`() {
        val event = """{"choices":[{"delta":{"role":"assistant"},"index":0}]}"""
        assertEquals(null, DeltaExtractor.extractContent(event))
    }

    @Test
    fun `finish chunk without content yields null`() {
        val event = """{"choices":[{"delta":{},"finish_reason":"stop","index":0}]}"""
        assertEquals(null, DeltaExtractor.extractContent(event))
    }

    @Test
    fun `malformed json yields null instead of crashing`() {
        assertEquals(null, DeltaExtractor.extractContent("not json"))
    }

    @Test
    fun `extracts provider error message`() {
        val event = """{"error":{"message":"Insufficient credits","type":"insufficient_quota"}}"""
        assertEquals("Insufficient credits", DeltaExtractor.extractError(event))
    }

    @Test
    fun `no error field yields null`() {
        val event = """{"choices":[{"delta":{"content":"ok"}}]}"""
        assertEquals(null, DeltaExtractor.extractError(event))
    }
}