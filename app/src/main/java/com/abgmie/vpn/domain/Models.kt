package com.abgmie.vpn.domain

import com.abgmie.vpn.data.db.ServerEntity

enum class VpnStatus(val label: String) {
    DISCONNECTED("Disconnected"),
    CONNECTING("Connecting"),
    CONNECTED("Connected"),
    RECONNECTING("Reconnecting"),
    DISCONNECTING("Disconnecting"),
    FAILED("Connection Failed");

    val isBusy get() = this == CONNECTING || this == RECONNECTING || this == DISCONNECTING
    val isActive get() = this == CONNECTED || this == RECONNECTING
}

/** Snapshot of the real tunnel. Every number here comes from WireGuard statistics, never simulated. */
data class VpnState(
    val status: VpnStatus = VpnStatus.DISCONNECTED,
    val server: ServerEntity? = null,
    val connectedAt: Long? = null,
    val rxBytes: Long = 0,
    val txBytes: Long = 0,
    val rxRate: Long = 0,
    val txRate: Long = 0,
    val lastHandshake: Long = 0,
    val publicIp: String? = null,
    val error: String? = null,
)

enum class ThemeMode { SYSTEM, LIGHT, DARK }
enum class DnsMode { AUTOMATIC, CUSTOM }
enum class SortMode { NAME, PING, LOAD }

data class AppSettings(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val autoConnect: Boolean = false,
    val killSwitch: Boolean = false,
    val autoReconnect: Boolean = true,
    val dnsMode: DnsMode = DnsMode.AUTOMATIC,
    val dnsV4: String = "",
    val dnsV4b: String = "",
    val dnsV6: String = "",
    val lastServerId: Long? = null,
    val localOnly: Boolean = false,
    val sort: SortMode = SortMode.PING,
)
