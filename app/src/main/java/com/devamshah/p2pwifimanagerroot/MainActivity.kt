package com.devamshah.p2pwifimanagerroot

import android.Manifest
import android.app.admin.DevicePolicyManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.material.materialswitch.MaterialSwitch

class MainActivity : AppCompatActivity() {

    private val TAG = "AuthMainActivity"
    private lateinit var statusTextView: TextView
    private lateinit var activationSwitch: MaterialSwitch
    private lateinit var activateDeviceAdminButton: Button
    private lateinit var devicePolicyManager: DevicePolicyManager
    private lateinit var adminComponentName: ComponentName
    // Permissions needed by AuthenticatorService
    private val permissionsToRequest = mutableListOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.CHANGE_WIFI_STATE,
        Manifest.permission.ACCESS_WIFI_STATE,
        Manifest.permission.CHANGE_NETWORK_STATE,
        Manifest.permission.INTERNET,
        Manifest.permission.CAMERA
    ).apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    // permissions launcher
    private val requestPermissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val allGranted = permissions.entries.all { it.value }
            if (allGranted) {
                Toast.makeText(this, "Permissions granted. Starting service.", Toast.LENGTH_SHORT).show()
                checkAndRequestDeviceAdmin()
            } else {
                Toast.makeText(this, "Permissions denied. Service cannot run.", Toast.LENGTH_LONG).show()
                statusTextView.text = getString(R.string.disabled_permissions_denied)
                activationSwitch.isChecked = false
            }
        }
    private val requestDeviceAdminLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                Log.d(TAG, "Device Admin Activated Successfully")
                Toast.makeText(this, "Device Administrator Activated!", Toast.LENGTH_SHORT).show()
                updateDeviceAdminButtonState()
                startAuthenticatorService() // Now start the service
                activationSwitch.isChecked = true
            } else {
                Log.w(TAG, "Device Admin Activation Failed or Cancelled")
                Toast.makeText(this, "Device Administrator not activated. Lock/Unlock features won't work.", Toast.LENGTH_LONG).show()
                activationSwitch.isChecked = false
                statusTextView.text = getString(R.string.disabled_device_admin_needed)
            }
        }
    // receiver listens for the status updates from AuthenticatorService
    private val serviceStatusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val status = intent?.getStringExtra("status")
            status?.let {
                Log.d(TAG, "Received status update: $it")
                statusTextView.text = it
            }
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        statusTextView = findViewById(R.id.statusTextView)
        activationSwitch = findViewById(R.id.activationSwitch)
        activateDeviceAdminButton = findViewById(R.id.activateDeviceAdminButton) // NEW
        // NEW: Initialize Device Policy Manager and ComponentName
        devicePolicyManager = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        adminComponentName = ComponentName(this, MyDeviceAdminReceiver::class.java)
        // Register the receiver to get live updates
        LocalBroadcastManager.getInstance(this).registerReceiver(
            serviceStatusReceiver, IntentFilter("AUTH_SERVICE_STATUS_UPDATE")
        )
        activationSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                checkPermissionsAndStartService()
            } else {
                stopAuthenticatorService()
            }
        }
        activateDeviceAdminButton.setOnClickListener {
            val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
            intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponentName)
            intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "This app requires Device Administrator privileges to perform remote lock/unlock and photo capture in case of theft.")
            requestDeviceAdminLauncher.launch(intent)
        }

        updateDeviceAdminButtonState()
    }
    override fun onResume() {
        super.onResume()
        updateDeviceAdminButtonState() // Ensure button state is updated if user comes back from settings
        // Also check if service is running and update switch
        // You might want to query service status here if it's not handled by the broadcast receiver
        // e.g., if AuthenticatorService exposes a Binder or a shared preference.
    }
    override fun onDestroy() {
        super.onDestroy()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(serviceStatusReceiver)
    }
    private fun checkPermissionsAndStartService() {
        val neededPermissions = permissionsToRequest.filter {
            checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()
        if (neededPermissions.isNotEmpty()) {
            requestPermissionsLauncher.launch(neededPermissions)
        } else {
            startAuthenticatorService()
        }
    }
    private fun checkAndRequestDeviceAdmin() {
        if (!devicePolicyManager.isAdminActive(adminComponentName)) {
            // Device Admin is not active, prompt the user
            Toast.makeText(this, "Please activate Device Administrator for full features.", Toast.LENGTH_LONG).show()
            val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
            intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponentName)
            intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "This app requires Device Administrator privileges to perform remote lock/unlock and photo capture in case of theft.")
            requestDeviceAdminLauncher.launch(intent)
        } else {
            // Device Admin is already active, proceed to start service
            startAuthenticatorService()
        }
    }
    private fun updateDeviceAdminButtonState() {
        if (devicePolicyManager.isAdminActive(adminComponentName)) {
            activateDeviceAdminButton.text = getString(R.string.device_administrator_active)
            activateDeviceAdminButton.isEnabled = false // Disable button once active
        } else {
            activateDeviceAdminButton.text = getString(R.string.activate_device_administrator)
            activateDeviceAdminButton.isEnabled = true
        }
    }
    private fun startAuthenticatorService() {
        Log.d(TAG, "Attempting to start AuthenticatorService from MainActivity.")
        val command = "am start-foreground-service -n ${packageName}/${AuthenticatorService::class.java.name}"
        val (success, stdout, stderr) = RootCommandExecutor.execute(command, timeoutMs = 5000)

        if (success) {
            Log.d(TAG, "AuthenticatorService force-started via root command.")
            statusTextView.text = getString(R.string.service_starting_via_root)
            Toast.makeText(this, "Sentry Service Activated (via root).", Toast.LENGTH_SHORT).show()
        } else {
            Log.e(TAG, "Failed to force-start AuthenticatorService via root: $stderr")
            // Fallback to normal start, might still fail on A15, but try anyway
            val serviceIntent = Intent(this, AuthenticatorService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
            statusTextView.text = getString(R.string.service_starting)
            Toast.makeText(this, "Sentry Service Activated (fallback).", Toast.LENGTH_SHORT).show()
        }
    }
    private fun stopAuthenticatorService() {
        Log.d(TAG, "Attempting to stop AuthenticatorService from MainActivity.")
        val serviceIntent = Intent(this, AuthenticatorService::class.java).apply {
            // The service is designed to be stopped by calling stopSelf() from an intent action
            action = "ACTION_STOP_SERVICE"
        }
        // call startService with a stop action & is destroyed by stopService().
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
        statusTextView.text = getString(R.string.service_stopped_by_user)
        Toast.makeText(this, "Authenticator Service stopped.", Toast.LENGTH_SHORT).show()
    }
}