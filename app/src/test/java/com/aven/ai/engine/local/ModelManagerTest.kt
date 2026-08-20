package com.aven.ai.engine.local

import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ModelManagerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private class FakeDownloader(
        var fail: Boolean = false,
        private val hang: Boolean = false
    ) : ModelDownloader {
        val progressCalls = mutableListOf<Float>()
        override suspend fun download(url: String, target: File, onProgress: suspend (Float) -> Unit) {
            if (hang) {
                awaitCancellation()
            }
            if (fail) {
                throw RuntimeException("boom")
            }
            onProgress(0.4f)
            progressCalls.add(0.4f)
            onProgress(1.0f)
            progressCalls.add(1.0f)
            target.parentFile?.mkdirs()
            target.writeText("dummy-model")
        }
    }

    private fun modelFile(): File = File(tempFolder.root, "models/Llama-3.2-1B-Instruct-Q4_K_M.gguf")

    private fun fakeEngine(path: String): LocalEngine? = LocalEngine(null)

    @Test
    fun `no file means not installed`() = runTest {
        val manager = ModelManager({ modelFile() }, FakeDownloader(), this, ::fakeEngine)
        assertEquals(LocalModelState.NotInstalled, manager.state.value)
        assertNull(manager.engine())
    }

    @Test
    fun `install downloads verifies and reaches ready`() = runTest {
        val downloader = FakeDownloader()
        val manager = ModelManager({ modelFile() }, downloader, this, ::fakeEngine)

        manager.install()
        testScheduler.advanceUntilIdle()

        assertEquals("reaches ready", LocalModelState.Ready, manager.state.value)
        assertNotNull("engine is available", manager.engine())
        assertTrue("model file written", modelFile().exists())
        assertTrue("progress reported", downloader.progressCalls.isNotEmpty())
    }

    @Test
    fun `download failure lands in failed state`() = runTest {
        val manager = ModelManager({ modelFile() }, FakeDownloader(fail = true), this, ::fakeEngine)

        manager.install()
        testScheduler.advanceUntilIdle()

        assertTrue(manager.state.value is LocalModelState.Failed)
        val failed = manager.state.value as LocalModelState.Failed
        assertTrue(failed.message.contains("boom"))
    }

    @Test
    fun `cancel returns to not installed`() = runTest {
        val manager = ModelManager({ modelFile() }, FakeDownloader(hang = true), this, ::fakeEngine)

        manager.install()
        testScheduler.advanceUntilIdle()
        assertTrue(manager.state.value is LocalModelState.Downloading)

        manager.cancelDownload()
        testScheduler.advanceUntilIdle()

        assertEquals(LocalModelState.NotInstalled, manager.state.value)
    }

    @Test
    fun `existing file loads immediately without downloading`() = runTest {
        modelFile().parentFile?.mkdirs()
        modelFile().writeText("dummy")
        val downloader = FakeDownloader(fail = true)
        val manager = ModelManager({ modelFile() }, downloader, this, ::fakeEngine)

        assertEquals(LocalModelState.Ready, manager.state.value)

        manager.install()
        testScheduler.advanceUntilIdle()

        assertEquals(LocalModelState.Ready, manager.state.value)
        assertNotNull(manager.engine())
    }

    @Test
    fun `unloadable model file is rejected and deleted`() = runTest {
        modelFile().parentFile?.mkdirs()
        modelFile().writeText("dummy")
        val manager = ModelManager(
            { modelFile() }, FakeDownloader(), this, { null }
        )

        assertEquals(LocalModelState.Ready, manager.state.value)
        manager.install()
        testScheduler.advanceUntilIdle()

        assertTrue(manager.state.value is LocalModelState.Failed)
        assertNull(manager.engine())
        assertTrue(!modelFile().exists())
    }

    @Test
    fun `remove deletes the model and clears the engine`() = runTest {
        val manager = ModelManager({ modelFile() }, FakeDownloader(), this, ::fakeEngine)
        manager.install()
        testScheduler.advanceUntilIdle()
        assertEquals(LocalModelState.Ready, manager.state.value)

        manager.remove()
        testScheduler.advanceUntilIdle()

        assertEquals(LocalModelState.NotInstalled, manager.state.value)
        assertNull(manager.engine())
        assertTrue(!modelFile().exists())
    }
}