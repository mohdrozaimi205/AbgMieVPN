package com.abgmie.vpn.mobile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.abgmie.vpn.core.Abg
import com.abgmie.vpn.core.Brand
import com.abgmie.vpn.core.Fmt
import com.abgmie.vpn.data.countries.CountryData
import com.abgmie.vpn.data.db.ServerEntity
import com.abgmie.vpn.domain.Target
import com.abgmie.vpn.domain.VpnState
import com.abgmie.vpn.domain.VpnStatus
import com.abgmie.vpn.ui.*

@Composable
fun HomeScreen(vm: MainViewModel, wide: Boolean, onOpenLocations: () -> Unit, onAddServer: () -> Unit) {
    val v by vm.vpnState.collectAsStateWithLifecycle()
    val selected by vm.selected.collectAsStateWithLifecycle()
    val servers by vm.servers.collectAsStateWithLifecycle()
    val user by vm.user.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = if (wide) 40.dp else 18.dp, vertical = 10.dp)) {
        // Header
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("AbgMieVPN", color = Abg.colors.text, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                Text("One World. One Connection.", color = Brand.ElectricBlue, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }
            Box(Modifier.size(42.dp).clip(CircleShape).background(Brand.ElectricBlue.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                val photo = user?.photoUrl
                if (photo != null) AsyncImage(photo, "Account", Modifier.fillMaxSize()) else Icon(Icons.Rounded.Person, null, tint = Brand.ElectricBlue)
            }
        }
        Spacer(Modifier.height(16.dp))

        if (wide) {
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    PowerBlock(v, vm, 280.dp)
                    Spacer(Modifier.height(16.dp))
                    ServerCard(selected, v, onOpenLocations)
                }
                Column(Modifier.weight(1f)) {
                    if (servers.isEmpty() && selected == null) NoServerCard(onAddServer, onOpenLocations)
                    LiveMetrics(v, cols = 2)
                }
            }
        } else {
            if (servers.isEmpty() && selected == null) NoServerCard(onAddServer, onOpenLocations)
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) { PowerBlock(v, vm, 230.dp) }
            Spacer(Modifier.height(14.dp))
            ServerCard(selected, v, onOpenLocations)
            Spacer(Modifier.height(6.dp))
            LiveMetrics(v, cols = 2)
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun PowerBlock(v: VpnState, vm: MainViewModel, size: androidx.compose.ui.unit.Dp) {
    val now = rememberNow(v.status.isActive)
    StatusPill(v.status, large = true)
    Spacer(Modifier.height(10.dp))
    ConnectOrb(v.status, size, onClick = { vm.togglePower() })
    Text(
        v.connectedAt?.takeIf { v.status.isActive }?.let { Fmt.duration(now - it) } ?: "00:00:00",
        color = Abg.colors.text, fontSize = 30.sp, fontWeight = FontWeight.Light, letterSpacing = 2.sp,
    )
    Text("Connection duration", color = Abg.colors.textDim, fontSize = 12.sp)
    if (v.status.isActive) {
        Spacer(Modifier.height(10.dp))
        SecondaryButton("Disconnect", { vm.disconnect() }, Modifier.width(220.dp), Icons.Rounded.LinkOff)
    }
    v.error?.let {
        Spacer(Modifier.height(10.dp))
        GlassCard(Modifier.fillMaxWidth(), radius = 18.dp, padding = PaddingValues(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.ErrorOutline, null, tint = Brand.Danger)
                Spacer(Modifier.width(10.dp))
                Text(it, color = Abg.colors.text, fontSize = 13.sp)
            }
        }
    }
}

@Composable
fun ServerCard(s: Target?, v: VpnState, onClick: () -> Unit) {
    val country = CountryData.find(s?.countryCode)
    GlassCard(Modifier.fillMaxWidth(), onClick = onClick, highlight = v.status == VpnStatus.CONNECTED) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(country?.flag ?: "🌐", fontSize = 34.sp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(country?.name ?: "No server selected", color = Abg.colors.text, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                Text(s?.let { "${it.title} · ${it.subtitle}" } ?: "Tap to choose a location", color = Abg.colors.textDim, fontSize = 13.sp, maxLines = 2)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(s?.pingMs?.let { "$it ms" } ?: "— ms", color = Brand.Cyan, fontWeight = FontWeight.SemiBold)
                Icon(Icons.Rounded.ChevronRight, null, tint = Abg.colors.textDim)
            }
        }
    }
}

@Composable
fun LiveMetrics(v: VpnState, cols: Int) {
    val na = "n/a (OpenVPN)"
    val tiles = listOf(
        Triple("Download", if (v.hasCounters) Fmt.rate(v.rxRate) else na, Icons.Rounded.SouthEast),
        Triple("Upload", if (v.hasCounters) Fmt.rate(v.txRate) else na, Icons.Rounded.NorthEast),
        Triple("Total downloaded", if (v.hasCounters) Fmt.bytes(v.rxBytes) else na, Icons.Rounded.CloudDownload),
        Triple("Total uploaded", if (v.hasCounters) Fmt.bytes(v.txBytes) else na, Icons.Rounded.CloudUpload),
        Triple("Public IP", v.publicIp ?: "—", Icons.Rounded.Language),
        Triple("VPN server", v.endpointText ?: "—", Icons.Rounded.Dns),
    )
    TileGrid(tiles, cols)
}

@Composable
fun NoServerCard(onAdd: () -> Unit, onBrowse: (() -> Unit)? = null) {
    GlassCard(Modifier.fillMaxWidth().padding(bottom = 14.dp), highlight = true) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.CloudOff, null, tint = Brand.Warning)
            Spacer(Modifier.width(10.dp))
            Text("No server selected yet", color = Abg.colors.text, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "Pick a free VPN Gate public server in Locations, or add your own WireGuard server (most private).",
            color = Abg.colors.textDim, fontSize = 13.sp, textAlign = TextAlign.Start,
        )
        Spacer(Modifier.height(12.dp))
        if (onBrowse != null) {
            PrimaryButton("Browse VPN Gate servers", onBrowse, Modifier.fillMaxWidth(), icon = Icons.Rounded.Public)
            Spacer(Modifier.height(8.dp))
            SecondaryButton("Add my own server", onAdd, Modifier.fillMaxWidth(), Icons.Rounded.Add)
        } else {
            PrimaryButton("Add server", onAdd, Modifier.fillMaxWidth(), icon = Icons.Rounded.Add)
        }
    }
}
