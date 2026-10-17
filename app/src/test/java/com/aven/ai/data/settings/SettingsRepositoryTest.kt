package com.aven.ai.data.settings

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
    fun `onboarding flag defaults to false and persists once set`() = runTest {
        val repo = SettingsRepository(context)
        assertFalse(repo.settings.first().onboardingCompleted)

        repo.setOnboardingCompleted()

        assertTrue(repo.settings.first().onboardingCompleted)
        assertTrue(SettingsRepository(context).settings.first().onboardingCompleted)
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

    @Test
    fun `missing avatar colour is assigned from the palette`() = runTest {
        val repo = UserProfileRepository(context)
        assertNull(repo.profile.first().avatarColor)

        repo.ensureAvatarColorAssigned()

        val assigned = repo.profile.first().avatarColor
        assertNotNull(assigned)
        assertTrue(UserProfileRepository.AVATAR_COLORS.contains(assigned))
    }

    @Test
    fun `ensureAvatarColorAssigned does not change an assigned colour`() = runTest {
        val repo = UserProfileRepository(context)
        repo.ensureAvatarColorAssigned()
        val assigned = repo.profile.first().avatarColor

        repeat(5) { repo.ensureAvatarColorAssigned() }

        assertEquals(assigned, repo.profile.first().avatarColor)
    }

    @Test
    fun `assigned avatar colour persists across repository instances`() = runTest {
        UserProfileRepository(context).ensureAvatarColorAssigned()
        val first = UserProfileRepository(context).profile.first().avatarColor

        assertNotNull(first)
        assertEquals(first, UserProfileRepository(context).profile.first().avatarColor)
    }

    @Test
    fun `setAvatarColor updates the stored colour`() = runTest {
        val repo = UserProfileRepository(context)
        val manual = UserProfileRepository.AVATAR_COLORS.last()

        repo.setAvatarColor(manual)

        assertEquals(manual, repo.profile.first().avatarColor)
    }

    @Test
    fun `ensureAvatarColorAssigned preserves a manually chosen colour`() = runTest {
        val repo = UserProfileRepository(context)
        val manual = UserProfileRepository.AVATAR_COLORS.first()
        repo.setAvatarColor(manual)

        repo.ensureAvatarColorAssigned()

        assertEquals(manual, repo.profile.first().avatarColor)
    }

    @Test
    fun `every palette colour meets 4_5 to 1 contrast against white`() {
        UserProfileRepository.AVATAR_COLORS.forEach { argb ->
            val contrast = 1.05 / (luminanceOf(argb) + 0.05)
            assertTrue(
                "contrast %.2f:1 for #%08X".format(contrast, argb),
                contrast >= 4.5
            )
        }
    }

    private fun luminanceOf(argb: Int): Double {
        val r = channel((argb shr 16) and 0xFF)
        val g = channel((argb shr 8) and 0xFF)
        val b = channel(argb and 0xFF)
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }

    private fun channel(value: Int): Double {
        val c = value / 255.0
        return if (c <= 0.03928) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4)
    }
}