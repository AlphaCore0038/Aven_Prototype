package com.aven.ai.engine.local

import com.aven.ai.engine.AiChatException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.Buffer
import okio.buffer
import okio.sink
import java.io.File

/** Streams a remote file into [target] (atomically via a `.part` rename). */
interface ModelDownloader {
    suspend fun download(url: String, target: File, onProgress: suspend (Float) -> Unit)
}

class OkHttpModelDownloader(
    private val client: OkHttpClient
) : ModelDownloader {

    override suspend fun download(url: String, target: File, onProgress: suspend (Float) -> Unit) {
        withContext(Dispatchers.IO) {
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw AiChatException("Model download failed (HTTP ${response.code}).")
                }
                val total = response.body?.contentLength() ?: -1L
                target.parentFile?.mkdirs()
                val part = File(target.parentFile, "${target.name}.part")
                part.outputStream().sink().buffer().use { sink ->
                    var downloaded = 0L
                    val source = response.body!!.source()
                    val chunk = Buffer()
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = source.read(chunk, CHUNK_SIZE)
                        if (read == -1L) break
                        sink.write(chunk, read)
                        downloaded += read
                        if (total > 0) {
                            onProgress(downloaded.toFloat() / total.toFloat())
                        }
                    }
                }
                if (!part.renameTo(target)) {
                    part.delete()
                    throw AiChatException("Could not finalize the model download.")
                }
            }
        }
    }

    private companion object {
        const val CHUNK_SIZE = 8192L
    }
}