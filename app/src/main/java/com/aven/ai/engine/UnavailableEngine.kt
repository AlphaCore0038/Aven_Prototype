package com.aven.ai.engine

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * The engine state before any real backend exists. Generation fails with an
 * honest, user-presentable error instead of faking output. Replaced by the
 * real engines in Phase 4 (remote) and Phase 5 (local llama.cpp).
 */
class UnavailableEngine : AiChatEngine {
    override val id = "unavailable"
    override val displayName = "No AI engine connected"

    override fun generate(request: GenerateRequest): Flow<String> = flow {
        throw AiChatException("Aven's AI isn't connected yet.")
    }
}