package com.aven.ai.data.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Tracks real internet connectivity of the device. A network counts as online
 * only when it is validated (NET_CAPABILITY_VALIDATED) — the OS has confirmed
 * actual internet access, not merely that Wi-Fi/cellular is up.
 *
 * The source of truth is [ConnectivityManager.activeNetwork], re-checked every
 * [POLL_INTERVAL_MS]. Network callbacks trigger an immediate re-check, but the
 * periodic poll guarantees the state self-corrects even when the OS misses or
 * delays callback delivery (a known quirk on several OEM builds).
 */
class ConnectivityMonitor(
    context: Context,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) {

    companion object {
        private const val POLL_INTERVAL_MS = 2_000L
    }

    private val connectivityManager =
        context.applicationContext.getSystemService(ConnectivityManager::class.java)

    private val _isOnline = MutableStateFlow(false)
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = refresh()
        override fun onCapabilitiesChanged(
            network: Network,
            networkCapabilities: NetworkCapabilities
        ) = refresh()
        override fun onLost(network: Network) {
            _isOnline.value = false
            refresh()
        }
    }

    init {
        connectivityManager.registerDefaultNetworkCallback(callback)
        scope.launch {
            while (isActive) {
                refresh()
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    /** Re-derives connectivity from the active network. Internal for tests. */
    internal fun refresh() {
        val active = connectivityManager.activeNetwork
        val capabilities = active?.let { connectivityManager.getNetworkCapabilities(it) }
        _isOnline.value =
            capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}