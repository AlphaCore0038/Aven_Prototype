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
import kotlinx.coroutines.flow.stateIn

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
            container.modelManager.state
        ) { settings, _, _ ->
            modelRegistry.engineForSettings(settings.defaultProviderId, settings.defaultModelId)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = UnavailableEngine()
        )

    val localModelState: StateFlow<LocalModelState> = container.modelManager.state

    fun installLocalModel() = container.modelManager.install()

    fun cancelLocalModelDownload() = container.modelManager.cancelDownload()

    fun removeLocalModel() = container.modelManager.remove()

    suspend fun selectModel(option: AiModelOption) {
        settingsRepository.setDefaultProvider(option.providerId)
        settingsRepository.setDefaultModel(option.modelId)
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