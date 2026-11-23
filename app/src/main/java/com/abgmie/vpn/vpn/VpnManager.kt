package com.abgmie.vpn.vpn

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.VpnService
import android.util.Log
import com.abgmie.vpn.data.db.ServerEntity
import com.abgmie.vpn.data.settings.SettingsRepository
import com.abgmie.vpn.domain.VpnState
import com.abgmie.vpn.domain.VpnStatus
import com.abgmie.vpn.servers.ServerRepository
import com.abgmie.vpn.statistics.StatsRepository
import com.wireguard.android.backend.GoBackend
import com.wireguard.android.backend.Tunnel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single source of truth for the WireGuard tunnel. Shared by phone, tablet and TV UIs.
 *
 * CONNECTED is only reported after the WireGuard backend is UP *and* a cryptographic
 * handshake with the server has been observed in the tunnel statistics.
 */
@Singleton
class VpnManager @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val servers: ServerRepository,
    private val settings: SettingsRepository,
    private val stats: StatsRepository,
    private val http: OkHttpClient,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val backend by lazy { GoBackend(ctx) }
    private val lock = Mutex()
    private var monitor: Job? = null
    private var sessionId: Long? = null
    @Volatile private var userStopping = false
    @Volatile private var networkChanged = false

    private val _state = MutableStateFlow(VpnState())
    val state: StateFlow<VpnState> = _state.asStateFlow()

    private val tunnel = object : Tunnel {
        override fun getName(): String = "abgmie"
        override fun onStateChange(newState: Tunnel.State) {
            // Tunnel taken down by the system (e.g. another VPN app started, permission revoked).
            if (newState == Tunnel.State.DOWN && !userStopping && _state.value.status.isActive) {
                scope.launch { onTunnelLost("VPN was stopped by Android (another VPN app or permission revoked).") }
            }
        }
    }

    init {
        // Android "Always-on VPN": the system starts our VpnService; bring the last server up.
        GoBackend.setAlwaysOnCallback {
            scope.launch {
                val id = settings.current().lastServerId ?: return@launch
                servers.get(id)?.let { connect(it) }
            }
        }
        runCatching {
            val cm = ctx.getSystemService(ConnectivityManager::class.java)
            cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) { networkChanged = true }
                override fun onLost(network: Network) { networkChanged = true }
            })
        }
        scope.launch { stats.closeDangling() }
    }

    /** Non-null when the user must grant VPN permission first (system dialog). */
    fun permissionIntent(): Intent? = VpnService.prepare(ctx)

    fun connect(server: ServerEntity) {
        scope.launch { doConnect(server, reconnecting = false) }
    }

    fun disconnect() {
        scope.launch { doDisconnect() }
    }

    /** Used before handing the VPN slot to another engine (Android allows one VPN at a time). */
    suspend fun disconnectAndWait() = doDisconnect()

    fun toggle(server: ServerEntity?) {
        val st = _state.value.status
        when {
            st.isBusy -> Unit
            st.isActive -> disconnect()
            server != null -> connect(server)
        }
    }

    private suspend fun doConnect(server: ServerEntity, reconnecting: Boolean) = lock.withLock {
        val s = settings.current()
        if (permissionIntent() != null) {
            _state.update { it.copy(status = VpnStatus.FAILED, error = "VPN permission not granted.") }
            return@withLock
        }
        userStopping = false
        monitor?.cancel()
        val keepStart = if (reconnecting) _state.value.connectedAt else null
        _state.update {
            it.copy(
                status = if (reconnecting) VpnStatus.RECONNECTING else VpnStatus.CONNECTING,
                server = server, error = null,
                publicIp = if (reconnecting) it.publicIp else null,
            )
        }
        try {
            val cfg = servers.buildConfig(server, s)
            userStopping = true // our own DOWN/UP cycle must not be treated as "tunnel lost"
            withContext(Dispatchers.IO) {
                if (backend.getState(tunnel) == Tunnel.State.UP) backend.setState(tunnel, Tunnel.State.DOWN, null)
                backend.setState(tunnel, Tunnel.State.UP, cfg)
            }
            userStopping = false
            // Generate a little traffic so WireGuard performs its handshake, then verify it.
            scope.launch { fetchPublicIp() }
            val hs = waitForHandshake(timeoutMs = 20_000)
            if (hs <= 0L) {
                userStopping = true
                runCatching { backend.setState(tunnel, Tunnel.State.DOWN, null) }
                servers.markConnected(server.id, ok = false)
                _state.update {
                    VpnState(status = VpnStatus.FAILED, server = server,
                        error = "No handshake from ${server.endpoint}. Check the server is running, UDP port open and your key is registered.")
                }
                return@withLock
            }
            servers.markConnected(server.id, ok = true)
            settings.setLastServer(server.id)
            if (sessionId == null) sessionId = stats.start(server)
            _state.update {
                it.copy(status = VpnStatus.CONNECTED, connectedAt = keepStart ?: System.currentTimeMillis(), lastHandshake = hs)
            }
            fetchPublicIp()
            startMonitor()
        } catch (e: Exception) {
            Log.w(TAG, "connect failed: ${e.javaClass.simpleName}") // never log configs/keys
            userStopping = true
            runCatching { backend.setState(tunnel, Tunnel.State.DOWN, null) }
            servers.markConnected(server.id, ok = false)
            _state.update { VpnState(status = VpnStatus.FAILED, server = server, error = friendly(e)) }
            endSession()
        }
    }

    private suspend fun doDisconnect() = lock.withLock {
        if (_state.value.status == VpnStatus.DISCONNECTED) return@withLock
        userStopping = true
        monitor?.cancel()
        _state.update { it.copy(status = VpnStatus.DISCONNECTING) }
        runCatching { withContext(Dispatchers.IO) { backend.setState(tunnel, Tunnel.State.DOWN, null) } }
        endSession()
        _state.update { VpnState(status = VpnStatus.DISCONNECTED, server = it.server) }
    }

    private suspend fun onTunnelLost(reason: String) {
        val s = settings.current()
        val server = _state.value.server
        if ((s.autoReconnect || s.killSwitch) && server != null) {
            doConnect(server, reconnecting = true)
        } else {
            monitor?.cancel()
            endSession()
            _state.update { VpnState(status = VpnStatus.DISCONNECTED, server = it.server, error = reason) }
        }
    }

    private fun latestHandshake(): Long = runCatching {
        val st = backend.getStatistics(tunnel)
        st.peers().maxOfOrNull { k -> st.peer(k)?.latestHandshakeEpochMillis() ?: 0L } ?: 0L
    }.getOrDefault(0L)

    private suspend fun waitForHandshake(timeoutMs: Long): Long {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) {
            val hs = latestHandshake()
            if (hs > 0) return hs
            delay(400)
        }
        return 0L
    }

    private fun startMonitor() {
        monitor = scope.launch {
            var lastRx = -1L; var lastTx = -1L
            var tick = 0
            var lastHealthCheck = System.currentTimeMillis()
            while (isActive) {
                delay(1000)
                tick++
                val st = runCatching { backend.getStatistics(tunnel) }.getOrNull() ?: continue
                val rx = st.totalRx(); val tx = st.totalTx()
                val hs = latestHandshake()
                _state.update {
                    it.copy(
                        rxBytes = rx, txBytes = tx,
                        rxRate = if (lastRx >= 0) (rx - lastRx).coerceAtLeast(0) else 0,
                        txRate = if (lastTx >= 0) (tx - lastTx).coerceAtLeast(0) else 0,
                        lastHandshake = if (hs > 0) hs else it.lastHandshake,
                    )
                }
                lastRx = rx; lastTx = tx
                if (tick % 10 == 0) sessionId?.let { stats.update(it, rx, tx) }

                // Tunnel health: WireGuard re-handshakes at least every ~2 min while traffic flows.
                val now = System.currentTimeMillis()
                val stale = hs > 0 && now - hs > 180_000
                if ((stale && now - lastHealthCheck > 60_000) || networkChanged) {
                    networkChanged = false
                    lastHealthCheck = now
                    fetchPublicIp() // produces traffic -> forces a fresh handshake if the path is alive
                    delay(8_000)
                    if (System.currentTimeMillis() - latestHandshake() > 190_000) {
                        val server = _state.value.server ?: continue
                        if (settings.current().autoReconnect || settings.current().killSwitch) {
                            scope.launch { doConnect(server, reconnecting = true) }
                            return@launch
                        } else {
                            _state.update { it.copy(error = "Server not responding (no handshake for 3+ minutes).") }
                        }
                    }
                }
            }
        }
    }

    private suspend fun endSession() {
        val id = sessionId ?: return
        sessionId = null
        val v = _state.value
        stats.finish(id, v.rxBytes, v.txBytes)
    }

    /** Public IP as seen from the internet (api.ipify.org). Shown only when it really answered. */
    suspend fun fetchPublicIp() {
        val ip = withContext(Dispatchers.IO) {
            runCatching {
                http.newCall(Request.Builder().url("https://api.ipify.org?format=json").build()).execute().use { r ->
                    if (!r.isSuccessful) null
                    else Json.parseToJsonElement(r.body!!.string()).jsonObject["ip"]?.jsonPrimitive?.content
                }
            }.getOrNull()
        }
        if (ip != null) _state.update { it.copy(publicIp = ip) }
    }

    private fun friendly(e: Exception): String {
        val m = e.message ?: ""
        return when {
            e.javaClass.simpleName.contains("BackendException") ->
                "WireGuard backend error: ${runCatching { (e as com.wireguard.android.backend.BackendException).reason.name }.getOrDefault(m)}"
            e is com.wireguard.config.BadConfigException -> "Server config is invalid."
            else -> "Connection error: ${m.ifBlank { e.javaClass.simpleName }}"
        }
    }

    companion object { private const val TAG = "AbgMieVPN" }
}
