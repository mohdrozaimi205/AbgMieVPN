package com.abgmie.vpn.tv

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.abgmie.vpn.core.Abg
import com.abgmie.vpn.core.Brand
import com.abgmie.vpn.core.Fmt
import com.abgmie.vpn.data.countries.Continent
import com.abgmie.vpn.data.countries.CountryData
import com.abgmie.vpn.data.db.ServerEntity
import com.abgmie.vpn.ui.*

private val tvTabs = listOf(
    "Home" to Icons.Rounded.Shield,
    "Locations" to Icons.Rounded.Public,
    "Statistics" to Icons.Rounded.BarChart,
    "Settings" to Icons.Rounded.Settings,
)

/**
 * Dedicated 16:9 TV experience: left sidebar, D-pad focus everywhere, large type.
 * Shares MainViewModel / VpnManager with the phone UI – the VPN engine is identical.
 */
@Composable
fun TvApp(vm: MainViewModel, snackbar: SnackbarHostState) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var sub by rememberSaveable { mutableStateOf(SubPage.NONE) }
    var importOpen by rememberSaveable { mutableStateOf(false) }
    val c = Abg.colors
    BackHandler(enabled = sub != SubPage.NONE || tab != 0) { if (sub != SubPage.NONE) sub = SubPage.NONE else tab = 0 }

    Box(Modifier.fillMaxSize().brandBackground(c.bg, c.bgAlt, c.isDark)) {
        Row(Modifier.fillMaxSize().padding(horizontal = 36.dp, vertical = 28.dp)) {
            // LEFT: navigation sidebar
            Column(Modifier.width(250.dp).fillMaxHeight()) {
                Text("AbgMieVPN", color = c.text, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Text("One World. One Connection.", color = Brand.ElectricBlue, fontSize = 13.sp)
                Spacer(Modifier.height(30.dp))
                tvTabs.forEachIndexed { i, (label, icon) ->
                    SidebarItem(label, icon, selected = tab == i && sub == SubPage.NONE) { sub = SubPage.NONE; tab = i }
                    Spacer(Modifier.height(8.dp))
                }
                SidebarItem("Updates", Icons.Rounded.SystemUpdate, selected = sub == SubPage.UPDATES) { sub = SubPage.UPDATES }
            }
            Spacer(Modifier.width(28.dp))
            Box(Modifier.weight(1f).fillMaxHeight()) {
                if (sub != SubPage.NONE) {
                    TvPage(sub.name.lowercase().replaceFirstChar { it.uppercase() }) {
                        val s by vm.settings.collectAsStateWithLifecycle()
                        when (sub) {
                            SubPage.DNS -> DnsContent(s, vm)
                            SubPage.UPDATES -> UpdateContent(hiltViewModel())
                            SubPage.KILLSWITCH -> KillSwitchContent(s.killSwitch) { vm.setKillSwitch(it) }
                            SubPage.PRIVACY -> InfoBlock("Privacy Policy", InfoText.privacy)
                            SubPage.DATA -> InfoBlock("Data Handling", InfoText.data)
                            SubPage.SECURITY -> InfoBlock("Security Information", InfoText.security)
                            SubPage.ABOUT -> InfoBlock("AbgMieVPN", InfoText.about)
                            SubPage.NONE -> Unit
                        }
                    }
                } else Crossfade(tab, animationSpec = tween(200), label = "tv") { t ->
                    when (t) {
                        0 -> TvHome(vm, onLocations = { tab = 1 }, onAdd = { importOpen = true })
                        1 -> TvLocations(vm, onAdd = { importOpen = true }, onConnected = { tab = 0 })
                        2 -> TvPage("Statistics") {
                            val v by vm.vpnState.collectAsStateWithLifecycle()
                            val u by vm.usage.collectAsStateWithLifecycle()
                            val s by vm.sessions.collectAsStateWithLifecycle()
                            StatsContent(v, u, s, onReset = { vm.resetStats() }, wide = true)
                        }
                        else -> TvPage("Settings") {
                            val s by vm.settings.collectAsStateWithLifecycle()
                            val user by vm.user.collectAsStateWithLifecycle()
                            SettingsContent(s, user, vm.authConfigured, vm) { sub = it }
                        }
                    }
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(24.dp))
        if (importOpen) ImportServerDialog(vm) { importOpen = false }
    }
}

@Composable
private fun TvPage(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(end = 12.dp)) {
        Text(title, color = Abg.colors.text, fontSize = 34.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        content()
        Spacer(Modifier.height(40.dp))
    }
}

/** Sidebar entry: blue rounded selection, slight scale and visible ring on D-pad focus. */
@Composable
private fun SidebarItem(label: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val focused by src.collectIsFocusedAsState()
    val scale by animateFloatAsState(if (focused) 1.06f else 1f, spring(Spring.DampingRatioMediumBouncy), label = "s")
    val bg by animateColorAsState(
        when { selected -> Brand.ElectricBlue; focused -> Brand.ElectricBlue.copy(alpha = 0.25f); else -> Color.Transparent }, tween(200), label = "bg"
    )
    Row(
        Modifier.fillMaxWidth().scale(scale).clip(RoundedCornerShape(18.dp)).background(bg)
            .border(if (focused) 2.5.dp else 0.dp, if (focused) Brand.Cyan else Color.Transparent, RoundedCornerShape(18.dp))
            .clickable(interactionSource = src, indication = null, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = if (selected) Brand.CyanSoft else Abg.colors.textDim, modifier = Modifier.size(26.dp))
        Spacer(Modifier.width(14.dp))
        Text(label, color = if (selected) Color.White else Abg.colors.text, fontSize = 19.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium)
    }
}

/** TV Quick Connect: last working server + status + giant Connect button focused on launch (press OK). */
@Composable
fun TvHome(vm: MainViewModel, onLocations: () -> Unit, onAdd: () -> Unit) {
    val v by vm.vpnState.collectAsStateWithLifecycle()
    val selected by vm.selected.collectAsStateWithLifecycle()
    val servers by vm.servers.collectAsStateWithLifecycle()
    val now = rememberNow(v.status.isActive)
    val orbFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { orbFocus.requestFocus() } }

    Row(Modifier.fillMaxSize()) {
        // CENTER: connect panel
        Column(Modifier.weight(1.1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            StatusPill(v.status, large = true)
            Spacer(Modifier.height(16.dp))
            ConnectOrb(v.status, 300.dp, onClick = { vm.togglePower() }, focusRequester = orbFocus)
            Text(
                if (v.status.isActive) "Press OK to disconnect" else "Press OK to connect",
                color = Abg.colors.textDim, fontSize = 16.sp,
            )
            v.error?.let { Spacer(Modifier.height(10.dp)); Text(it, color = Brand.Danger, fontSize = 15.sp) }
            if (selected == null) {
                Spacer(Modifier.height(14.dp))
                Text("No server selected – pick a VPN Gate relay or add your own server.", color = Brand.Warning, fontSize = 16.sp)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PrimaryButton("Locations", onLocations, icon = Icons.Rounded.Public)
                    SecondaryButton("Add server", onAdd, icon = Icons.Rounded.Add)
                }
            }
        }
        Spacer(Modifier.width(24.dp))
        // RIGHT: server + live info
        Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.Center) {
            val country = CountryData.find(selected?.countryCode)
            GlassCard(Modifier.fillMaxWidth(), onClick = onLocations, highlight = v.status.isActive) {
                Text("SELECTED SERVER", color = Abg.colors.textDim, fontSize = 13.sp, letterSpacing = 1.4.sp)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(country?.flag ?: "🌐", fontSize = 44.sp)
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(country?.name ?: "Choose a location", color = Abg.colors.text, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
                        Text(selected?.let { "${it.title} · ${it.endpoint}" } ?: "Open Locations", color = Abg.colors.textDim, fontSize = 15.sp, maxLines = 1)
                        Text(selected?.subtitle ?: "", color = Brand.Cyan, fontSize = 13.sp)
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            TvInfo("Duration", v.connectedAt?.takeIf { v.status.isActive }?.let { Fmt.duration(now - it) } ?: "00:00:00", Icons.Rounded.Timer)
            TvInfo("Public IP", v.publicIp ?: "—", Icons.Rounded.Language)
            TvInfo("Download", if (v.hasCounters) Fmt.rate(v.rxRate) + "  ·  " + Fmt.bytes(v.rxBytes) else "n/a (OpenVPN)", Icons.Rounded.SouthEast)
            TvInfo("Upload", if (v.hasCounters) Fmt.rate(v.txRate) + "  ·  " + Fmt.bytes(v.txBytes) else "n/a (OpenVPN)", Icons.Rounded.NorthEast)
            TvInfo("Ping", selected?.pingMs?.let { "$it ms" } ?: "—", Icons.Rounded.NetworkPing)
        }
    }
}

@Composable
private fun TvInfo(label: String, value: String, icon: ImageVector) {
    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(Brand.ElectricBlue.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Brand.ElectricBlue)
        }
        Spacer(Modifier.width(14.dp))
        Text(label, color = Abg.colors.textDim, fontSize = 17.sp, modifier = Modifier.width(120.dp))
        Text(value, color = Abg.colors.text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** TV country browser: horizontal sections per continent, large flags, remote-friendly search. */
@Composable
fun TvLocations(vm: MainViewModel, onAdd: () -> Unit, onConnected: () -> Unit) {
    val q by vm.query.collectAsStateWithLifecycle()
    val servers by vm.servers.collectAsStateWithLifecycle()
    val rows by vm.countryRows.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.continent.value = null }

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Text("Locations", color = Abg.colors.text, fontSize = 34.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                PrimaryButton("Auto Connect", { vm.autoConnect(); onConnected() }, icon = Icons.Rounded.Bolt)
                SecondaryButton("Measure ping", { vm.measurePings() }, icon = Icons.Rounded.NetworkPing)
                SecondaryButton("Add server", onAdd, icon = Icons.Rounded.Add)
                SecondaryButton("Refresh VPN Gate", { vm.refreshGate() }, icon = Icons.Rounded.Refresh)
            }
            OutlinedTextField(
                value = q, onValueChange = { vm.query.value = it }, singleLine = true,
                placeholder = { Text("Search country or city") }, leadingIcon = { Icon(Icons.Rounded.Search, null) },
                shape = CircleShape, modifier = Modifier.fillMaxWidth(0.6f).padding(vertical = 12.dp),
            )
            val gl by vm.gateList.collectAsStateWithLifecycle()
            if (!vm.helperInstalled()) {
                Spacer(Modifier.height(6.dp))
                Text("VPN Gate needs the free helper app \"OpenVPN for Android\".", color = Brand.Warning, fontSize = 15.sp)
                PrimaryButton("Install OpenVPN for Android", { vm.installHelper() }, icon = Icons.Rounded.Download)
            }
            if (gl.servers.isNotEmpty()) {
                SectionTitle("VPN Gate – top public relays (volunteer servers, may log)")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(6.dp)) {
                    items(gl.servers.sortedByDescending { it.score }.take(20), key = { it.id }) { g ->
                        GlassCard(Modifier.width(280.dp), onClick = { vm.connectGate(g); onConnected() }, radius = 22.dp) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(CountryData.find(g.countryCode)?.flag ?: "🌐", fontSize = 34.sp)
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text(g.countryName, color = Abg.colors.text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                    Text(g.hostName, color = Abg.colors.textDim, fontSize = 13.sp, maxLines = 1)
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(com.abgmie.vpn.mobile.gateLine(g), color = Brand.Cyan, fontSize = 13.sp, maxLines = 1)
                        }
                    }
                }
            } else if (gl.error != null) {
                Text(gl.error ?: "", color = Brand.Danger, fontSize = 14.sp)
            }
            if (servers.isNotEmpty()) {
                SectionTitle("My servers")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(6.dp)) {
                    items(servers, key = { it.id }) { s -> TvServerCard(s) { vm.connectTo(s); onConnected() } }
                }
            }
        }
        Continent.entries.forEach { cont ->
            val list = rows.filter { it.country.continent == cont }
            if (list.isNotEmpty()) item(key = cont.name) {
                SectionTitle(cont.label + if (cont == Continent.ANTARCTICA) " (informational)" else "")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(6.dp)) {
                    items(list, key = { it.country.code }) { row ->
                        GlassCard(
                            Modifier.width(210.dp).height(140.dp),
                            onClick = {
                                val best = row.servers.minByOrNull { it.lastPingMs ?: Int.MAX_VALUE }
                                val gate = row.bestGate
                                when {
                                    best != null -> { vm.connectTo(best); onConnected() }
                                    gate != null -> { vm.connectGate(gate); onConnected() }
                                    else -> vm.say("${row.country.name}: Coming Soon")
                                }
                            },
                            highlight = row.connectable, radius = 22.dp,
                        ) {
                            Text(row.country.flag, fontSize = 40.sp)
                            Spacer(Modifier.height(6.dp))
                            Text(row.country.name, color = Abg.colors.text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                            Text(
                                when {
                                    row.servers.isNotEmpty() -> "Connectable · ${row.servers.size}" + (row.bestPing?.let { " · $it ms" } ?: "")
                                    row.connectable -> "VPN Gate · ${row.gates.size}"
                                    else -> "Coming Soon"
                                },
                                color = if (row.connectable) Brand.Success else Abg.colors.textDim, fontSize = 13.sp,
                            )
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(40.dp)) }
    }
}

@Composable
private fun TvServerCard(s: ServerEntity, onClick: () -> Unit) {
    val flag = CountryData.find(s.countryCode)?.flag ?: "🌐"
    GlassCard(Modifier.width(260.dp), onClick = onClick, radius = 22.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(flag, fontSize = 36.sp)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(s.city, color = Abg.colors.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Text(s.name, color = Abg.colors.textDim, fontSize = 13.sp, maxLines = 1)
            }
        }
        Spacer(Modifier.height(8.dp))
        Text("${s.lastPingMs?.let { "$it ms" } ?: "ping n/a"} · load ${s.loadPercent?.let { "$it%" } ?: "unknown"}", color = Brand.Cyan, fontSize = 14.sp)
    }
}
