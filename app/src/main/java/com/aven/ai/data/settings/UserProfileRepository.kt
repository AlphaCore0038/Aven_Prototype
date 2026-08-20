package com.aven.ai.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.profileDataStore: DataStore<Preferences> by preferencesDataStore(name = "aven_profile")

/** User details (name / date of birth / email) shown in Settings → User Details. */
class UserProfileRepository(private val context: Context) {

    data class UserProfile(
        val name: String = "",
        val dateOfBirth: String = "",
        val email: String = ""
    )

    val profile: Flow<UserProfile> = context.profileDataStore.data.map { prefs ->
        UserProfile(
            name = prefs[KEY_NAME] ?: "",
            dateOfBirth = prefs[KEY_DOB] ?: "",
            email = prefs[KEY_EMAIL] ?: ""
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

    /** Wipes the profile. Used by tests and a future "Reset profile" action. */
    suspend fun clearAll() {
        context.profileDataStore.edit { it.clear() }
    }

    private companion object {
        val KEY_NAME = stringPreferencesKey("name")
        val KEY_DOB = stringPreferencesKey("date_of_birth")
        val KEY_EMAIL = stringPreferencesKey("email")
    }
}