package com.aven.ai.engine.remote

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Extracts streamed content deltas and error payloads from OpenAI-compatible
 * chunk JSON without requiring generated serializers.
 */
object DeltaExtractor {

    private val json = Json { ignoreUnknownKeys = true }

    /** The content delta of a chunk, or null when the chunk carries none. */
    fun extractContent(event: String): String? = runCatching {
        val root = json.parseToJsonElement(event).jsonObject
        root["choices"]?.jsonArray?.firstOrNull()?.jsonObject
            ?.get("delta")?.jsonObject
            ?.get("content")?.jsonPrimitive?.contentOrNull
    }.getOrNull()

    /** A provider-side error message, or null when the chunk is not an error. */
    fun extractError(event: String): String? = runCatching {
        val root = json.parseToJsonElement(event).jsonObject
        root["error"]?.jsonObject?.get("message")?.jsonPrimitive?.contentOrNull
    }.getOrNull()

    /** Usage from a chunk with `stream_options.include_usage`, or null when absent. */
    fun extractUsage(event: String): Pair<Int, Int>? = runCatching {
        val root = json.parseToJsonElement(event).jsonObject
        val usage = root["usage"]?.jsonObject ?: return@runCatching null
        val prompt = usage["prompt_tokens"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
        val completion = usage["completion_tokens"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
        if (prompt != null && completion != null) prompt to completion else null
    }.getOrNull()
}