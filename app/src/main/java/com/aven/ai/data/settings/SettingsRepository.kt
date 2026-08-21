package com.aven.ai.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "aven_settings")

/** App-wide preferences: appearance, default AI selection. */
class SettingsRepository(private val context: Context) {

    data class AppSettings(
        val theme: String = THEME_DARK,
        val defaultProviderId: String? = null,
        val defaultModelId: String? = null
    ) {
        companion object {
            const val THEME_SYSTEM = "system"
            const val THEME_DARK = "dark"
            const val THEME_LIGHT = "light"
        }
    }

    val settings: Flow<AppSettings> = context.settingsDataStore.data.map { prefs ->
        AppSettings(
            theme = prefs[KEY_THEME] ?: AppSettings.THEME_DARK,
            defaultProviderId = prefs[KEY_DEFAULT_PROVIDER],
            defaultModelId = prefs[KEY_DEFAULT_MODEL]
        )
    }

    suspend fun setTheme(theme: String) {
        context.settingsDataStore.edit { it[KEY_THEME] = theme }
    }

    suspend fun setDefaultProvider(providerId: String) {
        context.settingsDataStore.edit { it[KEY_DEFAULT_PROVIDER] = providerId }
    }

    suspend fun setDefaultModel(modelId: String) {
        context.settingsDataStore.edit { it[KEY_DEFAULT_MODEL] = modelId }
    }

    /** Wipes all settings. Used by tests and a future "Reset settings" action. */
    suspend fun clearAll() {
        context.settingsDataStore.edit { it.clear() }
    }

    private companion object {
        val KEY_THEME = stringPreferencesKey("theme")
        val KEY_DEFAULT_PROVIDER = stringPreferencesKey("default_provider_id")
        val KEY_DEFAULT_MODEL = stringPreferencesKey("default_model_id")
    }
}