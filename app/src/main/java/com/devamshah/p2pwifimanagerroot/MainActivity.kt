package com.devamshah.p2pwifimanagerroot

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
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
    // Permissions needed by AuthenticatorService
    private val permissionsToRequest = mutableListOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.CHANGE_WIFI_STATE,
        Manifest.permission.ACCESS_WIFI_STATE,
        Manifest.permission.CHANGE_NETWORK_STATE,
        Manifest.permission.INTERNET
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
                startAuthenticatorService()
                activationSwitch.isChecked = true
            } else {
                Toast.makeText(this, "Permissions denied. Service cannot run.", Toast.LENGTH_LONG).show()
                statusTextView.text = getString(R.string.disabled_permissions_denied)
                activationSwitch.isChecked = false
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
    private fun startAuthenticatorService() {
        Log.d(TAG, "Attempting to start AuthenticatorService from MainActivity.")
        val serviceIntent = Intent(this, AuthenticatorService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
        statusTextView.text = getString(R.string.service_starting)
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
        statusTextView.text = "Service stopped by user."
        Toast.makeText(this, "Authenticator Service stopped.", Toast.LENGTH_SHORT).show()
    }
}