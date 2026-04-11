package com.driversafety.ai.location

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.net.Uri
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Manages GPS location and opens Google Maps for nearby searches.
 */
class LocationManager(private val context: Context) {

    private val fusedClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    private var currentLocation: Location? = null

    companion object {
        private const val TAG = "LocationManager"
        const val QUERY_COFFEE = "coffee+shops+near+me"
        const val QUERY_REST_AREA = "rest+area+near+me"
    }

    /**
     * Start continuous location updates as a Flow.
     */
    fun locationFlow(): Flow<Location> = callbackFlow {
        if (!hasLocationPermission()) {
            close(SecurityException("Location permission not granted"))
            return@callbackFlow
        }

        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5_000L)
            .setMinUpdateIntervalMillis(2_000L)
            .build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { location ->
                    currentLocation = location
                    trySend(location)
                }
            }
        }

        try {
            fusedClient.requestLocationUpdates(request, callback, context.mainLooper)
        } catch (e: SecurityException) {
            close(e)
            return@callbackFlow
        }

        awaitClose {
            fusedClient.removeLocationUpdates(callback)
        }
    }

    /**
     * Get last known location (quick, cached).
     */
    fun getLastLocation(callback: (Location?) -> Unit) {
        if (!hasLocationPermission()) {
            callback(null)
            return
        }
        try {
            fusedClient.lastLocation.addOnSuccessListener { location ->
                currentLocation = location
                callback(location)
            }.addOnFailureListener {
                callback(currentLocation)
            }
        } catch (e: SecurityException) {
            callback(null)
        }
    }

    /**
     * Open Google Maps with "coffee shops near me" using current GPS coords.
     */
    fun openNearbyCoffee() = openNearbyMaps(QUERY_COFFEE)

    /**
     * Open Google Maps with "rest area near me" using current GPS coords.
     */
    fun openNearbyRestArea() = openNearbyMaps(QUERY_REST_AREA)

    /**
     * Generic nearby Maps opener. Uses lat/lon if available, otherwise text search.
     */
    fun openNearbyMaps(query: String) {
        val loc = currentLocation
        val intent = if (loc != null) {
            // Use precise GPS coordinates
            val uri = Uri.parse(
                "geo:${loc.latitude},${loc.longitude}?q=$query"
            )
            Intent(Intent.ACTION_VIEW, uri).apply {
                setPackage("com.google.android.apps.maps")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        } else {
            // Fallback: text search without GPS
            val uri = Uri.parse("geo:0,0?q=$query")
            Intent(Intent.ACTION_VIEW, uri).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        }

        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Maps not available, trying browser fallback")
            // Fallback to Maps web URL
            val webUri = Uri.parse("https://www.google.com/maps/search/$query")
            val webIntent = Intent(Intent.ACTION_VIEW, webUri).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            try {
                context.startActivity(webIntent)
            } catch (ex: Exception) {
                Log.e(TAG, "Cannot open maps at all: ${ex.message}")
            }
        }
    }

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

    /**
     * Returns current cached location (may be null if not yet obtained).
     */
    fun getCachedLocation(): Location? = currentLocation
}
