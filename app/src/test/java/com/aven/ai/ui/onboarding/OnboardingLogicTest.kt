package com.aven.ai.ui.onboarding

import com.aven.ai.engine.RemoteModelConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OnboardingLogicTest {

    @Test
    fun `fresh install without completion shows onboarding`() {
        assertEquals(
            StartGate.Onboarding,
            startGate(completed = false, isExistingInstall = false)
        )
    }

    @Test
    fun `existing install skips onboarding even when the saved name is blank`() {
        assertEquals(
            StartGate.App,
            startGate(completed = false, isExistingInstall = true)
        )
    }

    @Test
    fun `completed onboarding always skips onboarding`() {
        assertEquals(StartGate.App, startGate(completed = true, isExistingInstall = false))
        assertEquals(StartGate.App, startGate(completed = true, isExistingInstall = true))
    }

    @Test
    fun `blank and whitespace-only names start at the welcome step`() {
        assertEquals(0, onboardingInitialStep(""))
        assertEquals(0, onboardingInitialStep("   "))
    }

    @Test
    fun `a saved name resumes at the provider step`() {
        assertEquals(1, onboardingInitialStep("Chaitanya"))
    }

    @Test
    fun `an identical already-saved provider is matched so retries do not duplicate`() {
        val models = listOf(
            RemoteModelConfig("cfg-1", "My Server", "https://x.example/v1", "qwen/7b")
        )

        assertEquals(
            "cfg-1",
            matchingProviderId(models, "My Server", "https://x.example/v1", "qwen/7b")
        )
        assertNull(matchingProviderId(models, "Other", "https://x.example/v1", "qwen/7b"))
        assertNull(matchingProviderId(models, "My Server", "https://x.example/v2", "qwen/7b"))
        assertNull(matchingProviderId(models, "My Server", "https://x.example/v1", "other/model"))
        assertNull(matchingProviderId(emptyList(), "My Server", "https://x.example/v1", "qwen/7b"))
    }
}
