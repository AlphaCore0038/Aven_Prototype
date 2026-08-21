package com.aven.ai.data.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.NetworkInfo
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowNetworkCapabilities
import org.robolectric.shadows.ShadowNetworkInfo

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ConnectivityMonitorTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val connectivityManager = context.getSystemService(ConnectivityManager::class.java)
    private val shadow = shadowOf(connectivityManager)

    @Before
    fun reset() {
        shadow.clearAllNetworks()
        shadow.setActiveNetworkInfo(null)
    }

    private fun monitor() = ConnectivityMonitor(
        context = context,
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    )

    private fun capabilities(validated: Boolean): NetworkCapabilities {
        val caps = ShadowNetworkCapabilities.newInstance()
        shadowOf(caps).addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        if (validated) {
            shadowOf(caps).addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        }
        return caps
    }

    private fun setWifiActive() {
        shadow.setActiveNetworkInfo(wifiNetworkInfo())
        shadow.setNetworkCapabilities(connectivityManager.activeNetwork, capabilities(validated = true))
    }

    private fun wifiNetworkInfo() = ShadowNetworkInfo.newInstance(
        NetworkInfo.DetailedState.CONNECTED,
        ConnectivityManager.TYPE_WIFI,
        0,
        true,
        NetworkInfo.State.CONNECTED
    )

    @Test
    fun `reports offline when no active network`() {
        val m = monitor()
        m.refresh()
        assertFalse(m.isOnline.value)
    }

    @Test
    fun `reports online when active network is validated`() {
        setWifiActive()
        val m = monitor()
        m.refresh()
        assertTrue(m.isOnline.value)
    }

    @Test
    fun `reports offline when active network is not validated`() {
        shadow.setActiveNetworkInfo(wifiNetworkInfo())
        shadow.setNetworkCapabilities(
            connectivityManager.activeNetwork,
            capabilities(validated = false)
        )
        val m = monitor()
        m.refresh()
        assertFalse(m.isOnline.value)
    }
}