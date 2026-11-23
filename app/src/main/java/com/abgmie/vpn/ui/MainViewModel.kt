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
import com.abgmie.vpn.domain.SortMode
import com.abgmie.vpn.domain.ThemeMode
import com.abgmie.vpn.domain.VpnState
import com.abgmie.vpn.servers.ServerRepository
import com.abgmie.vpn.statistics.StatsRepository
import com.abgmie.vpn.statistics.UsageSummary
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

/** A country row in the location browser. connectable only when real servers exist for it. */
data class CountryRow(val country: Country, val servers: List<ServerEntity>) {
    val connectable get() = servers.any { it.enabled }
    val bestPing get() = servers.mapNotNull { it.lastPingMs }.minOrNull()
}

@HiltViewModel
class MainViewModel @Inject constructor(
    private val vpn: VpnManager,
    private val serverRepo: ServerRepository,
    private val settingsRepo: SettingsRepository,
    private val statsRepo: StatsRepository,
    private val auth: AuthRepository,
) : ViewModel() {

    val vpnState: StateFlow<VpnState> = vpn.state
    val settings: StateFlow<AppSettings> = settingsRepo.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())
    val servers: StateFlow<List<ServerEntity>> = serverRepo.servers.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val sessions: StateFlow<List<SessionEntity>> = statsRepo.sessions.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val usage: StateFlow<UsageSummary> = statsRepo.sessions.map { StatsRepository.summarize(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, StatsRepository.summarize(emptyList()))
    val user = auth.user
    val authConfigured get() = auth.firebaseAvailable

    private val selectedId = MutableStateFlow<Long?>(null)
    val selected: StateFlow<ServerEntity?> = combine(servers, settings, selectedId, vpn.state) { list, s, sel, v ->
        v.server?.takeIf { v.status.isActive || v.status.isBusy }
            ?: list.firstOrNull { it.id == sel }
            ?: list.firstOrNull { it.id == s.lastServerId }
            ?: list.firstOrNull()
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val query = MutableStateFlow("")
    val continent = MutableStateFlow<Continent?>(null)

    val countryRows: StateFlow<List<CountryRow>> = combine(servers, query, continent, settings) { list, q, cont, s ->
        val byCountry = list.groupBy { it.countryCode }
        val qq = q.trim().lowercase()
        CountryData.all
            .asSequence()
            .filter { cont == null || it.continent == cont }
            .map { CountryRow(it, sortServers(byCountry[it.code].orEmpty(), s.sort)) }
            .filter { row ->
                qq.isEmpty() || row.country.name.lowercase().contains(qq) || row.country.code.lowercase() == qq ||
                    row.country.cities.any { c -> c.lowercase().contains(qq) } ||
                    row.servers.any { sv -> sv.city.lowercase().contains(qq) || sv.name.lowercase().contains(qq) }
            }
            .sortedWith(
                compareByDescending<CountryRow> { it.connectable }
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
    private var pending: ServerEntity? = null
    private var autoConnectDone = false

    init {
        viewModelScope.launch {
            val s = settingsRepo.settings.first()
            val list = serverRepo.servers.first()
            if (s.autoConnect && !autoConnectDone && !vpn.state.value.status.isActive && list.isNotEmpty()) {
                autoConnectDone = true
                val target = list.firstOrNull { it.id == s.lastServerId } ?: serverRepo.pickBest()
                target?.let { connectTo(it) }
            }
        }
        viewModelScope.launch { if (servers.value.isNotEmpty()) serverRepo.measureAll() }
    }

    fun say(msg: String) { _messages.tryEmit(msg) }

    fun select(s: ServerEntity) { selectedId.value = s.id }

    fun connectTo(s: ServerEntity) {
        select(s)
        val intent = vpn.permissionIntent()
        if (intent != null) { pending = s; _permission.tryEmit(intent) } else vpn.connect(s)
    }

    fun onPermissionResult(granted: Boolean) {
        val s = pending ?: return
        pending = null
        if (granted) vpn.connect(s) else say("VPN permission is required to connect.")
    }

    /** Main power button: connect selected server or disconnect. */
    fun togglePower() {
        val st = vpn.state.value.status
        when {
            st.isBusy -> Unit
            st.isActive -> vpn.disconnect()
            else -> {
                val s = selected.value
                if (s == null) say("No VPN server configured yet. Add a server in Locations.") else connectTo(s)
            }
        }
    }

    fun disconnect() = vpn.disconnect()

    fun autoConnect() = viewModelScope.launch {
        if (servers.value.isEmpty()) { say("No VPN server configured yet."); return@launch }
        say("Measuring latency…")
        val best = serverRepo.pickBest()
        if (best == null) say("No available server.") else connectTo(best)
    }

    fun measurePings() = viewModelScope.launch {
        val r = serverRepo.measureAll()
        if (r.isNotEmpty() && r.all { it.second == null }) say("Ping blocked by the network/device – latency unavailable.")
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
        say(if (vpn.state.value.status.isActive) "DNS saved. Reconnect to apply." else "DNS saved.")
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
