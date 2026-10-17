package com.aven.ai.engine

import com.aven.ai.data.security.KeyManager
import com.aven.ai.data.settings.RemoteModelRepository
import com.aven.ai.data.settings.SettingsRepository
import com.aven.ai.engine.local.LocalEngine
import com.aven.ai.engine.local.LocalModelState
import com.aven.ai.engine.local.ModelManager
import com.aven.ai.engine.remote.OpenAiCompatibleEngine
import kotlinx.coroutines.flow.first
import okhttp3.OkHttpClient

enum class EngineKind { REMOTE, LOCAL }

/**
 * A selectable model. Local options are static; remote options are projected
 * from the user's [RemoteModelConfig] list, so the stable [providerId] is the
 * config UUID (never a parsed `provider/model` string).
 */
data class AiModelOption(
    val providerId: String,
    val modelId: String,
    val displayName: String,
    val description: String,
    val kind: EngineKind,
    val baseUrl: String? = null,
)

/**
 * The catalogue of models Aven can generate with, and the factory for their
 * engines. Local models are fixed; online models come from the user's saved
 * configurations. Selection is strict — the engine for the chosen option is
 * returned, and no silent fallback to another provider ever happens.
 */
class ModelRegistry(
    private val keyManager: KeyManager,
    private val client: OkHttpClient,
    private val settingsRepository: SettingsRepository,
    private val remoteModels: RemoteModelRepository,
    private val modelManager: ModelManager,
    private val gemmaManager: ModelManager
) {

    /** The two built-in on-device models. Never mixed with remote configs. */
    private val localOptions: List<AiModelOption> = listOf(
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
        )
    )

    /** Projects a saved config into the UI-facing option. */
    fun optionFor(config: RemoteModelConfig): AiModelOption = AiModelOption(
        providerId = config.id,
        modelId = config.modelId,
        displayName = formatModelDisplayName(config.modelId),
        description = config.providerName,
        kind = EngineKind.REMOTE,
        baseUrl = config.baseUrl
    )

    fun localOptions(): List<AiModelOption> = localOptions

    fun engineFor(option: AiModelOption): AiChatEngine = when (option.kind) {
        EngineKind.LOCAL -> {
            val mgr = if (option.modelId == LocalEngine.GEMMA_ID) gemmaManager else modelManager
            mgr.engine() ?: UnavailableEngine()
        }
        EngineKind.REMOTE -> {
            val config = RemoteModelConfig(
                id = option.providerId,
                providerName = option.description,
                baseUrl = option.baseUrl ?: return UnavailableEngine(),
                modelId = option.modelId
            )
            OpenAiCompatibleEngine(
                config = config,
                apiKey = { keyManager.readKey(option.providerId) },
                client = client
            )
        }
    }

    /**
     * The engine for a saved selection. Strict: only the selected model is
     * resolved; an unknown/unavailable remote selection yields the honest
     * [UnavailableEngine] rather than switching to a different provider.
     */
    suspend fun engineForSettings(providerId: String?, modelId: String?): AiChatEngine {
        if (providerId == null || modelId == null) return UnavailableEngine()
        localOptions.firstOrNull { it.providerId == providerId && it.modelId == modelId }?.let {
            return engineFor(it)
        }
        val config = remoteModels.models.first().firstOrNull { it.id == providerId }
            ?: return UnavailableEngine()
        return engineFor(optionFor(config))
    }

    suspend fun defaultEngine(): AiChatEngine {
        val settings = settingsRepository.settings.first()
        return engineForSettings(settings.defaultProviderId, settings.defaultModelId)
    }

    /** True when the option can generate right now (key present / model ready). */
    fun isAvailable(option: AiModelOption): Boolean = when (option.kind) {
        EngineKind.LOCAL -> {
            val mgr = if (option.modelId == LocalEngine.GEMMA_ID) gemmaManager else modelManager
            mgr.state.value is LocalModelState.Ready
        }
        EngineKind.REMOTE -> keyManager.hasKey(option.providerId)
    }
}