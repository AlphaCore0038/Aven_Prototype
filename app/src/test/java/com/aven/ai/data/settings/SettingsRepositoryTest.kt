package com.aven.ai.data.settings

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SettingsRepositoryTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Before
    fun resetState() = runTest {
        SettingsRepository(context).clearAll()
        UserProfileRepository(context).clearAll()
    }

    @Test
    fun `settings default to dark theme and no model`() = runTest {
        val repo = SettingsRepository(context)
        val defaults = repo.settings.first()
        assertEquals(SettingsRepository.AppSettings.THEME_DARK, defaults.theme)
        assertNull(defaults.defaultProviderId)
        assertNull(defaults.defaultModelId)
    }

    @Test
    fun `theme and model selection round-trip`() = runTest {
        val repo = SettingsRepository(context)
        repo.setTheme(SettingsRepository.AppSettings.THEME_DARK)
        repo.setDefaultProvider("zen")
        repo.setDefaultModel("deepseek-v4-flash")

        val updated = repo.settings.first()
        assertEquals(SettingsRepository.AppSettings.THEME_DARK, updated.theme)
        assertEquals("zen", updated.defaultProviderId)
        assertEquals("deepseek-v4-flash", updated.defaultModelId)
    }

    @Test
    fun `user profile fields round-trip`() = runTest {
        val repo = UserProfileRepository(context)
        assertEquals("", repo.profile.first().name)

        repo.setName("Chaitanya")
        repo.setDateOfBirth("2000-01-01")
        repo.setEmail("chaitanya@example.com")

        val profile = repo.profile.first()
        assertEquals("Chaitanya", profile.name)
        assertEquals("2000-01-01", profile.dateOfBirth)
        assertEquals("chaitanya@example.com", profile.email)
    }
}