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

enum class VpnEngine { WIREGUARD, OPENVPN }

/**
 * A public VPN Gate relay (vpngate.net, University of Tsukuba academic experiment).
 * Run by volunteers, protocol OpenVPN. Every field comes from the live VPN Gate list – nothing invented.
 */
data class GateServer(
    val hostName: String,
    val ip: String,
    val score: Long,
    /** Ping reported by VPN Gate (measured from their side in Japan), null if not reported. */
    val reportedPingMs: Int?,
    val speedBps: Long,
    val countryName: String,
    val countryCode: String,
    val sessions: Int,
    val uptimeMs: Long,
    val totalUsers: Long,
    val logPolicy: String,
    val operator: String,
    val configBase64: String,
    val myPingMs: Int? = null,
) {
    val id get() = "gate:" + hostName
    val speedMbps get() = speedBps / 1_000_000.0
}

/** What the user selected to connect to: own WireGuard server or a VPN Gate relay. */
sealed interface Target {
    val countryCode: String
    val title: String
    val subtitle: String
    val endpoint: String
    val pingMs: Int?

    data class Wg(val server: ServerEntity) : Target {
        override val countryCode get() = server.countryCode
        override val title get() = server.city
        override val subtitle get() = server.name + " · WireGuard"
        override val endpoint get() = server.endpoint
        override val pingMs get() = server.lastPingMs
    }

    data class Gate(val gate: GateServer) : Target {
        override val countryCode get() = gate.countryCode
        override val title get() = gate.hostName
        override val subtitle get() = "VPN Gate · OpenVPN · public relay"
        override val endpoint get() = gate.ip
        override val pingMs get() = gate.myPingMs ?: gate.reportedPingMs
    }
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
    val engine: VpnEngine = VpnEngine.WIREGUARD,
    val gate: GateServer? = null,
) {
    /** OpenVPN for Android's API does not expose byte counters, so we never show fake numbers. */
    val hasCounters get() = engine == VpnEngine.WIREGUARD
    val endpointText get() = server?.endpoint ?: gate?.ip
    val countryCode get() = server?.countryCode ?: gate?.countryCode
}

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
