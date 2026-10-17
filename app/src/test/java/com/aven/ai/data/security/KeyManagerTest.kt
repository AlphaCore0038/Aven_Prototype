package com.aven.ai.data.security

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * JVM-side coverage of the id-keyed API. AndroidKeyStore is unavailable under
 * Robolectric, so the Keystore path itself is verified on device only.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class KeyManagerTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Before
    fun reset() {
        KeyManager(context).let { km ->
            km.clearKey("cfg-a")
            km.clearKey("cfg-b")
        }
    }

    @Test
    fun `no key returns null`() {
        assertNull(KeyManager(context).readKey("cfg-a"))
        assertFalse(KeyManager(context).hasKey("cfg-a"))
    }

    @Test
    @Ignore("AndroidKeyStore unavailable under Robolectric — NOT VERIFIED, REQUIRES DEVICE")
    fun `write then read round-trips per id`() {
        val km = KeyManager(context)
        km.writeKey("cfg-a", "secret-value")
        assertEquals("secret-value", km.readKey("cfg-a"))
        assertTrue(km.hasKey("cfg-a"))
    }

    @Test
    @Ignore("AndroidKeyStore unavailable under Robolectric — NOT VERIFIED, REQUIRES DEVICE")
    fun `distinct ids are independent`() {
        val km = KeyManager(context)
        km.writeKey("cfg-a", "key-a")
        km.writeKey("cfg-b", "key-b")
        assertEquals("key-a", km.readKey("cfg-a"))
        assertEquals("key-b", km.readKey("cfg-b"))
        km.clearKey("cfg-a")
        assertNull(km.readKey("cfg-a"))
        assertEquals("key-b", km.readKey("cfg-b"))
    }

    @Test
    @Ignore("AndroidKeyStore unavailable under Robolectric — NOT VERIFIED, REQUIRES DEVICE")
    fun `stored value is ciphertext, not plaintext`() {
        val km = KeyManager(context)
        km.writeKey("cfg-a", "very-secret-api-key")
        val raw = context.getSharedPreferences("aven_secure_keys", android.content.Context.MODE_PRIVATE)
            .getString("key_cfg-a", null)
        assertTrue(raw != null && !raw!!.contains("very-secret-api-key"))
    }

    @Test
    @Ignore("AndroidKeyStore unavailable under Robolectric — NOT VERIFIED, REQUIRES DEVICE")
    fun `clear removes the key`() {
        val km = KeyManager(context)
        km.writeKey("cfg-a", "k")
        km.clearKey("cfg-a")
        assertNull(km.readKey("cfg-a"))
        assertFalse(km.hasKey("cfg-a"))
    }
}