package com.jarvismini.automation

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume

/**
 * One-shot device location for auto-replies to AI-chat contacts. Uses the platform
 * LocationManager (no Play Services). Returns a Google Maps link plus, when available,
 * a rough place name. Only ever called when the contact is on the AI chat list and
 * location sharing is switched on.
 */
object LocationHelper {

    private const val TAG = "LocationHelper"
    private const val FRESH_MS = 5 * 60_000L

    // English + Hinglish phrasings of "where are you / send your location".
    private val ASK = Regex(
        "(where\\s*(are|r)\\s*(you|u|ya)|where\\s*u\\s*at|wru\\b|your\\s*(current\\s*)?location|" +
            "(send|share|drop)\\s*(me\\s*)?(your\\s*|ur\\s*)?(live\\s*)?(loc|location|pin)|" +
            "ur\\s*location|location\\s*(please|plz|pls|bhej|bhejo)|\\bloc\\s*(bhej|bhejo|do|dena)|" +
            "kah?a?n\\s*(ho|hai|h)\\b|kidhar\\s*(ho|hai|h)\\b|kaha\\s*h)",
        RegexOption.IGNORE_CASE
    )

    fun isLocationRequest(text: String): Boolean = ASK.containsMatchIn(text)

    private fun hasPermission(ctx: Context): Boolean =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    @Suppress("MissingPermission")
    private suspend fun locate(ctx: Context): Location? {
        if (!hasPermission(ctx)) return null
        val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
            .filter { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) || it == LocationManager.PASSIVE_PROVIDER }

        val lastKnown = providers.mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }
        if (lastKnown != null && System.currentTimeMillis() - lastKnown.time < FRESH_MS) return lastKnown

        // Stale or missing: ask for a fresh fix (API 30+), fall back to whatever we had.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val fresh = withTimeoutOrNull(12_000) {
                for (p in providers.filter { it != LocationManager.PASSIVE_PROVIDER }) {
                    val loc = suspendCancellableCoroutine<Location?> { cont ->
                        try {
                            lm.getCurrentLocation(p, null, ContextCompat.getMainExecutor(ctx)) { cont.resume(it) }
                        } catch (e: Exception) {
                            cont.resume(null)
                        }
                    }
                    if (loc != null) return@withTimeoutOrNull loc
                }
                null
            }
            if (fresh != null) return fresh
        }
        return lastKnown
    }

    @Suppress("DEPRECATION")
    private suspend fun placeName(ctx: Context, loc: Location): String? = withContext(Dispatchers.IO) {
        withTimeoutOrNull(4_000) {
            try {
                if (!Geocoder.isPresent()) return@withTimeoutOrNull null
                val a = Geocoder(ctx, Locale.getDefault()).getFromLocation(loc.latitude, loc.longitude, 1)?.firstOrNull()
                    ?: return@withTimeoutOrNull null
                listOfNotNull(a.subLocality ?: a.thoroughfare, a.locality).distinct().joinToString(", ").ifBlank { null }
            } catch (e: Exception) {
                null
            }
        }
    }

    /** Reply text with a map link, or null if no location could be obtained. */
    suspend fun shareText(ctx: Context): String? {
        val loc = locate(ctx) ?: run { Log.w(TAG, "No location available"); return null }
        val ageMin = ((System.currentTimeMillis() - loc.time) / 60_000L).toInt()
        val place = placeName(ctx, loc)
        val link = "https://maps.google.com/?q=${"%.6f".format(Locale.US, loc.latitude)},${"%.6f".format(Locale.US, loc.longitude)}"
        return buildString {
            append("Here's where I am right now")
            if (place != null) append(" (near $place)")
            append(": $link")
            if (ageMin >= 10) append("\n(last updated $ageMin min ago)")
        }
    }
}
