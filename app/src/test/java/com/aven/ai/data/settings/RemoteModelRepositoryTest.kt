package com.aven.ai.data.settings

import androidx.test.core.app.ApplicationProvider
import com.aven.ai.engine.RemoteModelConfig
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RemoteModelRepositoryTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Before
    fun reset() = runTest {
        RemoteModelRepository(context).clearAll()
    }

    private fun config(
        id: String = "cfg-1",
        providerName: String = "OpenRouter",
        baseUrl: String = "https://openrouter.ai/api/v1",
        modelId: String = "some/model"
    ) = RemoteModelConfig(id, providerName, baseUrl, modelId)

    @Test
    fun `zero configured models by default`() = runTest {
        assertEquals(0, RemoteModelRepository(context).models.first().size)
    }

    @Test
    fun `adding one model persists it`() = runTest {
        val repo = RemoteModelRepository(context)
        assertTrue(repo.add(config()))
        val saved = repo.models.first().single()
        assertEquals("cfg-1", saved.id)
        assertEquals("OpenRouter", saved.providerName)
        assertEquals("https://openrouter.ai/api/v1", saved.baseUrl)
        assertEquals("some/model", saved.modelId)
    }

    @Test
    fun `adding multiple models keeps all of them`() = runTest {
        val repo = RemoteModelRepository(context)
        for (i in 1..5) {
            assertTrue(repo.add(config(id = "cfg-$i", modelId = "m/$i")))
        }
        assertEquals(5, repo.models.first().size)
    }

    @Test
    fun `tenth model is accepted and eleventh is rejected`() = runTest {
        val repo = RemoteModelRepository(context)
        for (i in 1..10) assertTrue(repo.add(config(id = "cfg-$i")))
        assertFalse(repo.add(config(id = "cfg-11")))
        assertEquals(10, repo.models.first().size)
    }

    @Test
    fun `same provider and credentials may be added twice`() = runTest {
        val repo = RemoteModelRepository(context)
        assertTrue(repo.add(config()))
        assertTrue(repo.add(config(id = "cfg-2")))
        assertEquals(2, repo.models.first().size)
    }

    @Test
    fun `editing updates fields and keeps the stable id`() = runTest {
        val repo = RemoteModelRepository(context)
        repo.add(config())
        repo.update(config(providerName = "My Server", baseUrl = "https://x.example/v1", modelId = "qwen/7b"))
        val saved = repo.models.first().single()
        assertEquals("cfg-1", saved.id)
        assertEquals("My Server", saved.providerName)
        assertEquals("https://x.example/v1", saved.baseUrl)
        assertEquals("qwen/7b", saved.modelId)
    }

    @Test
    fun `deleting removes only the targeted model`() = runTest {
        val repo = RemoteModelRepository(context)
        repo.add(config(id = "cfg-a"))
        repo.add(config(id = "cfg-b"))
        repo.delete("cfg-a")
        val remaining = repo.models.first()
        assertEquals(1, remaining.size)
        assertEquals("cfg-b", remaining.single().id)
    }

    @Test
    fun `models survive a new repository instance (restart)`() = runTest {
        RemoteModelRepository(context).add(config())
        val fresh = RemoteModelRepository(context)
        assertEquals(listOf(config()), fresh.models.first())
    }

    @Test
    fun `clearAll wipes everything`() = runTest {
        val repo = RemoteModelRepository(context)
        repo.add(config())
        repo.clearAll()
        assertEquals(0, repo.models.first().size)
    }

    @Test
    fun `validation rejects missing fields and malformed urls`() {
        val repo = RemoteModelRepository(context)
        assertNull(repo.validate("P", "https://ok.example/v1", "m"))
        assertNull(repo.validate("P", "http://ok.example/v1", "m"))
        assertEquals("Enter a provider name.", repo.validate("", "https://ok.example/v1", "m"))
        assertEquals("Enter the base gateway URL.", repo.validate("P", "  ", "m"))
        assertEquals("Enter a valid http(s) gateway URL.", repo.validate("P", "not-a-url", "m"))
        assertEquals("Enter a valid http(s) gateway URL.", repo.validate("P", "ftp://x.example", "m"))
        assertEquals("Enter the model ID.", repo.validate("P", "https://ok.example/v1", ""))
    }
}