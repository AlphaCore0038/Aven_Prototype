package com.aven.ai.data.security

import android.content.Context
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/**
 * Secure storage for API credentials, keyed by an arbitrary string id (each
 * user-configured online model owns its own id). Values are AES-GCM encrypted
 * under a Keystore-held master key and persisted in private SharedPreferences.
 */
class KeyManager(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )
    private val crypto = KeystoreCrypto()

    /** Emitted whenever a key is written or cleared. */
    private val _changes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val changes: SharedFlow<Unit> = _changes

    fun writeKey(id: String, key: String) {
        prefs.edit().putString(prefKey(id), crypto.encrypt(key)).apply()
        _changes.tryEmit(Unit)
    }

    fun readKey(id: String): String? {
        val payload = prefs.getString(prefKey(id), null) ?: return null
        return runCatching { crypto.decrypt(payload) }.getOrNull()
    }

    fun hasKey(id: String): Boolean = prefs.contains(prefKey(id))

    fun clearKey(id: String) {
        prefs.edit().remove(prefKey(id)).apply()
        _changes.tryEmit(Unit)
    }

    private fun prefKey(id: String): String = "key_$id"

    private companion object {
        const val PREFS_NAME = "aven_secure_keys"
    }
}