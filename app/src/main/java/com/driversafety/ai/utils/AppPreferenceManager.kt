package com.driversafety.ai.utils

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri

/**
 * Centralized SharedPreferences wrapper for all app settings.
 */
class AppPreferenceManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "driver_safety_prefs"

        // Keys
        const val KEY_CONTACT_NAME = "contact_name"
        const val KEY_CONTACT_PHONE = "contact_phone"
        const val KEY_MUSIC_URI = "music_uri"
        const val KEY_AUTO_CALL = "auto_call"
        const val KEY_VOICE_INTERACTION = "voice_interaction"
        const val KEY_WHATSAPP_ALERTS = "whatsapp_alerts"
        const val KEY_AUTO_RECONNECT = "auto_reconnect"
        const val KEY_LAST_BT_ADDRESS = "last_bt_address"
        const val KEY_LAST_BT_NAME = "last_bt_name"
        const val KEY_WIFI_IP = "wifi_ip"
        const val KEY_WIFI_PORT = "wifi_port"
        const val KEY_CONNECTION_MODE = "connection_mode"
        // Renamed keys to force a reset of old values saved on the phone
        const val KEY_MODERATE_THRESHOLD = "moderate_threshold_secs_v3"
        const val KEY_CRITICAL_THRESHOLD = "critical_threshold_secs_v3"

        // Connection modes
        const val MODE_BLUETOOTH = "bluetooth"
        const val MODE_WIFI = "wifi"
        const val MODE_WEBSOCKET = "websocket"

        // Default thresholds (in seconds now)
        const val DEFAULT_MODERATE_THRESHOLD = 2
        const val DEFAULT_CRITICAL_THRESHOLD = 3
    }

    // ========== EMERGENCY CONTACT ===========

    var contactName: String
        get() = prefs.getString(KEY_CONTACT_NAME, "") ?: ""
        set(value) = prefs.edit().putString(KEY_CONTACT_NAME, value).apply()

    var contactPhone: String
        get() = prefs.getString(KEY_CONTACT_PHONE, "") ?: ""
        set(value) = prefs.edit().putString(KEY_CONTACT_PHONE, value).apply()

    fun hasEmergencyContact(): Boolean = contactPhone.isNotEmpty()

    // ========== MUSIC ===========

    var musicUri: String
        get() = prefs.getString(KEY_MUSIC_URI, "") ?: ""
        set(value) = prefs.edit().putString(KEY_MUSIC_URI, value).apply()

    fun getMusicUriOrNull(): Uri? {
        val uriStr = musicUri
        return if (uriStr.isNotEmpty()) Uri.parse(uriStr) else null
    }

    fun hasCustomMusic(): Boolean = musicUri.isNotEmpty()

    // ========== FEATURE TOGGLES ===========

    var autoCallEnabled: Boolean
        get() = prefs.getBoolean(KEY_AUTO_CALL, false)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_CALL, value).apply()

    var voiceInteractionEnabled: Boolean
        get() = prefs.getBoolean(KEY_VOICE_INTERACTION, true)
        set(value) = prefs.edit().putBoolean(KEY_VOICE_INTERACTION, value).apply()

    var whatsappAlertsEnabled: Boolean
        get() = prefs.getBoolean(KEY_WHATSAPP_ALERTS, true)
        set(value) = prefs.edit().putBoolean(KEY_WHATSAPP_ALERTS, value).apply()

    var autoReconnect: Boolean
        get() = prefs.getBoolean(KEY_AUTO_RECONNECT, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_RECONNECT, value).apply()

    // ========== BLUETOOTH ===========

    var lastBtAddress: String
        get() = prefs.getString(KEY_LAST_BT_ADDRESS, "") ?: ""
        set(value) = prefs.edit().putString(KEY_LAST_BT_ADDRESS, value).apply()

    var lastBtName: String
        get() = prefs.getString(KEY_LAST_BT_NAME, "") ?: ""
        set(value) = prefs.edit().putString(KEY_LAST_BT_NAME, value).apply()

    // ========== WIFI ===========

    var wifiIp: String
        get() = prefs.getString(KEY_WIFI_IP, "") ?: ""
        set(value) = prefs.edit().putString(KEY_WIFI_IP, value).apply()

    var wifiPort: String
        get() = prefs.getString(KEY_WIFI_PORT, "8080") ?: "8080"
        set(value) = prefs.edit().putString(KEY_WIFI_PORT, value).apply()

    // ========== CONNECTION MODE ===========

    var connectionMode: String
        get() = prefs.getString(KEY_CONNECTION_MODE, MODE_BLUETOOTH) ?: MODE_BLUETOOTH
        set(value) = prefs.edit().putString(KEY_CONNECTION_MODE, value).apply()

    // ========== THRESHOLDS ===========

    var moderateThreshold: Int
        get() = prefs.getInt(KEY_MODERATE_THRESHOLD, DEFAULT_MODERATE_THRESHOLD)
        set(value) = prefs.edit().putInt(KEY_MODERATE_THRESHOLD, value).apply()

    var criticalThreshold: Int
        get() = prefs.getInt(KEY_CRITICAL_THRESHOLD, DEFAULT_CRITICAL_THRESHOLD)
        set(value) = prefs.edit().putInt(KEY_CRITICAL_THRESHOLD, value).apply()
}
