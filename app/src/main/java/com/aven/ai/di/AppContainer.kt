package com.aven.ai.di

import android.content.Context
import com.aven.ai.data.local.AvenDatabase
import com.aven.ai.data.network.HttpClientFactory
import com.aven.ai.data.security.KeyManager
import com.aven.ai.data.settings.SettingsRepository
import com.aven.ai.data.settings.UserProfileRepository
import com.aven.ai.engine.AiChatEngine
import com.aven.ai.engine.ModelRegistry
import com.aven.ai.engine.local.LocalEngine
import com.aven.ai.engine.local.ModelDownloader
import com.aven.ai.engine.local.ModelManager
import com.aven.ai.engine.local.OkHttpModelDownloader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient
import java.io.File

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

    val userProfileRepository: UserProfileRepository by lazy { UserProfileRepository(applicationContext) }

    val httpClient: OkHttpClient by lazy { HttpClientFactory.create() }

    private val applicationScope: CoroutineScope by lazy {
        CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }

    /** The on-device model lifecycle: download, verify, load, remove. */
    val modelManager: ModelManager by lazy {
        ModelManager(
            modelFile = { File(applicationContext.filesDir, "models/${LocalEngine.MODEL_FILE_NAME}") },
            downloader = httpDownloader,
            scope = applicationScope,
            modelLoader = { path -> LocalEngine.load(path) }
        )
    }

    private val httpDownloader: ModelDownloader by lazy { OkHttpModelDownloader(httpClient) }

    val modelRegistry: ModelRegistry by lazy {
        ModelRegistry(keyManager, httpClient, settingsRepository, modelManager)
    }

    /**
     * Resolves the engine for the current selection and key state at request
     * time, so key edits and model switches take effect on the next send.
     */
    val engineProvider: suspend () -> AiChatEngine = { modelRegistry.defaultEngine() }
}