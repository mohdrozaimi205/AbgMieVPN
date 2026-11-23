package com.abgmie.vpn.ui

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.abgmie.vpn.auth.AuthRepository
import com.abgmie.vpn.data.countries.Continent
import com.abgmie.vpn.data.countries.Country
import com.abgmie.vpn.data.countries.CountryData
import com.abgmie.vpn.data.db.ServerEntity
import com.abgmie.vpn.data.db.SessionEntity
import com.abgmie.vpn.data.settings.SettingsRepository
import com.abgmie.vpn.domain.AppSettings
import com.abgmie.vpn.domain.DnsMode
import com.abgmie.vpn.domain.GateServer
import com.abgmie.vpn.domain.SortMode
import com.abgmie.vpn.domain.Target
import com.abgmie.vpn.domain.ThemeMode
import com.abgmie.vpn.domain.VpnEngine
import com.abgmie.vpn.domain.VpnState
import com.abgmie.vpn.domain.VpnStatus
import com.abgmie.vpn.servers.GateList
import com.abgmie.vpn.servers.ServerRepository
import com.abgmie.vpn.servers.VpnGateRepository
import com.abgmie.vpn.statistics.StatsRepository
import com.abgmie.vpn.statistics.UsageSummary
import com.abgmie.vpn.vpn.OpenVpnBridge
import com.abgmie.vpn.vpn.VpnManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.net.InetAddress
import javax.inject.Inject

/** A country row in the location browser. Connectable only when real servers exist for it. */
data class CountryRow(val country: Country, val servers: List<ServerEntity>, val gates: List<GateServer> = emptyList()) {
    val connectable get() = servers.any { it.enabled } || gates.isNotEmpty()
    val bestPing get() = (servers.mapNotNull { it.lastPingMs } + gates.mapNotNull { it.myPingMs }).minOrNull()
    val bestGate get() = gates.sortedWith(compareBy<GateServer> { it.myPingMs ?: Int.MAX_VALUE }.thenByDescending { it.score }).firstOrNull()
}

@HiltViewModel
class MainViewModel @Inject constructor(
    private val vpn: VpnManager,
    private val ovpn: OpenVpnBridge,
    private val serverRepo: ServerRepository,
    private val gateRepo: VpnGateRepository,
    private val settingsRepo: SettingsRepository,
    private val statsRepo: StatsRepository,
    private val auth: AuthRepository,
) : ViewModel() {

    private val lastEngine = MutableStateFlow(VpnEngine.WIREGUARD)

    /** Unified tunnel state across WireGuard (built-in) and OpenVPN (helper app). */
    val vpnState: StateFlow<VpnState> = combine(vpn.state, ovpn.state, lastEngine) { w, o, last ->
        when {
            w.status.isActive || w.status.isBusy -> w
            o.status.isActive || o.status.isBusy -> o
            last == VpnEngine.OPENVPN -> o
            else -> w
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, VpnState())

    val settings: StateFlow<AppSettings> = settingsRepo.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())
    val servers: StateFlow<List<ServerEntity>> = serverRepo.servers.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val gateList: StateFlow<GateList> = gateRepo.list
    val sessions: StateFlow<List<SessionEntity>> = statsRepo.sessions.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val usage: StateFlow<UsageSummary> = statsRepo.sessions.map { StatsRepository.summarize(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, StatsRepository.summarize(emptyList()))
    val user = auth.user
    val authConfigured get() = auth.firebaseAvailable
    fun helperInstalled() = ovpn.isInstalled()

    private val selectedTarget = MutableStateFlow<Target?>(null)
    val selected: StateFlow<Target?> = combine(servers, settings, selectedTarget, vpnState) { list, s, sel, v ->
        val live = v.takeIf { it.status.isActive || it.status.isBusy }
        when {
            live?.server != null -> Target.Wg(live.server)
            live?.gate != null -> Target.Gate(live.gate)
            sel is Target.Wg -> list.firstOrNull { it.id == sel.server.id }?.let { Target.Wg(it) }
            sel is Target.Gate -> sel
            else -> (list.firstOrNull { it.id == s.lastServerId } ?: list.firstOrNull())?.let { Target.Wg(it) }
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val query = MutableStateFlow("")
    val continent = MutableStateFlow<Continent?>(null)

    val countryRows: StateFlow<List<CountryRow>> = combine(servers, query, continent, settings, gateRepo.list) { list, q, cont, s, gl ->
        val byCountry = list.groupBy { it.countryCode }
        val gatesBy = gl.servers.groupBy { it.countryCode }
        val qq = q.trim().lowercase()
        CountryData.all
            .asSequence()
            .filter { cont == null || it.continent == cont }
            .map { CountryRow(it, sortServers(byCountry[it.code].orEmpty(), s.sort), gatesBy[it.code].orEmpty().sortedByDescending { g -> g.score }) }
            .filter { row ->
                qq.isEmpty() || row.country.name.lowercase().contains(qq) || row.country.code.lowercase() == qq ||
                    row.country.cities.any { c -> c.lowercase().contains(qq) } ||
                    row.servers.any { sv -> sv.city.lowercase().contains(qq) || sv.name.lowercase().contains(qq) } ||
                    row.gates.any { g -> g.hostName.lowercase().contains(qq) }
            }
            .sortedWith(
                compareByDescending<CountryRow> { it.servers.isNotEmpty() }
                    .thenByDescending { it.connectable }
                    .thenBy { if (s.sort == SortMode.PING) it.bestPing ?: Int.MAX_VALUE else 0 }
                    .thenBy { if (s.sort == SortMode.LOAD) it.servers.mapNotNull { sv -> sv.loadPercent }.minOrNull() ?: 101 else 0 }
                    .thenBy { it.country.name }
            )
            .toList()
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private fun sortServers(l: List<ServerEntity>, mode: SortMode) = when (mode) {
        SortMode.PING -> l.sortedBy { it.lastPingMs ?: Int.MAX_VALUE }
        SortMode.LOAD -> l.sortedBy { it.loadPercent ?: 101 }
        SortMode.NAME -> l.sortedBy { it.city }
    }

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages
    private val _permission = MutableSharedFlow<Intent>(extraBufferCapacity = 1)
    val permissionRequests: SharedFlow<Intent> = _permission
    private val _external = MutableSharedFlow<Intent>(extraBufferCapacity = 1)
    val externalLaunches: SharedFlow<Intent> = _external
    private var pendingAction: (() -> Unit)? = null
    private var autoConnectDone = false

    init {
        viewModelScope.launch {
            gateRepo.loadCache()
            val age = gateRepo.list.value.fetchedAt?.let { System.currentTimeMillis() - it } ?: Long.MAX_VALUE
            if (age > 3 * 3600_000L) gateRepo.refresh()
        }
        viewModelScope.launch {
            val s = settingsRepo.settings.first()
            val list = serverRepo.servers.first()
            if (s.autoConnect && !autoConnectDone && !vpnState.value.status.isActive && list.isNotEmpty()) {
                autoConnectDone = true
                val target = list.firstOrNull { it.id == s.lastServerId } ?: serverRepo.pickBest()
                target?.let { connectTo(it) }
            }
        }
        viewModelScope.launch { if (servers.value.isNotEmpty()) serverRepo.measureAll() }
    }

    fun say(msg: String) { _messages.tryEmit(msg) }

    private fun requestThen(intent: Intent, action: () -> Unit) {
        pendingAction = action
        _permission.tryEmit(intent)
    }

    fun onPermissionResult(granted: Boolean) {
        val a = pendingAction ?: return
        pendingAction = null
        if (granted) a() else say("Permission is required to connect.")
    }

    fun select(t: Target) { selectedTarget.value = t }

    /** Connect to one of the owner's WireGuard servers (built-in engine). */
    fun connectTo(s: ServerEntity) {
        select(Target.Wg(s))
        lastEngine.value = VpnEngine.WIREGUARD
        viewModelScope.launch {
            if (ovpn.isActive) ovpn.disconnect()
            val intent = vpn.permissionIntent()
            if (intent != null) requestThen(intent) { vpn.connect(s) } else vpn.connect(s)
        }
    }

    /** Connect to a VPN Gate relay through OpenVPN for Android. */
    fun connectGate(g: GateServer) {
        select(Target.Gate(g))
        lastEngine.value = VpnEngine.OPENVPN
        viewModelScope.launch {
            if (!ovpn.isInstalled()) {
                say("Install the free app \"OpenVPN for Android\" once to use VPN Gate servers.")
                _external.tryEmit(ovpn.installIntent())
                return@launch
            }
            val i = ovpn.permissionIntent()
            if (i != null) { requestThen(i) { connectGate(g) }; return@launch }
            if (vpn.state.value.status != VpnStatus.DISCONNECTED) vpn.disconnectAndWait()
            val s = settings.value
            val dns = if (s.dnsMode == DnsMode.CUSTOM) listOf(s.dnsV4, s.dnsV4b).filter { it.isNotBlank() } else emptyList()
            ovpn.connect(g, gateRepo.openVpnConfig(g, dns))
        }
    }

    fun connect(t: Target) = when (t) {
        is Target.Wg -> connectTo(t.server)
        is Target.Gate -> connectGate(t.gate)
    }

    fun installHelper() { _external.tryEmit(ovpn.installIntent()) }

    /** Main power button: connect selection or disconnect whichever engine is up. */
    fun togglePower() {
        val st = vpnState.value.status
        when {
            st == VpnStatus.DISCONNECTING -> Unit
            st.isActive || st == VpnStatus.CONNECTING -> disconnect()
            else -> {
                val t = selected.value
                if (t == null) say("No server selected yet. Open Locations to pick one.") else connect(t)
            }
        }
    }

    fun disconnect() {
        viewModelScope.launch {
            if (vpnState.value.engine == VpnEngine.OPENVPN) ovpn.disconnect() else vpn.disconnect()
        }
    }

    fun autoConnect() = viewModelScope.launch {
        say("Measuring real latency…")
        if (servers.value.isNotEmpty()) {
            val best = serverRepo.pickBest()
            if (best != null) { connectTo(best); return@launch }
        }
        val g = gateRepo.pickBest()
        if (g == null) say(gateRepo.list.value.error ?: "No available server.") else connectGate(g)
    }

    fun measurePings() = viewModelScope.launch {
        val r = serverRepo.measureAll()
        val g = gateRepo.measure()
        val all = r.map { it.second } + g.map { it.myPingMs }
        if (all.isNotEmpty() && all.all { it == null })
            say("Ping blocked by the network/device – latency unavailable.")
    }

    fun refreshGate() = viewModelScope.launch {
        gateRepo.refresh().onSuccess { say("VPN Gate: ${it.size} public servers loaded.") }
    }

    fun toggleFavorite(s: ServerEntity) = viewModelScope.launch { serverRepo.setFavorite(s.id, !s.favorite) }
    fun deleteServer(s: ServerEntity) = viewModelScope.launch {
        if (vpn.state.value.server?.id == s.id && vpn.state.value.status.isActive) { say("Disconnect before deleting this server."); return@launch }
        serverRepo.delete(s.id); say("Server removed.")
    }

    fun importText(text: String, name: String, country: String, city: String, done: (Boolean) -> Unit) = viewModelScope.launch {
        runCatching { serverRepo.import(text, name, country, city) }
            .onSuccess { say("Server added."); serverRepo.measureAll(); done(true) }
            .onFailure { say(it.message ?: "Import failed."); done(false) }
    }

    fun importUrl(url: String, name: String, country: String, city: String, done: (Boolean) -> Unit) = viewModelScope.launch {
        runCatching { serverRepo.fetchConfig(url) }
            .onSuccess { importText(it, name, country, city, done) }
            .onFailure { say(it.message ?: "Download failed."); done(false) }
    }

    fun validate(text: String): String? = runCatching { serverRepo.validate(text); null }.getOrElse { it.message }

    fun setTheme(t: ThemeMode) = viewModelScope.launch { settingsRepo.setTheme(t) }
    fun setAutoConnect(v: Boolean) = viewModelScope.launch { settingsRepo.setAutoConnect(v) }
    fun setKillSwitch(v: Boolean) = viewModelScope.launch { settingsRepo.setKillSwitch(v) }
    fun setAutoReconnect(v: Boolean) = viewModelScope.launch { settingsRepo.setAutoReconnect(v) }
    fun setSort(v: SortMode) = viewModelScope.launch { settingsRepo.setSort(v) }
    fun setLocalOnly(v: Boolean) = viewModelScope.launch { settingsRepo.setLocalOnly(v) }

    /** Validates literal IP addresses only (no hostnames, avoids DNS lookups). */
    fun saveDns(mode: DnsMode, v4: String, v4b: String, v6: String) = viewModelScope.launch {
        if (mode == DnsMode.CUSTOM) {
            val bad = listOf(v4, v4b, v6).filter { it.isNotBlank() }.firstOrNull { !isIpLiteral(it.trim()) }
            if (bad != null) { say("Invalid DNS address: $bad"); return@launch }
            if (listOf(v4, v4b, v6).all { it.isBlank() }) { say("Enter at least one DNS address."); return@launch }
        }
        settingsRepo.setDns(mode, v4, v4b, v6)
        say(if (vpnState.value.status.isActive) "DNS saved. Reconnect to apply." else "DNS saved.")
    }

    private fun isIpLiteral(s: String): Boolean {
        val v4 = Regex("^((25[0-5]|2[0-4]\\d|1?\\d?\\d)\\.){3}(25[0-5]|2[0-4]\\d|1?\\d?\\d)$")
        if (v4.matches(s)) return true
        if (s.contains(':') && Regex("^[0-9a-fA-F:]+$").matches(s)) return runCatching { InetAddress.getByName(s); true }.getOrDefault(false)
        return false
    }

    fun signIn(activity: Context) = viewModelScope.launch {
        auth.signIn(activity).onSuccess { say("Signed in as ${it.name}") }.onFailure { say(it.message ?: "Sign-in failed.") }
    }
    fun signOut() = viewModelScope.launch { auth.signOut(); say("Signed out.") }
    fun resetStats() = viewModelScope.launch { statsRepo.reset(); say("Local statistics cleared.") }
}
