package com.aven.ai.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aven.ai.AvenApplication
import com.aven.ai.data.settings.SettingsRepository
import com.aven.ai.di.AppContainer
import com.aven.ai.engine.AiChatEngine
import com.aven.ai.engine.AiModelOption
import com.aven.ai.engine.UnavailableEngine
import com.aven.ai.engine.local.LocalModelState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ChatViewModel(private val container: AppContainer) : ViewModel() {

    private val modelRegistry = container.modelRegistry
    private val settingsRepository = container.settingsRepository

    val controller = ChatController(
        chatDao = container.chatDao,
        engineProvider = { container.engineProvider() },
        scope = viewModelScope
    )

    val models: List<AiModelOption> = modelRegistry.options

    /** The saved selection, exposed as state for the UI. */
    val settingsState: StateFlow<SettingsRepository.AppSettings> =
        settingsRepository.settings.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = SettingsRepository.AppSettings()
        )

    /** Re-evaluated whenever settings, stored keys, or the local model change. */
    val currentEngine: StateFlow<AiChatEngine> =
        combine(
            settingsRepository.settings,
            container.keyManager.changes,
            container.modelManager.state,
            container.gemmaModelManager.state
        ) { settings, _, _, _ ->
            modelRegistry.engineForSettings(settings.defaultProviderId, settings.defaultModelId)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = UnavailableEngine()
        )

    val localModelState: StateFlow<LocalModelState> = container.modelManager.state
    val gemmaModelState: StateFlow<LocalModelState> = container.gemmaModelManager.state

    fun localStateFor(modelId: String): StateFlow<LocalModelState> =
        if (modelId == com.aven.ai.engine.local.LocalEngine.GEMMA_ID) gemmaModelState else localModelState

    /** The user's stored name, used in the "Hey {name}...." welcome greeting. */
    val userName: StateFlow<String> =
        container.userProfileRepository.profile.map { it.name }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = ""
        )

    /** True when the device has a validated internet connection. */
    val isOnline: StateFlow<Boolean> = container.connectivityMonitor.isOnline

    fun installLocalModel() = container.modelManager.install()
    fun installLocalModelFor(modelId: String) = container.localManagerFor(modelId).install()

    fun cancelLocalModelDownload() = container.modelManager.cancelDownload()
    fun cancelLocalModelDownloadFor(modelId: String) = container.localManagerFor(modelId).cancelDownload()

    fun removeLocalModel() = container.modelManager.remove()
    fun removeLocalModelFor(modelId: String) = container.localManagerFor(modelId).remove()

    suspend fun selectModel(option: AiModelOption) {
        if (controller.uiState.value.isGenerating) return
        settingsRepository.setDefaultProvider(option.providerId)
        settingsRepository.setDefaultModel(option.modelId)
    }

    fun setResponseMode(mode: com.aven.ai.engine.ResponseMode) {
        viewModelScope.launch {
            settingsRepository.setResponseMode(mode.name)
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as AvenApplication
                ChatViewModel(app.container)
            }
        }
    }
}