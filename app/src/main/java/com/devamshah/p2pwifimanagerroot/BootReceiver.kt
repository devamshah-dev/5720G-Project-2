package com.devamshah.p2pwifimanagerroot
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

class BootReceiver : BroadcastReceiver() {
    private val TAG = "AuthBootReceiver"

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d(TAG, "Boot completed broadcast received. Starting AuthenticatorService...")
            val command = "am start-foreground-service -n ${context.packageName}/${AuthenticatorService::class.java.name}"
            val (success, stdout, stderr) = RootCommandExecutor.execute(command, timeoutMs = 5000)

            if (success) {
                Log.d(TAG, "AuthenticatorService force-started via root command.")
            } else {
                Log.e(TAG, "Failed to force-start AuthenticatorService via root: $stderr")
                // Fallback to normal start, might still fail on A15
                // context.startForegroundService(Intent(context, AuthenticatorService::class.java))
            }
        }
    }
}