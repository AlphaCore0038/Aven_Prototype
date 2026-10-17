package com.aven.ai.engine

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.aven.ai.data.security.KeyManager
import com.aven.ai.data.settings.RemoteModelRepository
import com.aven.ai.data.settings.SettingsRepository
import com.aven.ai.engine.RemoteModelConfig
import com.aven.ai.engine.local.LocalEngine
import com.aven.ai.engine.local.ModelDownloader
import com.aven.ai.engine.local.ModelManager
import com.aven.ai.engine.remote.OpenAiCompatibleEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ModelRegistryTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Before
    @After
    fun reset() = runBlocking {
        SettingsRepository(context).clearAll()
        RemoteModelRepository(context).clearAll()
    }

    private class NoOpDownloader : ModelDownloader {
        override suspend fun download(url: String, target: File, onProgress: suspend (Float) -> Unit) = Unit
    }

    private fun registry() = ModelRegistry(
        keyManager = KeyManager(context),
        client = OkHttpClient.Builder().build(),
        settingsRepository = SettingsRepository(context),
        remoteModels = RemoteModelRepository(context),
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

    private fun config(
        id: String = "cfg-1",
        providerName: String = "OpenRouter",
        baseUrl: String = "https://openrouter.ai/api/v1",
        modelId: String = "some/model"
    ) = RemoteModelConfig(id, providerName, baseUrl, modelId)

    @Test
    fun `fresh install has zero remote models and only local options`() = runBlocking {
        val r = registry()
        assertEquals(0, RemoteModelRepository(context).models.first().size)
        assertEquals(2, r.localOptions().size)
        assertTrue(r.localOptions().all { it.kind == EngineKind.LOCAL })
        assertTrue(r.defaultEngine() is UnavailableEngine)
    }

    @Test
    fun `adding a config exposes it as a selectable remote option`() = runBlocking {
        val r = registry()
        RemoteModelRepository(context).add(config())
        assertEquals(1, RemoteModelRepository(context).models.first().size)
        val option = r.optionFor(config())
        assertEquals("cfg-1", option.providerId)
        assertEquals("some/model", option.modelId)
        assertEquals("https://openrouter.ai/api/v1", option.baseUrl)
        assertEquals(EngineKind.REMOTE, option.kind)
    }

    @Test
    fun `model limit is enforced at the repository`() = runBlocking {
        val repo = RemoteModelRepository(context)
        for (i in 1..10) {
            assertTrue(repo.add(config(id = "cfg-$i")))
        }
        assertFalse("11th model must be rejected", repo.add(config(id = "cfg-11")))
        assertEquals(10, repo.models.first().size)
    }

    @Test
    fun `duplicate gateway and model entries are allowed`() = runBlocking {
        val repo = RemoteModelRepository(context)
        assertTrue(repo.add(config(id = "cfg-a")))
        assertTrue(repo.add(config(id = "cfg-b")))
        assertEquals(2, repo.models.first().size)
    }

    @Test
    fun `editing keeps the stable id and updates fields`() = runBlocking {
        val repo = RemoteModelRepository(context)
        repo.add(config())
        repo.update(config(modelId = "new/model"))
        val saved = repo.models.first().single()
        assertEquals("cfg-1", saved.id)
        assertEquals("new/model", saved.modelId)
    }

    @Test
    fun `deleting removes the config`() = runBlocking {
        val repo = RemoteModelRepository(context)
        repo.add(config())
        repo.delete("cfg-1")
        assertEquals(0, repo.models.first().size)
    }

    @Test
    fun `selected remote model resolves to its own engine with no fallback`() = runBlocking {
        val r = registry()
        val repo = RemoteModelRepository(context)
        repo.add(config(id = "cfg-a"))
        repo.add(config(id = "cfg-b", modelId = "other/model"))
        val engine = r.engineForSettings("cfg-a", "some/model")
        assertTrue(engine is OpenAiCompatibleEngine)
        assertEquals("cfg-a", engine.providerId)
        assertEquals("some/model", engine.modelId)
    }

    @Test
    fun `unknown selection yields the unavailable engine instead of a silent switch`() = runBlocking {
        val r = registry()
        RemoteModelRepository(context).add(config())
        assertTrue(r.engineForSettings("zen", "big-pickle") is UnavailableEngine)
        assertTrue(r.engineForSettings(null, null) is UnavailableEngine)
        assertTrue(r.engineForSettings("missing-id", "some/model") is UnavailableEngine)
    }

    @Test
    fun `unavailable engine has the honest id`() {
        assertTrue(UnavailableEngine().id == "unavailable")
    }

    @Test
    fun `local section contains both qwen and gemma`() {
        val options = registry().localOptions()
        assertTrue(options.any { it.modelId == LocalEngine.MODEL_ID })
        assertTrue(options.any { it.modelId == LocalEngine.GEMMA_ID })
        assertEquals(2, options.size)
        assertTrue(options.any { it.displayName == "Qwen 2.5 1.5B Uncensored" })
        assertTrue(options.any { it.displayName == "Gemma 1B" })
        assertFalse(
            "the replaced Llama entry must be gone",
            options.any { it.modelId == LocalEngine.LEGACY_MODEL_ID }
        )
        assertEquals("~1 GB", options.first { it.modelId == LocalEngine.MODEL_ID }.sizeLabel)
        assertEquals("~0.8 GB", options.first { it.modelId == LocalEngine.GEMMA_ID }.sizeLabel)
    }

    @Test
    @Ignore("AndroidKeyStore unavailable under Robolectric — NOT VERIFIED, REQUIRES DEVICE")
    fun `remote availability requires the per-model key`() = runBlocking {
        val r = registry()
        val repo = RemoteModelRepository(context)
        repo.add(config(id = "cfg-a"))
        val option = r.optionFor(config(id = "cfg-a"))
        assertFalse(r.isAvailable(option))
        KeyManager(context).writeKey("cfg-a", "k")
        assertTrue(r.isAvailable(option))
    }
}