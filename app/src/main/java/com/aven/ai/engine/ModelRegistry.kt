package com.aven.ai.engine

import com.aven.ai.data.security.KeyManager
import com.aven.ai.data.settings.SettingsRepository
import com.aven.ai.engine.local.LocalEngine
import com.aven.ai.engine.local.LocalModelState
import com.aven.ai.engine.local.ModelManager
import com.aven.ai.engine.remote.NimEngine
import com.aven.ai.engine.remote.OpenRouterEngine
import com.aven.ai.engine.remote.ZaiEngine
import com.aven.ai.engine.remote.ZenEngine
import kotlinx.coroutines.flow.first
import okhttp3.OkHttpClient

enum class EngineKind { REMOTE, LOCAL }

data class AiModelOption(
    val providerId: String,
    val modelId: String,
    val displayName: String,
    val description: String,
    val kind: EngineKind,
)

/**
 * The catalogue of models Aven can generate with, and the factory for their
 * engines. Selection is explicit — the engine for the user's chosen option is
 * returned, and no silent fallback to another provider ever happens.
 */
class ModelRegistry(
    private val keyManager: KeyManager,
    private val client: OkHttpClient,
    private val settingsRepository: SettingsRepository,
    private val modelManager: ModelManager,
    private val gemmaManager: ModelManager
) {

    val options: List<AiModelOption> = listOf(
        AiModelOption(
            providerId = LocalEngine.PROVIDER_ID,
            modelId = LocalEngine.MODEL_ID,
            displayName = "Llama 3.2 1B",
            description = "On-device — private, offline, no keys",
            kind = EngineKind.LOCAL
        ),
        AiModelOption(
            providerId = LocalEngine.PROVIDER_ID,
            modelId = LocalEngine.GEMMA_ID,
            displayName = "Gemma 1B",
            description = "Google Gemma 3 1B — Local",
            kind = EngineKind.LOCAL
        ),
        AiModelOption(
            providerId = ZenEngine.PROVIDER_ID,
            modelId = "big-pickle",
            displayName = "Big Pickle",
            description = "OpenCode Zen — free",
            kind = EngineKind.REMOTE
        ),
        AiModelOption(
            providerId = ZaiEngine.PROVIDER_ID,
            modelId = ZaiEngine.DEFAULT_MODEL,
            displayName = "GLM-4.7 Flash",
            description = "Z.AI — fast flash model",
            kind = EngineKind.REMOTE
        ),
        AiModelOption(
            providerId = OpenRouterEngine.PROVIDER_ID,
            modelId = "nvidia/nemotron-3-ultra-550b-a55b:free",
            displayName = "Nemotron 3 Ultra",
            description = "OpenRouter — NVIDIA reasoning, free",
            kind = EngineKind.REMOTE
        ),
        AiModelOption(
            providerId = NimEngine.PROVIDER_ID,
            modelId = "openai/gpt-oss-20b",
            displayName = "GPT OSS 20B",
            description = "NVIDIA NIM — OpenAI open weights",
            kind = EngineKind.REMOTE
        ),
        AiModelOption(
            providerId = NimEngine.PROVIDER_ID,
            modelId = "openai/gpt-oss-120b",
            displayName = "GPT OSS 120B",
            description = "NVIDIA NIM — OpenAI open weights",
            kind = EngineKind.REMOTE
        ),
        AiModelOption(
            providerId = NimEngine.PROVIDER_ID,
            modelId = "moonshotai/kimi-k3",
            displayName = "Kimi K3",
            description = "NVIDIA NIM — Moonshot AI",
            kind = EngineKind.REMOTE
        ),
        AiModelOption(
            providerId = NimEngine.PROVIDER_ID,
            modelId = "deepseek-ai/deepseek-v4-pro-0813",
            displayName = "V4 Pro",
            description = "NVIDIA NIM — DeepSeek",
            kind = EngineKind.REMOTE
        ),
        AiModelOption(
            providerId = NimEngine.PROVIDER_ID,
            modelId = "deepseek-ai/deepseek-v4-flash-0731",
            displayName = "V4 Flash",
            description = "NVIDIA NIM — DeepSeek",
            kind = EngineKind.REMOTE
        ),
        AiModelOption(
            providerId = NimEngine.PROVIDER_ID,
            modelId = "z-ai/glm-5.2",
            displayName = "GLM-5.2",
            description = "NVIDIA NIM — Z.AI",
            kind = EngineKind.REMOTE
        )
    )

    fun engineFor(option: AiModelOption): AiChatEngine = when (option.providerId) {
        LocalEngine.PROVIDER_ID -> {
            val mgr = if (option.modelId == LocalEngine.GEMMA_ID) gemmaManager else modelManager
            mgr.engine() ?: UnavailableEngine()
        }
        ZenEngine.PROVIDER_ID -> ZenEngine(
            apiKey = { keyManager.readKey(KeyManager.Provider.ZEN) },
            client = client,
            modelId = option.modelId,
            engineDisplayName = option.displayName
        )
        OpenRouterEngine.PROVIDER_ID -> OpenRouterEngine(
            apiKey = { keyManager.readKey(KeyManager.Provider.OPENROUTER) },
            client = client,
            modelId = option.modelId
        )
        NimEngine.PROVIDER_ID -> NimEngine(
            apiKey = { keyManager.readKey(KeyManager.Provider.NVIDIA_NIM) },
            client = client,
            modelId = option.modelId
        )
        ZaiEngine.PROVIDER_ID -> ZaiEngine(
            apiKey = { keyManager.readKey(KeyManager.Provider.ZAI) },
            client = client,
            modelId = option.modelId
        )
        else -> UnavailableEngine()
    }

    /**
     * The engine for a saved selection, with the first available option as
     * fallback. When nothing is configured the honest [UnavailableEngine] is
     * returned. Never switches provider silently once a selection exists.
     */
    fun engineForSettings(providerId: String?, modelId: String?): AiChatEngine {
        val saved = options.firstOrNull {
            (it.providerId == providerId) && (it.modelId == modelId)
        }
        if (saved != null) {
            if (isAvailable(saved)) return engineFor(saved)
            if (saved.kind == EngineKind.LOCAL) return UnavailableEngine()
        }

        options.firstOrNull { isAvailable(it) }?.let { return engineFor(it) }
        return UnavailableEngine()
    }

    suspend fun defaultEngine(): AiChatEngine {
        val settings = settingsRepository.settings.first()
        return engineForSettings(settings.defaultProviderId, settings.defaultModelId)
    }

    /** True when the option can generate right now (key present / model ready). */
    fun isAvailable(option: AiModelOption): Boolean = when (option.providerId) {
        LocalEngine.PROVIDER_ID -> {
            val mgr = if (option.modelId == LocalEngine.GEMMA_ID) gemmaManager else modelManager
            mgr.state.value is LocalModelState.Ready
        }
        ZenEngine.PROVIDER_ID -> keyManager.hasKey(KeyManager.Provider.ZEN)
        OpenRouterEngine.PROVIDER_ID -> keyManager.hasKey(KeyManager.Provider.OPENROUTER)
        NimEngine.PROVIDER_ID -> keyManager.hasKey(KeyManager.Provider.NVIDIA_NIM)
        ZaiEngine.PROVIDER_ID -> keyManager.hasKey(KeyManager.Provider.ZAI)
        else -> false
    }
}