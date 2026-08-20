package com.aven.ai.data.security

import android.content.Context
import com.aven.ai.BuildConfig
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/**
 * Secure storage for API keys. Values are AES-GCM encrypted under a
 * Keystore-held master key and persisted in private SharedPreferences.
 *
 * Debug builds seed their defaults from BuildConfig (sourced from the
 * gitignored local.properties) on first launch. Release builds never carry
 * defaults, so seeding is a no-op there.
 */
class KeyManager(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )
    private val crypto = KeystoreCrypto()

    /** Emitted whenever a key is written, cleared, or seeded. */
    private val _changes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val changes: SharedFlow<Unit> = _changes

    /**
     * Seeds the debug default keys if they are missing. Called on app start.
     */
    fun seedDefaultKeysIfNeeded() {
        var seededAny = false

        if (BuildConfig.DEBUG && BuildConfig.DEFAULT_ZEN_API_KEY.isNotBlank() && !hasKey(Provider.ZEN)) {
            writeKey(Provider.ZEN, BuildConfig.DEFAULT_ZEN_API_KEY)
            seededAny = true
        }
        if (BuildConfig.DEBUG && BuildConfig.DEFAULT_OPENROUTER_API_KEY.isNotBlank() && !hasKey(Provider.OPENROUTER)) {
            writeKey(Provider.OPENROUTER, BuildConfig.DEFAULT_OPENROUTER_API_KEY)
            seededAny = true
        }
        if (BuildConfig.DEBUG && BuildConfig.DEFAULT_ZAI_API_KEY.isNotBlank() && !hasKey(Provider.ZAI)) {
            writeKey(Provider.ZAI, BuildConfig.DEFAULT_ZAI_API_KEY)
            seededAny = true
        }

        if (seededAny) {
            prefs.edit().putBoolean(PREF_SEEDED, true).apply()
            _changes.tryEmit(Unit)
        }
    }

    fun writeKey(provider: Provider, key: String) {
        prefs.edit().putString(prefKey(provider), crypto.encrypt(key)).apply()
        _changes.tryEmit(Unit)
    }

    fun readKey(provider: Provider): String? {
        val payload = prefs.getString(prefKey(provider), null) ?: return null
        return runCatching { crypto.decrypt(payload) }.getOrNull()
    }

    fun hasKey(provider: Provider): Boolean = prefs.contains(prefKey(provider))

    fun clearKey(provider: Provider) {
        prefs.edit().remove(prefKey(provider)).apply()
        _changes.tryEmit(Unit)
    }

    private fun prefKey(provider: Provider): String = "key_${provider.name.lowercase()}"

    enum class Provider {
        ZEN,
        OPENROUTER,
        ZAI
    }

    private companion object {
        const val PREFS_NAME = "aven_secure_keys"
        const val PREF_SEEDED = "seeded"
    }
}