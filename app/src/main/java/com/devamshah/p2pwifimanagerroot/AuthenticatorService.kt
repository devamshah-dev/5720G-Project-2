package com.devamshah.p2pwifimanagerroot

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.text.format.Formatter
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import kotlinx.coroutines.*
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.io.*
import java.net.*
import java.nio.charset.StandardCharsets
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.coroutines.*
import java.io.File
import java.security.MessageDigest
import android.util.Base64
import java.nio.file.Files
import java.nio.file.Paths
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

class AuthenticatorService : Service() {

    private val TAG = "AuthService"
    private val CHANNEL_ID = "AUTH_SERVICE_CHANNEL"
    private val NOTIFICATION_ID = 100
    private val UDP_BROADCAST_PORT = 44678
    private val UDP_BROADCAST_INTERVAL_MS = 4 * 60 * 1000L // = 4 min
    private val TCP_LISTENER_PORT = 44679
    private val AUTH_SECRET_CODE = "5720GContextAppG20"
    private val AUTHORIZED_PC_ID_HASHES = setOf(
        "SHA256:pXKdD0P2hrsUhKyTqQuxmazkDg3ANjF/Gfcqky79vQ0",
        "SHA256:Qx3IiPBDRoeKvCQM1V4gwtLUPj/2JcfWjsd+JF3zn08"
    )
    private val SSH_DAEMON_COMMAND = "/data/adb/modules/ssh/system/bin/sshd -p 2222" // Or `sshd -p 2222` for OpenSSH
    private val P2P_INTERFACE_NAME = "p2p-wlan0-0" // Common Wi-Fi Direct interface name, verify on your target device (e.g., `adb shell ip a`)
    private val RECOVERY_PASSWORD = "123456"
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var udpBroadcastJob: Job? = null
    private var tcpListenerJob: Job? = null
    private var sshCheckJob: Job? = null
    private var currentLocalIpAddress: String? = null

    // .json file output
    private val JSON_FILE_NAME = "p2p-ip.json"

    private lateinit var locationManager: LocationManager
    private var lastKnownLocation: Location? = null
    private var currentFaceMatchResult: String = "NOT_TRIGGERED" // Placeholder for face match
    private lateinit var devicePolicyManager: DevicePolicyManager
    private lateinit var adminComponentName: ComponentName
    // NEW: LocationListener implementation
    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            lastKnownLocation = location
            Log.d(TAG, "Location updated: Lat=${location.latitude}, Lon=${location.longitude}, Provider=${location.provider}")
            // Consider updating notification with location if desirable
        }
        override fun onStatusChanged(provider: String, status: Int, extras: Bundle?) {
            Log.d(TAG, "Location provider $provider status changed: $status")
        }
        override fun onProviderEnabled(provider: String) {
            Log.d(TAG, "Location provider $provider enabled.")
        }
        override fun onProviderDisabled(provider: String) {
            Log.w(TAG, "Location provider $provider disabled.")
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "AuthenticatorService onCreate")
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification("Authenticator Service", "Starting..."))
        updateServiceStatus("Service Initializing...")
        locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
        devicePolicyManager = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        adminComponentName = ComponentName(this, MyDeviceAdminReceiver::class.java)
        if (checkRequiredPermissions()) {
            startServiceLogic()
        } else {
            Log.e(TAG, "Missing required permissions. Cannot start service logic.")
            updateServiceStatus("Permissions missing. Cannot start. Please grant manually.")
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "AuthenticatorService onStartCommand")
        // stop command from MainActivity
        if (intent?.action == "ACTION_STOP_SERVICE") {
            Log.i(TAG, "Received stop service intent.")
            stopSelf()
            return START_NOT_STICKY
        }
        // This is a good place to update `currentFaceMatchResult` if the service
        // receives an intent from your Face Match Worker/Service, e.g.:
        // if (intent?.action == "ACTION_FACE_MATCH_RESULT") {
        //     currentFaceMatchResult = intent.getStringExtra("result") ?: "UNKNOWN"
        //     Log.d(TAG, "Face match result updated: $currentFaceMatchResult")
        // }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null // Not a bound service
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "AuthenticatorService onDestroy. Shutting down.")
        serviceScope.cancel() // cancel all coroutines
        stopForeground(STOP_FOREGROUND_REMOVE) // remove notification
        updateServiceStatus("Service Stopped.")
        // stop SSH server if it was started by RootCommandExecutor service
        RootCommandExecutor.execute("pkill sshd")
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) { // Added coarse for safety
            locationManager.removeUpdates(locationListener)
            Log.d(TAG, "Location updates stopped.")
        }
    }

    // Service Lifecycle & Permissions
    private fun checkRequiredPermissions(): Boolean {
        val fineLocationGranted = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarseLocationGranted = checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED // NEW
        val accessWifiStateGranted = checkSelfPermission(Manifest.permission.ACCESS_WIFI_STATE) == PackageManager.PERMISSION_GRANTED // NEW
        val changeWifiStateGranted = checkSelfPermission(Manifest.permission.CHANGE_WIFI_STATE) == PackageManager.PERMISSION_GRANTED
        val postNotificationsGranted =
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        val cameraGranted = checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED // NEW
        val isDeviceAdmin = devicePolicyManager.isAdminActive(adminComponentName) // NEW

        // Log the status of each permission/capability
        Log.d(TAG, "Permissions Check: FineLoc=$fineLocationGranted, CoarseLoc=$coarseLocationGranted, WifiState=$accessWifiStateGranted, ChangeWifiState=$changeWifiStateGranted, PostNotif=$postNotificationsGranted, Camera=$cameraGranted, DeviceAdmin=$isDeviceAdmin")
        return (fineLocationGranted || coarseLocationGranted) && accessWifiStateGranted && changeWifiStateGranted && postNotificationsGranted && cameraGranted && isDeviceAdmin
    }

    private fun startServiceLogic() {
        // try specific P2P interface first, then general primary local IP.
        currentLocalIpAddress = RootCommandExecutor.getLocalIpAddress(P2P_INTERFACE_NAME)
        if (currentLocalIpAddress == null) {
            currentLocalIpAddress = RootCommandExecutor.getPrimaryLocalIpAddress()
        }

        if (currentLocalIpAddress == null) {
            Log.e(TAG, "Could not determine local IP address. Retrying periodically.")
            updateServiceStatus("No IP found. Waiting for network...")
            // Implement a retry loop here to keep checking for IP if needed
            serviceScope.launch {
                while (isActive && currentLocalIpAddress == null) {
                    delay(5000L) // 5 seconds wait
                    currentLocalIpAddress = RootCommandExecutor.getLocalIpAddress(P2P_INTERFACE_NAME)
                    if (currentLocalIpAddress == null) {
                        currentLocalIpAddress = RootCommandExecutor.getPrimaryLocalIpAddress()
                    }
                    if (currentLocalIpAddress != null) {
                        Log.d(TAG, "IP found after retry: $currentLocalIpAddress")
                        requestLocationUpdates()
                        startUdpBroadcast()
                        startTcpListener()
                        updateServiceStatus("IP: $currentLocalIpAddress. Ready.")
                    } else {
                        updateServiceStatus("No IP found. Still waiting for network...")
                    }
                }
            }
        } else {
            Log.d(TAG, "Service IP: $currentLocalIpAddress")
            updateServiceStatus("IP: $currentLocalIpAddress. Ready.")
            requestLocationUpdates()
            startUdpBroadcast()
            startTcpListener()
        }
    }
    private fun requestLocationUpdates() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            try {
                // Request updates from GPS_PROVIDER or NETWORK_PROVIDER
                // Using NETWORK_PROVIDER for potentially faster initial fix indoors
                locationManager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER, // Use NETWORK_PROVIDER for faster, battery-friendlier updates
                    10000L, // 10 seconds (adjust as needed for battery/accuracy)
                    100f,   // 100 meters (adjust as needed)
                    locationListener
                )
                locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER, // Also request GPS for higher accuracy when available
                    30000L, // 30 seconds
                    10f,    // 10 meters
                    locationListener
                )

                // Get last known location immediately
                lastKnownLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                if (lastKnownLocation == null) {
                    lastKnownLocation = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                }
                Log.d(TAG, "Requested location updates. Last known: ${lastKnownLocation?.latitude}, ${lastKnownLocation?.longitude}")
            } catch (e: SecurityException) {
                Log.e(TAG, "Location permission granted but failed to request updates: ${e.message}", e)
            }
        } else {
            Log.w(TAG, "Location permission not granted, cannot get GPS updates.")
        }
    }
    private fun getCurrentWifiInfo(): Pair<String?, String?> {
        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val connectivityManager = applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
            checkSelfPermission(Manifest.permission.ACCESS_WIFI_STATE) == PackageManager.PERMISSION_GRANTED) {

            val network = connectivityManager.activeNetwork // For API 23+
            val capabilities = connectivityManager.getNetworkCapabilities(network)

            if (capabilities != null && capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI)) {
                val wifiInfo = wifiManager.connectionInfo
                // SSID might be enclosed in double quotes, remove them
                val ssid = wifiInfo.ssid?.trim('\"')
                val bssid = wifiInfo.bssid
                return Pair(ssid, bssid)
            }
        }
        return Pair(null, null)
    }

    // --- NEW: Placeholder for getting face match result ---
    // You will replace this with actual logic that retrieves the result
    // from your face detection/matching component (e.g., from a shared preference,
    // a global state, or a direct call to a FaceMatchManager if it's running).
    // The result should indicate if the last unlock attempt was by the owner or an unknown person.
    // This value would be updated, for example, by your DeviceAdminReceiver after an unlock attempt.
    private fun getLatestFaceMatchResult(): String {
        // Example: If your DeviceAdminReceiver or PhotoCaptureWorker sets a preference after face match:
        // val sharedPrefs = getSharedPreferences("AntiTheftPrefs", Context.MODE_PRIVATE)
        // return sharedPrefs.getString("last_face_match_result", "NOT_TRIGGERED") ?: "NOT_TRIGGERED"

        // For now, return the class variable value, which is updated by other parts of your app
        return currentFaceMatchResult
    }

    // Foreground Service Notification
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Device Authenticator Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Manages remote device authentication and SSH start."
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    private fun createNotification(title: String, text: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock) // Generic lock icon
            .setOngoing(true) // Makes the notification persistent
            .setSilent(true) // No sound or vibration
            .build()
    }

    private fun updateNotification(text: String) {
        val notification = createNotification("Authenticator Service Active", text)
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun updateServiceStatus(status: String) {
        val intent = Intent("AUTH_SERVICE_STATUS_UPDATE")
        intent.putExtra("status", status)
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
        updateNotification(status) // Update the persistent notification as well
    }

    //UDP Broadcasting for Discovery
    @OptIn(InternalSerializationApi::class)
    private fun startUdpBroadcast() {
        udpBroadcastJob?.cancel()
        udpBroadcastJob = serviceScope.launch {
            while (isActive) {
                val ipAddr = currentLocalIpAddress
                if (ipAddr == null) {
                    Log.w(TAG, "Cannot broadcast, no current IP address found.")
                    delay(5000L)
                    continue
                }
                val sshPubKeyHash = RootCommandExecutor.getSshHostKeyFingerprint()
                if (sshPubKeyHash == null) {
                    Log.e(TAG, "Cannot broadcast, SSH host key fingerprint not found. Ensure key is at ${RootCommandExecutor.SSH_HOST_KEY_PATH}")
                    delay(5000L)
                    continue
                }

                // --- NEW: Get current Wi-Fi and Location data ---
                val (currentSsid, currentBssid) = getCurrentWifiInfo()
                val currentLatitude = lastKnownLocation?.latitude
                val currentLongitude = lastKnownLocation?.longitude
                val latestFaceMatchResult = getLatestFaceMatchResult()


                val messageData = BroadcastMessage(
                    androidId = Build.getSerial(),
                    ip = ipAddr,
                    sshPubKeyHash = sshPubKeyHash,
                    wifiSsid = currentSsid, // NEW
                    wifiBssid = currentBssid, // NEW
                    gpsLatitude = currentLatitude, // NEW
                    gpsLongitude = currentLongitude, // NEW
                    faceMatchResult = latestFaceMatchResult // NEW
                )
                val jsonMessage = Json.encodeToString<BroadcastMessage>(messageData)
                val messageBytes = jsonMessage.toByteArray(StandardCharsets.UTF_8)

                try {
                    val socket = DatagramSocket()
                    socket.broadcast = true
                    val broadcastAddress = getBroadcastAddressFromIp(ipAddr)

                    val packet = DatagramPacket(messageBytes, messageBytes.size, broadcastAddress, UDP_BROADCAST_PORT)
                    socket.send(packet)
                    Log.d(TAG, "UDP broadcast sent: $jsonMessage to ${broadcastAddress.hostAddress}:${UDP_BROADCAST_PORT}")
                    socket.close()
                } catch (e: Exception) {
                    Log.e(TAG, "Error sending UDP broadcast: ${e.message}", e)
                }
                delay(UDP_BROADCAST_INTERVAL_MS)
            }
        }
        Log.d(TAG, "UDP Broadcast job started.")
    }


    // Helper to get broadcast address based on the device's current IP (only works on regular Wi-Fi with DHCP)
    @Suppress("DEPRECATION")
    private fun getBroadcastAddressFromIp(ip: String): InetAddress {
        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val dhcpInfo = wifiManager.dhcpInfo

        // Calculate broadcast address if DHCP info is available
        if (dhcpInfo != null && dhcpInfo.ipAddress != 0) {
            val broadcast = (dhcpInfo.ipAddress and dhcpInfo.netmask) or dhcpInfo.netmask.inv()
            val quads = ByteArray(4)
            for (i in 0..3) {
                quads[i] = ((broadcast shr i * 8) and 0xFF).toByte()
            }
            return InetAddress.getByAddress(quads)
        }
        // Fallback for direct P2P connections or if DHCP info is not reliable = a /24 subnet, common for P2P
        val ipParts = ip.split(".")
        if (ipParts.size == 4) {
            try {
                return InetAddress.getByName("${ipParts[0]}.${ipParts[1]}.${ipParts[2]}.255")
            } catch (e: UnknownHostException) {
                Log.e(TAG, "Error constructing broadcast address from IP: ${e.message}")
            }
        }
        // Last resort: limited broadcast (might not cross all network segments)
        return InetAddress.getByName("255.255.255.255")
    }

    // TCP Listener for Authentication Requests
    private fun startTcpListener() {
        tcpListenerJob?.cancel()
        tcpListenerJob = serviceScope.launch {
            var serverSocket: ServerSocket? = null
            try {
                serverSocket = ServerSocket(TCP_LISTENER_PORT)
                Log.d(TAG, "TCP Listener started on port $TCP_LISTENER_PORT")
                updateServiceStatus("Listening for auth requests...")
                while (isActive) {
                    val clientSocket = serverSocket.accept() // Blocks until a connection is made
                    launch { //client in a new coroutine
                        Log.d(TAG, "Connection from ${clientSocket.inetAddress.hostAddress}")
                        handleAuthRequest(clientSocket)
                    }
                }
            } catch (e: Exception) {
                if (e !is CancellationException) {
                    Log.e(TAG, "TCP Listener error: ${e.message}", e)
                    updateServiceStatus("Listener error: ${e.message}")
                    // Potentially try to restart listener after a delay
                    delay(5000L) // Wait before potential restart
                    startTcpListener()
                }
            } finally {
                try {
                    serverSocket?.close()
                } catch (e: IOException) {
                    Log.e(TAG, "Error closing server socket: ${e.message}")
                }
                Log.d(TAG, "TCP Listener stopped.")
            }
        }
    }

    @OptIn(InternalSerializationApi::class)
    private fun handleAuthRequest(clientSocket: Socket) {
        var hostIp: String? = null
        try {
            hostIp = clientSocket.inetAddress.hostAddress
            val reader = BufferedReader(InputStreamReader(clientSocket.getInputStream(), StandardCharsets.UTF_8))
            val writer = OutputStreamWriter(clientSocket.getOutputStream(), StandardCharsets.UTF_8)

            val requestJson = reader.readLine()
            Log.d(TAG, "Received auth request from $hostIp: $requestJson")

            val authRequest = Json.decodeFromString<AuthRequest>(requestJson)
            val authorizedHashes = AUTHORIZED_PC_ID_HASHES

            if (authorizedHashes.contains(authRequest.pcIdHash) && authRequest.authCode == AUTH_SECRET_CODE) {
                Log.i(TAG, "Authentication successful for PC ID: ${authRequest.pcIdHash}. Command: ${authRequest.cmd}")
                updateServiceStatus("Auth OK. Executing: ${authRequest.cmd}")

                var response: AuthResponse
                when (authRequest.cmd) {
                    "start_ssh" -> {
                        val (nmapSuccess, nmapStdout, nmapStderr) = RootCommandExecutor.execute("nmap -O $hostIp")
                        if (nmapSuccess && ("Microsoft Windows 11" in nmapStdout || "OS details: Microsoft Windows 11" in nmapStdout)) {
                            Log.i(TAG, "Host $hostIp identified as Windows 11. Starting SSH.")
                            updateServiceStatus("Host OS OK. Starting SSH...")
                            startSshServer()
                            response = AuthResponse("OK", "SSH started. You can now connect.")
                        } else {
                            Log.w(TAG, "Nmap check failed for $hostIp or OS not Windows 11. Stderr: $nmapStderr")
                            updateServiceStatus("Nmap/OS check failed. Blocking $hostIp.")
                            RootCommandExecutor.execute("iptables -A INPUT -s $hostIp -j DROP")
                            response = AuthResponse("FAILED", "Host OS not authorized or Nmap failed. IP blocked.")
                        }
                    }
                    "lock_device" -> {
                        if (lockDevice()) {
                            response = AuthResponse("OK", "Device locked successfully.")
                        } else {
                            response = AuthResponse("FAILED", "Failed to lock device. Is Device Admin enabled?")
                        }
                    }
                    "unlock_device" -> { // This means setting a known password to regain access
                        if(unlockDevice()) {
                            response = AuthResponse("OK", "Device password reset to '$RECOVERY_PASSWORD'.")
                        } else {
                            response = AuthResponse("FAILED", "Failed to reset device password. Is Device Admin enabled?")
                        }
                    }
                    "take_photo" -> {
                        if(takePhoto()) {
                            response = AuthResponse("OK", "Photo capture requested.")
                        } else {
                            response = AuthResponse("FAILED", "Failed to request photo capture. Check root/Termux.")
                        }
                    }
                    else -> {
                        Log.w(TAG, "Received unknown command: ${authRequest.cmd}")
                        response = AuthResponse("FAILED", "Unknown command received.")
                    }
                }
                writer.write(Json.encodeToString<AuthResponse>(response) + "\n")

            } else {
                Log.w(TAG, "Authentication failed for host $hostIp. PC ID: ${authRequest.pcIdHash}, Secret Code match: ${authRequest.authCode == AUTH_SECRET_CODE}")
                updateServiceStatus("Auth failed for $hostIp. Blocking IP.")
                RootCommandExecutor.execute("iptables -A INPUT -s $hostIp -j DROP")
                val response = AuthResponse("FAILED", "Authentication failed. IP blocked.")
                writer.write(Json.encodeToString<AuthResponse>(response) + "\n")
            }
            writer.flush()
        } catch (e: Exception) {
            Log.e(TAG, "Error handling auth request from $hostIp: ${e.message}", e)
            updateServiceStatus("Request error from $hostIp: ${e.message}")
            try {
                val writer = OutputStreamWriter(clientSocket.getOutputStream(), StandardCharsets.UTF_8)
                val response = AuthResponse("ERROR", "Internal server error: ${e.message}")
                writer.write(Json.encodeToString<AuthResponse>(response) + "\n")
                writer.flush()
            } catch (ex: IOException) {
                Log.e(TAG, "Failed to send error response: ${ex.message}")
            }
        } finally {
            try {
                clientSocket.close()
            } catch (e: IOException) {
                Log.e(TAG, "Error closing client socket: ${e.message}")
            }
        }
    }

    private fun startSshServer() {
        // Check if SSH is already running before attempting to start
        if (checkSshProcess()) {
            Log.d(TAG, "SSH server is already running. No need to restart.")
            updateServiceStatus("SSH already running.")
            return
        }

        val (success, stdout, stderr) = RootCommandExecutor.execute(SSH_DAEMON_COMMAND)
        if (success) {
            Log.i(TAG, "SSH server started successfully: $stdout")
            updateServiceStatus("SSH server started.")
            startSshProcessMonitor() // Start monitoring to ensure it stays up
        } else {
            Log.e(TAG, "Failed to start SSH server. Stderr: $stderr")
            updateServiceStatus("Failed to start SSH: $stderr")
        }
    }

    private fun checkSshProcess(): Boolean {
        val (success, stdout, _) = RootCommandExecutor.execute("pgrep sshd")
        return success && stdout.isNotBlank()
    }

    private fun startSshProcessMonitor() {
        sshCheckJob?.cancel() // Cancel any previous monitor
        sshCheckJob = serviceScope.launch {
            while (isActive) {
                delay(60 * 1000L) // Check every minute
                if (!checkSshProcess()) {
                    Log.e(TAG, "SSH process died unexpectedly. Attempting to restart.")
                    updateServiceStatus("SSH crashed. Restarting...")
                    startSshServer() // Attempt to restart SSH
                }
            }
        }
        Log.d(TAG, "SSH process monitor started.")
    }

    // --- JSON File Output (Optional) ---
    @OptIn(InternalSerializationApi::class)
    private fun saveIpInfoToFile(ipInfo: P2pIpInfo) {
        val jsonString = Json.encodeToString<P2pIpInfo>(ipInfo) // Corrected: Using reified encodeToString<T>()
        val file = File(filesDir, JSON_FILE_NAME) // App's internal storage
        try {
            file.writeText(jsonString)
            Log.d(TAG, "IP Info saved to ${file.absolutePath}: $jsonString")
            // This is primarily for manual debugging on target or adb pull,
            // as discovery is via UDP broadcast.
        } catch (e: IOException) {
            Log.e(TAG, "Failed to write IP info to file: ${e.message}", e)
        }
    }
    private fun lockDevice(): Boolean {
        if (devicePolicyManager.isAdminActive(adminComponentName)) {
            Log.i(TAG, "Attempting to lock device.")
            devicePolicyManager.lockNow()
            updateServiceStatus("Device locked.")
            return true
        }
        Log.e(TAG, "Cannot lock device: Device Admin not active.")
        return false
    }

    // This "unlock" clears the existing password OR sets a new known password
    private fun unlockDevice(): Boolean {
        if (devicePolicyManager.isAdminActive(adminComponentName)) {
            Log.i(TAG, "Attempting to reset device password to '$RECOVERY_PASSWORD'.")
            // This requires the app to be a device administrator with password quality permissions
            if (devicePolicyManager.resetPassword(RECOVERY_PASSWORD, DevicePolicyManager.RESET_PASSWORD_REQUIRE_ENTRY)) {
                // DevicePolicyManager.RESET_PASSWORD_REQUIRE_ENTRY means user must enter it once
                // This might vary slightly based on Android version and specific policies.
                // For a true "unlock" you might also want to set password quality to something null/none,
                // but setting a known password is often more practical for recovery.
                updateServiceStatus("Device password reset.")
                return true
            } else {
                Log.e(TAG, "Failed to reset device password. Device Policy Manager issue or policy not set.")
                return false
            }
        }
        Log.e(TAG, "Cannot unlock device: Device Admin not active.")
        return false
    }

    // --- NEW: Take Photo Method (Leveraging WorkManager for robustness) ---
    private fun takePhoto(): Boolean {
        Log.i(TAG, "Scheduling PhotoCaptureWorker to take a photo.")
        try {
            val workRequest = OneTimeWorkRequestBuilder<PhotoCaptureWorker>().build()
            WorkManager.getInstance(applicationContext).enqueue(workRequest)
            updateServiceStatus("Photo capture scheduled.")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to schedule photo capture worker: ${e.message}", e)
            return false
        }
    }
}