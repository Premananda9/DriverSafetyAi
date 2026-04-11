package com.driversafety.ai.utils

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * Handles runtime permission requests.
 */
object PermissionManager {

    // Request codes
    const val RC_BLUETOOTH = 101
    const val RC_LOCATION = 102
    const val RC_SMS = 103
    const val RC_CALL = 104
    const val RC_MICROPHONE = 105
    const val RC_ALL_CRITICAL = 110

    /**
     * Bluetooth permissions (handles Android 12+ split permissions).
     */
    fun getBluetoothPermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN
            )
        } else {
            arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN
            )
        }
    }

    fun getLocationPermissions(): Array<String> = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )

    fun getSmsPermissions(): Array<String> = arrayOf(Manifest.permission.SEND_SMS)

    fun getCallPermissions(): Array<String> = arrayOf(Manifest.permission.CALL_PHONE)

    fun getMicPermissions(): Array<String> = arrayOf(Manifest.permission.RECORD_AUDIO)

    fun getNotificationPermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.POST_NOTIFICATIONS)
        } else emptyArray()
    }

    fun getMediaPermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.READ_MEDIA_AUDIO)
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    // ========== CHECK ===========

    fun hasPermission(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    fun hasAllPermissions(context: Context, permissions: Array<String>): Boolean =
        permissions.all { hasPermission(context, it) }

    fun hasBluetoothPermissions(context: Context): Boolean =
        hasAllPermissions(context, getBluetoothPermissions())

    fun hasLocationPermission(context: Context): Boolean =
        hasPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)

    fun hasSmsPermission(context: Context): Boolean =
        hasPermission(context, Manifest.permission.SEND_SMS)

    fun hasCallPermission(context: Context): Boolean =
        hasPermission(context, Manifest.permission.CALL_PHONE)

    fun hasMicPermission(context: Context): Boolean =
        hasPermission(context, Manifest.permission.RECORD_AUDIO)

    // ========== REQUEST ===========

    fun requestPermissions(activity: Activity, permissions: Array<String>, requestCode: Int) {
        ActivityCompat.requestPermissions(activity, permissions, requestCode)
    }

    fun requestBluetooth(activity: Activity) =
        requestPermissions(activity, getBluetoothPermissions(), RC_BLUETOOTH)

    fun requestLocation(activity: Activity) =
        requestPermissions(activity, getLocationPermissions(), RC_LOCATION)

    fun requestSms(activity: Activity) =
        requestPermissions(activity, getSmsPermissions(), RC_SMS)

    fun requestCall(activity: Activity) =
        requestPermissions(activity, getCallPermissions(), RC_CALL)

    fun requestMicrophone(activity: Activity) =
        requestPermissions(activity, getMicPermissions(), RC_MICROPHONE)

    /**
     * Request all critical permissions in one shot.
     */
    fun requestAllCritical(activity: Activity) {
        val all = mutableListOf<String>().apply {
            addAll(getBluetoothPermissions())
            addAll(getLocationPermissions())
            addAll(getSmsPermissions())
            addAll(getMicPermissions())
            addAll(getNotificationPermissions())
            addAll(getMediaPermissions())
        }
        requestPermissions(activity, all.toTypedArray(), RC_ALL_CRITICAL)
    }

    /**
     * Check if result contains all permissions granted.
     */
    fun allGranted(grantResults: IntArray): Boolean =
        grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }
}
