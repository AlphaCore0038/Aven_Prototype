package com.aven.ai.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.aven.ai.engine.RemoteModelConfig
import com.aven.ai.engine.validationError
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

private val Context.remoteModelsDataStore: DataStore<Preferences> by
    preferencesDataStore(name = "aven_remote_models")

/**
 * Persists user-configured online models as JSON in DataStore. Only the
 * non-secret fields live here; API keys are stored separately in [KeyManager].
 * Enforces the [RemoteModelConfig.MAX_MODELS] limit at the single writer.
 */
class RemoteModelRepository(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(RemoteModelConfig.serializer())

    val models: Flow<List<RemoteModelConfig>> = context.remoteModelsDataStore.data.map { prefs ->
        decode(prefs[KEY_CONFIGS])
    }

    /** Adds a config. Returns false when the server-side 10-model limit is reached. */
    suspend fun add(config: RemoteModelConfig): Boolean {
        var added = false
        context.remoteModelsDataStore.edit { prefs ->
            val current = decode(prefs[KEY_CONFIGS])
            if (current.size >= RemoteModelConfig.MAX_MODELS) return@edit
            prefs[KEY_CONFIGS] = encode(current + config)
            added = true
        }
        return added
    }

    suspend fun update(config: RemoteModelConfig) {
        context.remoteModelsDataStore.edit { prefs ->
            val current = decode(prefs[KEY_CONFIGS])
            val next = current.map { if (it.id == config.id) config else it }
            prefs[KEY_CONFIGS] = encode(next)
        }
    }

    suspend fun delete(id: String) {
        context.remoteModelsDataStore.edit { prefs ->
            val next = decode(prefs[KEY_CONFIGS]).filterNot { it.id == id }
            prefs[KEY_CONFIGS] = encode(next)
        }
    }

    suspend fun clearAll() {
        context.remoteModelsDataStore.edit { it.clear() }
    }

    /** Why a config cannot be saved, or null when the fields are valid. */
    fun validate(providerName: String, baseUrl: String, modelId: String): String? =
        RemoteModelConfig(
            id = "validate",
            providerName = providerName,
            baseUrl = baseUrl,
            modelId = modelId
        ).validationError()

    private fun decode(raw: String?): List<RemoteModelConfig> =
        raw?.let { runCatching { json.decodeFromString(serializer, it) }.getOrNull() } ?: emptyList()

    private fun encode(list: List<RemoteModelConfig>): String = json.encodeToString(serializer, list)

    private companion object {
        val KEY_CONFIGS = stringPreferencesKey("configs")
    }
}