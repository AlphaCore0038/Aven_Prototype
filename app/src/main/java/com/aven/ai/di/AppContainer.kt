package com.aven.ai.di

import android.content.Context
import com.aven.ai.data.local.AvenDatabase
import com.aven.ai.data.network.ConnectivityMonitor
import com.aven.ai.data.network.HttpClientFactory
import com.aven.ai.data.security.KeyManager
import com.aven.ai.data.settings.RemoteModelRepository
import com.aven.ai.data.settings.SettingsRepository
import com.aven.ai.data.settings.UserProfileRepository
import com.aven.ai.engine.AiChatEngine
import com.aven.ai.engine.ModelRegistry
import com.aven.ai.engine.local.LocalEngine
import com.aven.ai.engine.local.LocalModelState
import com.aven.ai.engine.local.ModelDownloader
import com.aven.ai.engine.local.ModelManager
import com.aven.ai.engine.local.OkHttpModelDownloader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Manual dependency-injection container (no DI framework).
 *
 * Data layer: Room database, secure key store (Keystore-backed), and the
 * DataStore-backed settings/profile repositories. The AI layer (model manager,
 * provider registry, chat controller) is registered in Phase 3+.
 */
class AppContainer(context: Context) {

    val applicationContext: Context = context.applicationContext

    val database: AvenDatabase by lazy { AvenDatabase.getInstance(applicationContext) }

    val chatDao by lazy { database.chatDao() }

    val keyManager: KeyManager by lazy { KeyManager(applicationContext) }

    val settingsRepository: SettingsRepository by lazy { SettingsRepository(applicationContext) }

    /** User-configured online models (non-secret fields; keys live in KeyManager). */
    val remoteModelRepository: RemoteModelRepository by lazy { RemoteModelRepository(applicationContext) }

    val userProfileRepository: UserProfileRepository by lazy { UserProfileRepository(applicationContext) }

    val httpClient: OkHttpClient by lazy { HttpClientFactory.create() }

    /** Real internet connectivity of the device (validated network). */
    val connectivityMonitor: ConnectivityMonitor by lazy {
        ConnectivityMonitor(applicationContext)
    }

    private val applicationScope: CoroutineScope by lazy {
        CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }

    /** The on-device model lifecycle: download, verify, load, remove — the primary local model. */
    val modelManager: ModelManager by lazy {
        ModelManager(
            modelFile = { File(applicationContext.filesDir, "models/${LocalEngine.MODEL_FILE_NAME}") },
            downloader = httpDownloader,
            scope = applicationScope,
            modelLoader = { path -> LocalEngine.load(path, LocalEngine.MODEL_ID) },
            ggufUrl = LocalEngine.GGUF_URL,
            isSelected = {
                runCatching { runBlocking { settingsRepository.settings.first().defaultModelId } }
                    .getOrNull().let { it == null || it == LocalEngine.MODEL_ID }
            }
        )
    }

    /** Gemma 3 1B — same infrastructure, separate file, never resident together with the primary model. */
    val gemmaModelManager: ModelManager by lazy {
        ModelManager(
            modelFile = { File(applicationContext.filesDir, "models/${LocalEngine.GEMMA_FILE_NAME}") },
            downloader = httpDownloader,
            scope = applicationScope,
            modelLoader = { path -> LocalEngine.load(path, LocalEngine.GEMMA_ID) },
            ggufUrl = LocalEngine.GEMMA_URL,
            isSelected = {
                runCatching { runBlocking { settingsRepository.settings.first().defaultModelId } }
                    .getOrNull() == LocalEngine.GEMMA_ID
            }
        )
    }

    fun localManagerFor(modelId: String?): ModelManager = when (modelId) {
        LocalEngine.GEMMA_ID -> gemmaModelManager
        else -> modelManager
    }

    /**
     * Downloads never hit the API call timeout (a >3 min GGUF stream would be
     * killed mid-transfer). Connect/read/write timeouts still apply.
     */
    private val httpDownloader: ModelDownloader by lazy {
        OkHttpModelDownloader(
            httpClient.newBuilder().callTimeout(0, TimeUnit.MILLISECONDS).build()
        )
    }

    val modelRegistry: ModelRegistry by lazy {
        ModelRegistry(
            keyManager = keyManager,
            client = httpClient,
            settingsRepository = settingsRepository,
            remoteModels = remoteModelRepository,
            modelManager = modelManager,
            gemmaManager = gemmaModelManager
        )
    }

    init {
        // One-time: stored legacy selections (Llama 3.2, then the Qwen 2.5
        // replacement) migrate to the Dolphin default. Explicit Gemma/remote
        // choices are preserved; model files on disk are never deleted.
        applicationScope.launch {
            runCatching {
                val stored = settingsRepository.settings.first().defaultModelId
                if (stored == LocalEngine.LEGACY_MODEL_ID || stored == LocalEngine.QWEN_LEGACY_MODEL_ID) {
                    settingsRepository.setDefaultModel(LocalEngine.MODEL_ID)
                }
            }
        }

        // Keep only the selected local model resident in RAM.
        applicationScope.launch {
            settingsRepository.settings
                .map { it.defaultModelId }
                .distinctUntilChanged()
                .collect { selectedId ->
                    val isGemma = selectedId == LocalEngine.GEMMA_ID
                    val selected = if (isGemma) gemmaModelManager else modelManager
                    val other = if (isGemma) modelManager else gemmaModelManager
                    if (other.engine() != null) other.unloadEngine()
                    if (selected.isInstalled() &&
                        (selected.state.value is LocalModelState.NotInstalled ||
                            selected.state.value is LocalModelState.Downloaded)
                    ) {
                        selected.ensureReady()
                    }
                }
        }

        // First-run avatar colour: assign-once, atomic, never overwrites a stored choice.
        applicationScope.launch {
            userProfileRepository.ensureAvatarColorAssigned()
        }
    }

    /**
     * Resolves the engine for the current selection and key state at request
     * time, so key edits and model switches take effect on the next send.
     */
    val engineProvider: suspend () -> AiChatEngine = { modelRegistry.defaultEngine() }
}