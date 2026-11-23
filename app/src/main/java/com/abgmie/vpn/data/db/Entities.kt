package com.abgmie.vpn.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A real WireGuard server owned/configured by the user.
 * The full config (incl. client private key) is stored ONLY as an AES-GCM blob (Android Keystore).
 */
@Entity(tableName = "servers")
data class ServerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val countryCode: String,
    val city: String,
    val endpointHost: String,
    val endpointPort: Int,
    val serverPublicKey: String,
    val encryptedConfig: String,
    val favorite: Boolean = false,
    val enabled: Boolean = true,
    val lastPingMs: Int? = null,
    val lastPingAt: Long? = null,
    /** Load % only when the owner's backend/registry provides it; null = unknown (never guessed). */
    val loadPercent: Int? = null,
    val lastConnectedAt: Long? = null,
    val lastOk: Boolean? = null,
    val createdAt: Long = System.currentTimeMillis(),
) {
    val endpoint get() = "$endpointHost:$endpointPort"
}

@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val serverId: Long,
    val serverName: String,
    val countryCode: String,
    val city: String,
    val startedAt: Long,
    val endedAt: Long? = null,
    val rxBytes: Long = 0,
    val txBytes: Long = 0,
)
