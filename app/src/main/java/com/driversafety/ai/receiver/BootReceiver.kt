package com.driversafety.ai.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.driversafety.ai.service.ForegroundMonitorService

/**
 * Restarts the monitoring service after device reboot.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val serviceIntent = Intent(context, ForegroundMonitorService::class.java)
            context.startForegroundService(serviceIntent)
        }
    }
}
