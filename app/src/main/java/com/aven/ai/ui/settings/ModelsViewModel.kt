package com.aven.ai.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aven.ai.AvenApplication
import com.aven.ai.data.settings.SettingsRepository
import com.aven.ai.di.AppContainer
import com.aven.ai.engine.RemoteModelConfig
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Backs Settings → Online models: the configured list, per-model key status,
 * and the saved default selection, all reactive.
 */
class ModelsViewModel(private val container: AppContainer) : ViewModel() {

    data class ModelRow(
        val config: RemoteModelConfig,
        val hasKey: Boolean,
        val isDefault: Boolean
    )

    val rows: StateFlow<List<ModelRow>> = combine(
        container.remoteModelRepository.models,
        container.keyManager.changes.onStart { emit(Unit) },
        container.settingsRepository.settings.onStart { emit(SettingsRepository.AppSettings()) }
    ) { configs, _, settings ->
        configs.map { config ->
            ModelRow(
                config = config,
                hasKey = container.keyManager.hasKey(config.id),
                isDefault = settings.defaultProviderId == config.id
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = emptyList()
    )

    val maxModels: Int get() = RemoteModelConfig.MAX_MODELS

    fun hasKey(id: String): Boolean = container.keyManager.hasKey(id)

    fun readKey(id: String): String? = container.keyManager.readKey(id)

    fun validate(providerName: String, baseUrl: String, modelId: String): String? =
        container.remoteModelRepository.validate(providerName, baseUrl, modelId)

    /** Saves a new model + its key. Returns false when the 10-model limit is reached. */
    fun save(providerName: String, baseUrl: String, modelId: String, apiKey: String, onSaved: (Boolean) -> Unit) {
        viewModelScope.launch {
            val config = RemoteModelConfig(
                id = java.util.UUID.randomUUID().toString(),
                providerName = providerName.trim(),
                baseUrl = baseUrl.trim(),
                modelId = modelId.trim()
            )
            val added = container.remoteModelRepository.add(config)
            if (added && apiKey.isNotBlank()) {
                container.keyManager.writeKey(config.id, apiKey.trim())
            }
            onSaved(added)
        }
    }

    /** Updates an existing model; blank [apiKey] keeps the stored key. */
    fun update(config: RemoteModelConfig, apiKey: String) {
        viewModelScope.launch {
            container.remoteModelRepository.update(config)
            if (apiKey.isNotBlank()) {
                container.keyManager.writeKey(config.id, apiKey.trim())
            }
            // Keep the saved default in sync when the selected model's modelId changes.
            val settings = container.settingsRepository.settings.first()
            if (settings.defaultProviderId == config.id &&
                settings.defaultModelId != config.modelId
            ) {
                container.settingsRepository.setDefaultModel(config.modelId)
            }
        }
    }

    fun delete(id: String) {
        viewModelScope.launch {
            container.remoteModelRepository.delete(id)
            container.keyManager.clearKey(id)
            // If the deleted model was the saved default, clear the selection
            // explicitly instead of letting it resolve to another provider.
            if (container.settingsRepository.settings.first().defaultProviderId == id) {
                container.settingsRepository.clearDefaultSelection()
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as AvenApplication
                ModelsViewModel(app.container)
            }
        }
    }
}