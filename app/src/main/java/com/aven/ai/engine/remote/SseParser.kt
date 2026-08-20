package com.aven.ai.engine.remote

import okio.BufferedSource

/**
 * Minimal server-sent-events parser for OpenAI-compatible streaming responses.
 * Delivers the payload of every complete "data:" event; comments, retries and
 * event/field lines are ignored.
 */
object SseParser {

    suspend fun parse(source: BufferedSource, onEvent: suspend (data: String) -> Unit) {
        var dataLines = mutableListOf<String>()
        while (true) {
            val line = source.readUtf8Line() ?: break
            val normalized = line.trimEnd('\r')
            if (normalized.isEmpty()) {
                if (dataLines.isNotEmpty()) {
                    onEvent(dataLines.joinToString("\n"))
                    dataLines = mutableListOf()
                }
            } else if (normalized.startsWith("data:")) {
                dataLines.add(normalized.removePrefix("data:").trimStart())
            }
        }
        if (dataLines.isNotEmpty()) {
            onEvent(dataLines.joinToString("\n"))
        }
    }
}