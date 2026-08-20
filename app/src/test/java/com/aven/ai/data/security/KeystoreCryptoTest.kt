package com.aven.ai.data.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.Test

/**
 * AndroidKeyStore is unavailable under Robolectric (no provider), so these
 * tests are skipped on the JVM and are intended to run on a device/emulator.
 * Verification status: NOT VERIFIED — REQUIRES DEVICE.
 */
@Ignore("AndroidKeyStore unavailable under Robolectric — run on device")
class KeystoreCryptoTest {

    private val crypto = KeystoreCrypto()

    @Test
    fun `encrypt then decrypt returns original plaintext`() {
        val plaintext = "sk-or-v1-abcdef1234567890"
        val payload = crypto.encrypt(plaintext)

        assertNotEquals(plaintext, payload)
        assertEquals(plaintext, crypto.decrypt(payload))
    }

    @Test
    fun `same plaintext produces different ciphertext each time`() {
        val plaintext = "secret-value"
        assertNotEquals(crypto.encrypt(plaintext), crypto.encrypt(plaintext))
    }

    @Test
    fun `key exists after first encrypt`() {
        assertFalse(crypto.hasKey())
        crypto.encrypt("anything")
        assertTrue(crypto.hasKey())
    }

    @Test
    fun `destroyKey removes the master key`() {
        crypto.encrypt("anything")
        crypto.destroyKey()
        assertFalse(crypto.hasKey())
    }
}