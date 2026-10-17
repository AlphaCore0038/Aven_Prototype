package com.aven.ai.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.profileDataStore: DataStore<Preferences> by preferencesDataStore(name = "aven_profile")

/** User details (name / date of birth / email / avatar colour) shown in Settings → User Details. */
class UserProfileRepository(private val context: Context) {

    data class UserProfile(
        val name: String = "",
        val dateOfBirth: String = "",
        val email: String = "",
        val avatarColor: Int? = null
    )

    val profile: Flow<UserProfile> = context.profileDataStore.data.map { prefs ->
        UserProfile(
            name = prefs[KEY_NAME] ?: "",
            dateOfBirth = prefs[KEY_DOB] ?: "",
            email = prefs[KEY_EMAIL] ?: "",
            avatarColor = prefs[KEY_AVATAR_COLOR]
        )
    }

    suspend fun setName(name: String) {
        context.profileDataStore.edit { it[KEY_NAME] = name }
    }

    suspend fun setDateOfBirth(dateOfBirth: String) {
        context.profileDataStore.edit { it[KEY_DOB] = dateOfBirth }
    }

    suspend fun setEmail(email: String) {
        context.profileDataStore.edit { it[KEY_EMAIL] = email }
    }

    /** Explicit user choice (future colour picker); stored as packed ARGB. */
    suspend fun setAvatarColor(argb: Int) {
        context.profileDataStore.edit { it[KEY_AVATAR_COLOR] = argb }
    }

    /**
     * Assigns a random palette colour only when none is stored yet. The atomic
     * check-and-set inside one edit never overwrites a saved or chosen colour.
     */
    suspend fun ensureAvatarColorAssigned() {
        context.profileDataStore.edit { prefs ->
            if (prefs[KEY_AVATAR_COLOR] == null) {
                prefs[KEY_AVATAR_COLOR] = AVATAR_COLORS.random()
            }
        }
    }

    /** Wipes the profile. Used by tests and a future "Reset profile" action. */
    suspend fun clearAll() {
        context.profileDataStore.edit { it.clear() }
    }

    companion object {
        /**
         * Deliberate avatar palette: eight muted, visually distinct colours,
         * each verified at >= 4.5:1 contrast against white (WCAG AA).
         */
        val AVATAR_COLORS: List<Int> = listOf(
            0xFFB4553A.toInt(),
            0xFF8A5A30.toInt(),
            0xFF647A3A.toInt(),
            0xFF2F8071.toInt(),
            0xFF3F6CAE.toInt(),
            0xFF5B5EA8.toInt(),
            0xFF8F4A7B.toInt(),
            0xFFA84C60.toInt()
        )

        private val KEY_NAME = stringPreferencesKey("name")
        private val KEY_DOB = stringPreferencesKey("date_of_birth")
        private val KEY_EMAIL = stringPreferencesKey("email")
        private val KEY_AVATAR_COLOR = intPreferencesKey("avatar_color")
    }
}
