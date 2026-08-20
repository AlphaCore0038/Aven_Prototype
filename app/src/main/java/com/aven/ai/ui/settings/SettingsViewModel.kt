package com.aven.ai.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aven.ai.AvenApplication
import com.aven.ai.di.AppContainer
import com.aven.ai.engine.local.LocalModelState
import kotlinx.coroutines.flow.StateFlow

class SettingsViewModel(private val container: AppContainer) : ViewModel() {

    val localModelState: StateFlow<LocalModelState> = container.modelManager.state

    fun installLocalModel() = container.modelManager.install()

    fun removeLocalModel() = container.modelManager.remove()

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as AvenApplication
                SettingsViewModel(app.container)
            }
        }
    }
}