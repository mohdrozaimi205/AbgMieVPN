package com.abgmie.vpn.vpn

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.net.Uri
import android.os.IBinder
import android.util.Log
import com.abgmie.vpn.domain.GateServer
import com.abgmie.vpn.domain.VpnEngine
import com.abgmie.vpn.domain.VpnState
import com.abgmie.vpn.domain.VpnStatus
import com.abgmie.vpn.statistics.StatsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import de.blinkt.openvpn.api.IOpenVPNAPIService
import de.blinkt.openvpn.api.IOpenVPNStatusCallback
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

/**
 * OpenVPN engine for VPN Gate relays, delegated to the free open-source app
 * "OpenVPN for Android" (de.blinkt.openvpn) through its official AIDL remote API.
 *
 * CONNECTED is shown only when OpenVPN reports LEVEL_CONNECTED (tunnel up, routes installed),
 * then confirmed by fetching the public IP through the tunnel.
 */
@Singleton
class OpenVpnBridge @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val stats: StatsRepository,
    private val http: OkHttpClient,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var service: IOpenVPNAPIService? = null
    private var bindWait: CompletableDeferred<IOpenVPNAPIService?>? = null
    private var watchdog: Job? = null
    private var sessionId: Long? = null
    @Volatile private var ourGate: GateServer? = null
    @Volatile private var wasConnected = false

    private val _state = MutableStateFlow(VpnState(engine = VpnEngine.OPENVPN))
    val state: StateFlow<VpnState> = _state.asStateFlow()

    fun isInstalled(): Boolean = try {
        ctx.packageManager.getPackageInfo(PKG, 0); true
    } catch (e: PackageManager.NameNotFoundException) { false }

    fun installIntent(): Intent {
        val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$PKG")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return if (market.resolveActivity(ctx.packageManager) != null) market
        else Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$PKG")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    private val callback = object : IOpenVPNStatusCallback.Stub() {
        override fun newStatus(uuid: String?, state: String?, message: String?, level: String?) {
            onStatus(state.orEmpty(), message.orEmpty(), level.orEmpty())
        }
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val s = IOpenVPNAPIService.Stub.asInterface(binder)
            service = s
            runCatching { s.registerStatusCallback(callback) }
            bindWait?.complete(s)
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
        }
    }

    private suspend fun api(): IOpenVPNAPIService? {
        service?.let { return it }
        if (!isInstalled()) return null
        val wait = CompletableDeferred<IOpenVPNAPIService?>()
        bindWait = wait
        val ok = withContext(Dispatchers.Main) {
            runCatching {
                ctx.bindService(Intent(IOpenVPNAPIService::class.java.name).setPackage(PKG), connection, Context.BIND_AUTO_CREATE)
            }.getOrDefault(false)
        }
        if (!ok) return null
        return withTimeoutOrNull(6_000) { wait.await() }
    }

    /**
     * Returns an Intent the user must approve first (API access for AbgMieVPN, then Android's VPN permission
     * for OpenVPN for Android), or null when everything is already allowed.
     */
    suspend fun permissionIntent(): Intent? {
        val s = api() ?: return null
        return runCatching { s.prepare(ctx.packageName) ?: s.prepareVPNService() }.getOrNull()
    }

    suspend fun connect(g: GateServer, config: String): Boolean {
        val s = api()
        if (s == null) {
            _state.value = VpnState(engine = VpnEngine.OPENVPN, status = VpnStatus.FAILED, gate = g,
                error = "OpenVPN for Android is not installed or could not be reached.")
            return false
        }
        ourGate = g
        wasConnected = false
        _state.value = VpnState(engine = VpnEngine.OPENVPN, status = VpnStatus.CONNECTING, gate = g)
        return try {
            s.startVPN(config)
            watchdog?.cancel()
            watchdog = scope.launch {
                delay(45_000)
                if (_state.value.status == VpnStatus.CONNECTING && ourGate == g) {
                    runCatching { service?.disconnect() }
                    _state.value = VpnState(engine = VpnEngine.OPENVPN, status = VpnStatus.FAILED, gate = g,
                        error = "${g.hostName} did not respond within 45 s. Volunteer relays go offline often – try another.")
                }
            }
            true
        } catch (e: Exception) {
            Log.w(TAG, "startVPN failed: ${e.javaClass.simpleName}")
            _state.value = VpnState(engine = VpnEngine.OPENVPN, status = VpnStatus.FAILED, gate = g,
                error = "OpenVPN for Android refused the request: ${e.message ?: e.javaClass.simpleName}")
            false
        }
    }

    suspend fun disconnect() {
        watchdog?.cancel()
        if (_state.value.status != VpnStatus.DISCONNECTED) _state.update { it.copy(status = VpnStatus.DISCONNECTING) }
        runCatching { api()?.disconnect() }
        ourGate = null
        finishSession()
        _state.update { VpnState(engine = VpnEngine.OPENVPN, status = VpnStatus.DISCONNECTED, gate = it.gate) }
    }

    val isActive get() = _state.value.status.isActive || _state.value.status == VpnStatus.CONNECTING

    private fun onStatus(state: String, message: String, level: String) {
        val gate = ourGate ?: _state.value.gate
        when (level) {
            "LEVEL_CONNECTED" -> {
                watchdog?.cancel()
                val first = !wasConnected
                wasConnected = true
                _state.update {
                    it.copy(status = VpnStatus.CONNECTED, gate = gate, error = null,
                        connectedAt = if (first || it.connectedAt == null) System.currentTimeMillis() else it.connectedAt)
                }
                if (first) scope.launch {
                    gate?.let { g -> sessionId = stats.startRaw("VPN Gate · ${g.hostName}", g.countryCode, g.countryName) }
                    fetchPublicIp()
                }
            }
            "LEVEL_START", "LEVEL_CONNECTING_SERVER_REPLIED", "LEVEL_CONNECTING_NO_SERVER_REPLY_YET", "LEVEL_WAITING_FOR_USER_INPUT" ->
                if (_state.value.status != VpnStatus.DISCONNECTING)
                    _state.update { it.copy(status = if (wasConnected) VpnStatus.RECONNECTING else VpnStatus.CONNECTING, gate = gate) }
            "LEVEL_NONETWORK" ->
                if (wasConnected) _state.update { it.copy(status = VpnStatus.RECONNECTING, error = "Waiting for network…") }
            "LEVEL_AUTH_FAILED" -> {
                watchdog?.cancel()
                _state.update { VpnState(engine = VpnEngine.OPENVPN, status = VpnStatus.FAILED, gate = gate, error = "Authentication failed on ${gate?.hostName ?: "server"}.") }
                scope.launch { finishSession() }
            }
            "LEVEL_VPNPAUSED" -> _state.update { it.copy(status = VpnStatus.DISCONNECTED, error = "VPN paused in OpenVPN for Android.") }
            "LEVEL_NOTCONNECTED" -> {
                val cur = _state.value.status
                if (cur == VpnStatus.CONNECTING) return // NOPROCESS is reported briefly while a new profile starts
                if (cur.isActive || cur == VpnStatus.DISCONNECTING) {
                    wasConnected = false
                    scope.launch { finishSession() }
                    _state.update { VpnState(engine = VpnEngine.OPENVPN, status = VpnStatus.DISCONNECTED, gate = gate) }
                }
            }
        }
        if (level != "LEVEL_CONNECTED") Log.d(TAG, "status $state") // no configs/keys logged
    }

    private suspend fun finishSession() {
        val id = sessionId ?: return
        sessionId = null
        stats.finish(id, 0, 0)
    }

    private suspend fun fetchPublicIp() {
        val ip = runCatching {
            http.newCall(Request.Builder().url("https://api.ipify.org?format=json").build()).execute().use { r ->
                if (r.isSuccessful) Json.parseToJsonElement(r.body!!.string()).jsonObject["ip"]?.jsonPrimitive?.content else null
            }
        }.getOrNull()
        if (ip != null) _state.update { it.copy(publicIp = ip) }
    }

    companion object {
        const val PKG = "de.blinkt.openvpn"
        private const val TAG = "AbgMieVPN"
    }
}
