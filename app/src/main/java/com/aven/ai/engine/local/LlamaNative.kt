package com.aven.ai.engine.local

/** Receives one generated text piece (typically a sub-word token) at a time. */
fun interface LlamaTokenListener {
    fun onToken(piece: String)
}

/**
 * JNI bridge to the native `aven_llama` library. Handles are opaque pointers
 * into native memory; generation is blocking and streams through
 * [LlamaTokenListener].
 */
object LlamaNative {
    init {
        System.loadLibrary("aven_llama")
    }

    external fun nativeLoad(path: String): Long
    external fun nativeUnload(handle: Long)
    external fun nativeCancel()
    external fun nativeGenerate(handle: Long, prompt: String, listener: LlamaTokenListener): String
    external fun nativeGenerateChat(
        handle: Long,
        system: String,
        historyRoles: Array<String>,
        historyContents: Array<String>,
        prompt: String,
        listener: LlamaTokenListener
    ): String
}