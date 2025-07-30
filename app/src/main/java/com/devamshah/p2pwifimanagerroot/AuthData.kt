package com.devamshah.p2pwifimanagerroot

import kotlinx.serialization.Serializable

@kotlinx.serialization.InternalSerializationApi
data class BroadcastMessage(
    val androidId: String,          // Device unique ID (Build.SERIAL)
    val ip: String,                 // Target's current IPv4 address
    val sshPubKeyHash: String,      // SHA256 of target's SSH host public key
    val timestamp: Long = System.currentTimeMillis(), // freshness check
    val wifiSsid: String? = null,   // Current connected Wi-Fi SSID
    val wifiBssid: String? = null,  // Current connected Wi-Fi BSSID
    val gpsLatitude: Double? = null, // Current GPS Latitude
    val gpsLongitude: Double? = null, // Current GPS Longitude
    val faceMatchResult: String? = null // Result of on-device face match (e.g., "OWNER_MATCH", "UNRECOGNIZED_FACE", "NOT_TRIGGERED")
)
@Serializable
@kotlinx.serialization.InternalSerializationApi
data class AuthRequest(
    val cmd: String,                // "start_ssh"
    val pcIdHash: String,           // SHA256 of Host PC's SSH public key
    val authCode: String            // Pre-shared secret code
)
@Serializable
@kotlinx.serialization.InternalSerializationApi
data class AuthResponse(
    val status: String,             // "OK", "FAILED"
    val message: String             // message
)
@Serializable
@kotlinx.serialization.InternalSerializationApi
data class P2pIpInfo( // For the JSON file output (optional, as main IP discovery is via broadcast now)
    val ipv4: String?,
    val ipv6: String?,
    val timestamp: Long = System.currentTimeMillis()
)
