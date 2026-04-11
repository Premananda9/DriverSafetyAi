package com.driversafety.ai.emergency

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.telephony.SmsManager
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * Handles sending emergency SMS, WhatsApp message, and initiating phone calls.
 */
class EmergencyManager(private val context: Context) {

    companion object {
        private const val TAG = "EmergencyManager"
        const val EMERGENCY_MESSAGE =
            "🚨 DRIVER SAFETY ALERT: I am driving and feeling extremely sleepy. " +
            "My drowsiness sensor has detected a critical level. " +
            "Please contact me immediately!"
    }

    /**
     * Send emergency SMS to the provided phone number.
     * Returns true if sent, false if permission denied or no number.
     */
    fun sendEmergencySms(phoneNumber: String, location: android.location.Location? = null): Boolean {
        if (!hasSmsPermission()) {
            Log.w(TAG, "SMS permission not granted")
            return false
        }
        if (phoneNumber.isBlank()) {
            Log.w(TAG, "No emergency contact phone number set")
            return false
        }

        return try {
            @Suppress("DEPRECATION")
            val smsManager = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
            } else {
                SmsManager.getDefault()
            }

            var finalMessage = EMERGENCY_MESSAGE
            if (location != null) {
                finalMessage += "\nMy exact location: https://maps.google.com/?q=${location.latitude},${location.longitude}"
            }

            // Split message if too long
            val parts = smsManager.divideMessage(finalMessage)
            smsManager.sendMultipartTextMessage(phoneNumber, null, parts, null, null)

            Log.d(TAG, "Emergency SMS sent to $phoneNumber")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send SMS: ${e.message}")
            false
        }
    }

    /**
     * Initiate a phone call to the emergency contact.
     * Requires CALL_PHONE permission.
     */
    fun callEmergencyContact(phoneNumber: String): Boolean {
        if (!hasCallPermission()) {
            Log.w(TAG, "CALL_PHONE permission not granted")
            return false
        }
        if (phoneNumber.isBlank()) return false

        return try {
            val intent = Intent(Intent.ACTION_CALL).apply {
                data = Uri.parse("tel:$phoneNumber")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            Log.d(TAG, "Calling $phoneNumber")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Call failed: ${e.message}")
            false
        }
    }

    private fun hasSmsPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            context, Manifest.permission.SEND_SMS
        ) == PackageManager.PERMISSION_GRANTED

    private fun hasCallPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            context, Manifest.permission.CALL_PHONE
        ) == PackageManager.PERMISSION_GRANTED
}
