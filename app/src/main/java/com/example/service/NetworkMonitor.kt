package com.example.service

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.util.Log

class NetworkMonitor(
    private val context: Context,
    private val onNetworkAvailable: () -> Unit,
    private val onNetworkLost: () -> Unit
) {
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var isRegistered = false

    fun isConnected(): Boolean {
        val cm = connectivityManager ?: return false
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val activeNetwork = cm.activeNetwork ?: return false
                val capabilities = cm.getNetworkCapabilities(activeNetwork) ?: return false
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            } else {
                @Suppress("DEPRECATION")
                val networkInfo = cm.activeNetworkInfo
                networkInfo != null && networkInfo.isConnected
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error checking network connectivity: ${e.message}")
            false
        }
    }

    fun startMonitoring() {
        if (isRegistered || connectivityManager == null) return
        try {
            val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    Log.d(TAG, "Network is available / default changed: $network")
                    onNetworkAvailable()
                }

                override fun onCapabilitiesChanged(
                    network: Network,
                    networkCapabilities: NetworkCapabilities
                ) {
                    val hasInternet = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    Log.d(TAG, "Network capabilities changed: hasInternet=$hasInternet")
                    if (hasInternet) {
                        onNetworkAvailable()
                    }
                }

                override fun onLost(network: Network) {
                    Log.d(TAG, "Network connection lost: $network")
                    // If device seamlessly switched to another active network (e.g. WiFi to 4G), signal available
                    if (isConnected()) {
                        Log.d(TAG, "Alternative active network connected, resuming")
                        onNetworkAvailable()
                    } else {
                        onNetworkLost()
                    }
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                connectivityManager.registerDefaultNetworkCallback(callback)
            } else {
                val request = NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build()
                connectivityManager.registerNetworkCallback(request, callback)
            }
            networkCallback = callback
            isRegistered = true
        } catch (e: Exception) {
            Log.w(TAG, "Could not register network callback: ${e.message}")
        }
    }

    fun stopMonitoring() {
        if (!isRegistered) return
        try {
            networkCallback?.let { connectivityManager?.unregisterNetworkCallback(it) }
        } catch (e: Exception) {
            Log.w(TAG, "Could not unregister network callback: ${e.message}")
        } finally {
            networkCallback = null
            isRegistered = false
        }
    }

    companion object {
        private const val TAG = "NetworkMonitor"
    }
}
