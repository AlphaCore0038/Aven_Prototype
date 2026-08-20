package com.aven.ai.engine.local

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

sealed interface LocalModelState {
    data object NotInstalled : LocalModelState
    data class Downloading(val progress: Float) : LocalModelState
    data object Ready : LocalModelState
    data class Failed(val message: String) : LocalModelState
}

/**
 * Owns the lifecycle of the on-device model file: install (download), verify
 * (native load), ready, and remove. The engine instance is created only after
 * the model file has been verified, so [engine] is never a broken stub.
 */
class ModelManager(
    private val modelFile: () -> File,
    private val downloader: ModelDownloader,
    private val scope: CoroutineScope,
    private val modelLoader: (String) -> LocalEngine?
) {

    private val _state = MutableStateFlow<LocalModelState>(
        if (modelFile().isFile) LocalModelState.Ready else LocalModelState.NotInstalled
    )
    val state: StateFlow<LocalModelState> = _state.asStateFlow()

    private var downloadJob: Job? = null
    private var engine: LocalEngine? = null

    fun install() {
        if (_state.value is LocalModelState.Downloading) return
        if (modelFile().isFile) {
            verifyAndReady()
            return
        }
        downloadJob = scope.launch {
            _state.update { LocalModelState.Downloading(0f) }
            try {
                downloader.download(LocalEngine.GGUF_URL, modelFile()) { progress ->
                    _state.update { LocalModelState.Downloading(progress) }
                }
                verifyAndReady()
            } catch (e: kotlinx.coroutines.CancellationException) {
                _state.value = LocalModelState.NotInstalled
                throw e
            } catch (e: Exception) {
                _state.value = LocalModelState.Failed(
                    e.message ?: "Model installation failed."
                )
            }
        }
    }

    fun cancelDownload() {
        downloadJob?.cancel()
    }

    /** The verified engine, or null when the model is not installed. */
    fun engine(): LocalEngine? = engine

    fun remove() {
        cancelDownload()
        LocalEngine.unload(engine)
        engine = null
        modelFile().delete()
        _state.value = LocalModelState.NotInstalled
    }

    private fun verifyAndReady() {
        LocalEngine.unload(engine)
        val loaded = modelLoader(modelFile().absolutePath)
        if (loaded == null) {
            modelFile().delete()
            _state.value = LocalModelState.Failed(
                "The downloaded model file could not be loaded. Please try installing again."
            )
            return
        }
        engine = loaded
        _state.value = LocalModelState.Ready
    }
}