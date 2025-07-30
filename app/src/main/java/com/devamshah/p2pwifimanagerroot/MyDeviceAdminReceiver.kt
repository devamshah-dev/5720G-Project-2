package com.devamshah.p2pwifimanagerroot

import android.app.admin.DeviceAdminReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast

class MyDeviceAdminReceiver : DeviceAdminReceiver() {
    private val TAG = "DeviceAdminReceiver"

    companion object {
        fun getComponentName(context: Context): ComponentName {
            return ComponentName(context, MyDeviceAdminReceiver::class.java)
        }
    }

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Log.d(TAG, "Device Admin Enabled: ${getComponentName(context).flattenToShortString()}")
        Toast.makeText(context, "Device Administrator Enabled", Toast.LENGTH_SHORT).show()
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        Log.d(TAG, "Device Admin Disabled: ${getComponentName(context).flattenToShortString()}")
        Toast.makeText(context, "Device Administrator Disabled", Toast.LENGTH_SHORT).show()
    }

    override fun onPasswordFailed(context: Context, intent: Intent) {
        super.onPasswordFailed(context, intent)
        Log.d(TAG, "Password Failed Attempt")
        // You could trigger a photo capture here if you want to,
        // or send a broadcast to AuthenticatorService to update faceMatchResult.
        // For now, AuthenticatorService manages the commands from PC.
    }

    override fun onPasswordSucceeded(context: Context, intent: Intent) {
        super.onPasswordSucceeded(context, intent)
        Log.d(TAG, "Password Succeeded")
    }

    // You can override other methods like onLockTaskModeEntering/Exiting, etc.
}