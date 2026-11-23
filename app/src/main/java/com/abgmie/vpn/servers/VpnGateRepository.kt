package com.abgmie.vpn.servers

import android.content.Context
import android.util.Base64
import com.abgmie.vpn.domain.GateServer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class GateList(
    val servers: List<GateServer> = emptyList(),
    val fetchedAt: Long? = null,
    val loading: Boolean = false,
    val error: String? = null,
)

/**
 * Live list of VPN Gate public relays from the official API (https://www.vpngate.net/api/iphone/).
 * Only real, currently listed servers are shown. Cached on device so the list survives restarts.
 */
@Singleton
class VpnGateRepository @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val http: OkHttpClient,
) {
    private val cache = File(ctx.filesDir, "vpngate.csv")
    private val _list = MutableStateFlow(GateList())
    val list: StateFlow<GateList> = _list.asStateFlow()

    suspend fun loadCache() = withContext(Dispatchers.IO) {
        if (_list.value.servers.isEmpty() && cache.exists()) {
            runCatching { parse(cache.readText()) }.getOrNull()?.takeIf { it.isNotEmpty() }?.let { s ->
                _list.update { it.copy(servers = s, fetchedAt = cache.lastModified()) }
            }
        }
    }

    suspend fun refresh(): Result<List<GateServer>> = withContext(Dispatchers.IO) {
        _list.update { it.copy(loading = true, error = null) }
        val r = runCatching {
            val req = Request.Builder().url(API).header("User-Agent", "AbgMieVPN").build()
            val body = http.newCall(req).execute().use { res ->
                if (!res.isSuccessful) error("VPN Gate list unavailable (HTTP ${res.code}).")
                res.body!!.string()
            }
            val servers = parse(body)
            if (servers.isEmpty()) error("VPN Gate returned no servers. Try again later.")
            cache.writeText(body)
            servers
        }
        r.onSuccess { s -> _list.update { GateList(servers = s, fetchedAt = System.currentTimeMillis()) } }
            .onFailure { e -> _list.update { it.copy(loading = false, error = e.message ?: "Could not load VPN Gate list.") } }
        r
    }

    /** Measures our own ICMP ping to the best-scored relays (real latency from this device). */
    suspend fun measure(top: Int = 20): List<GateServer> = coroutineScope {
        val current = _list.value.servers
        val candidates = current.sortedByDescending { it.score }.take(top)
        val measured = candidates.map { g -> async(Dispatchers.IO) { g.copy(myPingMs = LatencyProbe.ping(g.ip)) } }.awaitAll()
        val byId = measured.associateBy { it.id }
        _list.update { l -> l.copy(servers = l.servers.map { byId[it.id] ?: it }) }
        measured
    }

    /** Best relay: lowest measured ping among top-scored relays, then VPN Gate score. */
    suspend fun pickBest(): GateServer? {
        if (_list.value.servers.isEmpty()) refresh()
        val m = measure()
        return m.sortedWith(compareBy<GateServer> { it.myPingMs == null }.thenBy { it.myPingMs ?: Int.MAX_VALUE }.thenByDescending { it.score })
            .firstOrNull()
    }

    /** Decoded .ovpn config, cleaned and made compatible with OpenVPN 2.6 (VPN Gate still uses AES-128-CBC). */
    fun openVpnConfig(g: GateServer, dns: List<String>): String {
        val raw = String(Base64.decode(g.configBase64, Base64.DEFAULT), Charsets.UTF_8)
        val lines = raw.lines().filter { l -> val t = l.trim(); t.isNotEmpty() && !t.startsWith("#") && !t.startsWith(";") }
        val sb = StringBuilder(lines.joinToString("\n")).append('\n')
        if (lines.none { it.trim().startsWith("data-ciphers") }) {
            sb.append("data-ciphers AES-256-GCM:AES-128-GCM:CHACHA20-POLY1305:AES-128-CBC\n")
            sb.append("data-ciphers-fallback AES-128-CBC\n")
        }
        dns.forEach { sb.append("dhcp-option DNS ").append(it).append('\n') }
        return sb.toString()
    }

    companion object {
        const val API = "https://www.vpngate.net/api/iphone/"

        fun parse(csv: String): List<GateServer> = csv.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("*") && !it.startsWith("#") }
            .mapNotNull { line ->
                val p = line.split(',')
                if (p.size < 15) return@mapNotNull null
                val config = p.last()
                if (config.length < 100) return@mapNotNull null
                runCatching {
                    GateServer(
                        hostName = p[0], ip = p[1],
                        score = p[2].toLongOrNull() ?: 0,
                        reportedPingMs = p[3].toIntOrNull(),
                        speedBps = p[4].toLongOrNull() ?: 0,
                        countryName = p[5], countryCode = p[6].uppercase(),
                        sessions = p[7].toIntOrNull() ?: 0,
                        uptimeMs = p[8].toLongOrNull() ?: 0,
                        totalUsers = p[9].toLongOrNull() ?: 0,
                        logPolicy = p[11], operator = p[12],
                        configBase64 = config,
                    )
                }.getOrNull()
            }
            .distinctBy { it.hostName }
            .toList()
    }
}
