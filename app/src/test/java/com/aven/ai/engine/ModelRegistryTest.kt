package com.aven.ai.engine

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.aven.ai.data.security.KeyManager
import com.aven.ai.data.settings.SettingsRepository
import com.aven.ai.engine.local.LocalEngine
import com.aven.ai.engine.local.ModelDownloader
import com.aven.ai.engine.local.ModelManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlinx.coroutines.runBlocking
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ModelRegistryTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Before
    @After
    fun reset() = runBlocking {
        SettingsRepository(context).clearAll()
    }

    private class NoOpDownloader : ModelDownloader {
        override suspend fun download(url: String, target: File, onProgress: suspend (Float) -> Unit) = Unit
    }

    private fun registry() = ModelRegistry(
        keyManager = KeyManager(context),
        client = OkHttpClient.Builder().build(),
        settingsRepository = SettingsRepository(context),
        modelManager = ModelManager(
            modelFile = { File(context.cacheDir, "missing.gguf") },
            downloader = NoOpDownloader(),
            scope = CoroutineScope(Dispatchers.Unconfined),
            modelLoader = { null }
        ),
        gemmaManager = ModelManager(
            modelFile = { File(context.cacheDir, "missing-gemma.gguf") },
            downloader = NoOpDownloader(),
            scope = CoroutineScope(Dispatchers.Unconfined),
            modelLoader = { null }
        )
    )

    @Test
    fun `no keys and no selection yields the unavailable engine`() = runBlocking {
        val engine = registry().defaultEngine()
        assertTrue(engine is UnavailableEngine)
    }

    @Test
    fun `unavailable engine has the honest id`() {
        assertTrue(UnavailableEngine().id == "unavailable")
    }

    @Test
    fun `option catalogue contains zen openrouter and local`() {
        val options = registry().options
        assertTrue(options.any { it.providerId == "zen" })
        assertTrue(options.any { it.providerId == "openrouter" })
        assertTrue(options.any { it.providerId == LocalEngine.PROVIDER_ID })
        assertTrue(options.filter { it.providerId != LocalEngine.PROVIDER_ID }
            .all { it.kind == EngineKind.REMOTE })
    }

    @Test
    fun `local section contains both llama and gemma`() {
        val options = registry().options.filter { it.providerId == LocalEngine.PROVIDER_ID }
        assertTrue(options.any { it.modelId == LocalEngine.MODEL_ID })
        assertTrue(options.any { it.modelId == LocalEngine.GEMMA_ID })
        assertTrue(options.size == 2)
        assertTrue(options.any { it.displayName == "Llama 3.2 1B" })
        assertTrue(options.any { it.displayName == "Gemma 1B" })
    }
}