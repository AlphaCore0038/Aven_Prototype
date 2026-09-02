package com.aven.ai.engine.local

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

sealed interface LocalModelState {
    data object NotInstalled : LocalModelState
    data object Loading : LocalModelState
    data class Downloading(val progress: Float) : LocalModelState
    data object Ready : LocalModelState
    data class Failed(val message: String) : LocalModelState
}

/**
 * Owns the lifecycle of the on-device model file: install (download), verify
 * (native load), ready, and remove. The engine instance is created only after
 * the model file has been verified, so [engine] is never a broken stub.
 *
 * A file that exists on disk is [LocalModelState.Loading] until the native
 * load succeeds — never silently [LocalModelState.Ready].
 */
class ModelManager(
    private val modelFile: () -> File,
    private val downloader: ModelDownloader,
    private val scope: CoroutineScope,
    private val modelLoader: (String) -> LocalEngine?,
    private val ggufUrl: String = LocalEngine.GGUF_URL,
    private val isSelected: () -> Boolean = { true }
) {

    private val _state = MutableStateFlow<LocalModelState>(LocalModelState.NotInstalled)
    val state: StateFlow<LocalModelState> = _state.asStateFlow()

    private var downloadJob: Job? = null
    private var loadJob: Job? = null
    private var engine: LocalEngine? = null

    init {
        if (modelFile().isFile && isSelected()) {
            _state.value = LocalModelState.Loading
            // Load the engine instance on a background thread so we don't block
            // the lazy initialization of the container.
            loadJob = scope.launch {
                verifyAndReady()
            }
        }
    }

    fun install() {
        if (_state.value is LocalModelState.Downloading || _state.value is LocalModelState.Loading) return
        if (modelFile().isFile) {
            loadJob = scope.launch { verifyAndReady() }
            return
        }
        var lastEmitted = -1f
        downloadJob = scope.launch {
            _state.update { LocalModelState.Downloading(0f) }
            try {
                downloader.download(ggufUrl, modelFile()) { progress ->
                    // Throttle emissions: the downloader reports per-chunk and a
                    // recomposition per 8 KB would hammer the UI thread.
                    if (progress - lastEmitted >= PROGRESS_STEP || progress >= 1f) {
                        lastEmitted = progress
                        _state.update { LocalModelState.Downloading(progress) }
                    }
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
        loadJob?.cancel()
        loadJob = null
        LocalEngine.unload(engine)
        engine = null
        modelFile().delete()
        _state.value = LocalModelState.NotInstalled
    }

    /** Unloads the native engine to free RAM but keeps the file on disk. */
    fun unloadEngine() {
        loadJob?.cancel()
        loadJob = null
        LocalEngine.unload(engine)
        engine = null
        if (_state.value is LocalModelState.Ready || _state.value is LocalModelState.Loading) {
            _state.value = LocalModelState.NotInstalled
        }
    }

    fun isInstalled(): Boolean = modelFile().isFile

    fun ensureReady() {
        if (_state.value is LocalModelState.Downloading || _state.value is LocalModelState.Loading) return
        if (!modelFile().isFile) return
        _state.value = LocalModelState.Loading
        loadJob = scope.launch { verifyAndReady() }
    }

    /**
     * Loads the model file natively and transitions to Ready/Failed. Runs
     * inside its own job ([loadJob]) so a concurrent [remove] cancels it and
     * can never observe a resurrected Ready state afterwards.
     */
    private fun CoroutineScope.verifyAndReady() {
        LocalEngine.unload(engine)
        engine = null
        _state.value = LocalModelState.Loading
        val loaded = modelLoader(modelFile().absolutePath)
        ensureActive()
        if (loaded == null) {
            // Keep the downloaded file: a load failure can be transient (low
            // memory, cold start). install() retries verification without a
            // new 800 MB download.
            _state.value = LocalModelState.Failed(
                "The local model could not be loaded on this device. Tap retry to try again."
            )
            return
        }
        engine = loaded
        _state.value = LocalModelState.Ready
    }

    private companion object {
        /** Minimum progress delta between two UI-facing emissions. */
        const val PROGRESS_STEP = 0.005f
    }
}