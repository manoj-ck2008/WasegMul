package com.agrelius.wasegmul.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/**
 * Utility to check network availability.
 *
 * ### Validated uplink (§3.42)
 * `NET_CAPABILITY_INTERNET` only means a route exists (captive portals pass it);
 * `NET_CAPABILITY_VALIDATED` means the system actually reached the internet. Tier-3
 * Barcode Resolution keys off this — without it every portal login burns a full
 * cascade timeout before failing.
 */
object ConnectivityChecker {
    fun isOnline(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}
