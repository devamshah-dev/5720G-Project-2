package com.devamshah.p2pwifimanagerroot

import kotlinx.serialization.Serializable

@kotlinx.serialization.InternalSerializationApi
data class BroadcastMessage(
    val androidId: String,          // Device unique ID (e.g., Build.SERIAL)
    val ip: String,                 // Target's current IPv4 address
    val sshPubKeyHash: String,      // SHA256 of target's SSH host public key
    val timestamp: Long = System.currentTimeMillis() // For freshness check
)

@kotlinx.serialization.InternalSerializationApi
data class AuthRequest(
    val cmd: String,                // Expected: "start_ssh"
    val pcIdHash: String,           // SHA256 of Host PC's SSH public key
    val authCode: String            // Pre-shared secret code
)

@kotlinx.serialization.InternalSerializationApi
data class AuthResponse(
    val status: String,             // "OK", "FAILED", "ERROR"
    val message: String             // Detailed message
)

@kotlinx.serialization.InternalSerializationApi
data class P2pIpInfo( // For the JSON file output (optional, as main IP discovery is via broadcast now)
    val ipv4: String?,
    val ipv6: String?,
    val timestamp: Long = System.currentTimeMillis()
)
