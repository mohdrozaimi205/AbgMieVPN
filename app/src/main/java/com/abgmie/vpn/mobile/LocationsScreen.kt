package com.abgmie.vpn.mobile

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.abgmie.vpn.core.Abg
import com.abgmie.vpn.core.Brand
import com.abgmie.vpn.data.countries.Continent
import com.abgmie.vpn.data.countries.CountryData
import com.abgmie.vpn.data.db.ServerEntity
import com.abgmie.vpn.domain.SortMode
import com.abgmie.vpn.ui.*

@Composable
fun LocationsScreen(vm: MainViewModel, wide: Boolean, onAdd: () -> Unit, onConnected: () -> Unit) {
    val rows by vm.countryRows.collectAsStateWithLifecycle()
    val servers by vm.servers.collectAsStateWithLifecycle()
    val q by vm.query.collectAsStateWithLifecycle()
    val cont by vm.continent.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val v by vm.vpnState.collectAsStateWithLifecycle()
    var expanded by remember { mutableStateOf<String?>(null) }
    val favs = servers.filter { it.favorite }
    val recent = servers.filter { it.lastConnectedAt != null }.sortedByDescending { it.lastConnectedAt }.take(3)
    val hp = if (wide) 40.dp else 18.dp

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = hp, vertical = 10.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Locations", style = MaterialTheme.typography.displaySmall, color = Abg.colors.text, modifier = Modifier.weight(1f))
                IconButton(onClick = { vm.measurePings() }) { Icon(Icons.Rounded.NetworkPing, "Measure ping", tint = Brand.ElectricBlue) }
                IconButton(onClick = onAdd) { Icon(Icons.Rounded.AddCircle, "Add server", tint = Brand.ElectricBlue) }
            }
            OutlinedTextField(
                value = q, onValueChange = { vm.query.value = it }, singleLine = true,
                placeholder = { Text("Search country or city") }, leadingIcon = { Icon(Icons.Rounded.Search, null) },
                shape = CircleShape, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            )
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(cont == null, { vm.continent.value = null }, label = { Text("All") })
                Continent.entries.forEach { c -> FilterChip(cont == c, { vm.continent.value = c }, label = { Text(c.label) }) }
            }
            Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Sort:", color = Abg.colors.textDim, fontSize = 13.sp)
                AssistChip({ vm.setSort(SortMode.PING) }, { Text("Lowest ping") }, leadingIcon = { if (settings.sort == SortMode.PING) Icon(Icons.Rounded.Check, null) })
                AssistChip({ vm.setSort(SortMode.LOAD) }, { Text("Lowest load") }, leadingIcon = { if (settings.sort == SortMode.LOAD) Icon(Icons.Rounded.Check, null) })
            }
            Spacer(Modifier.height(10.dp))
            GlassCard(Modifier.fillMaxWidth(), onClick = { vm.autoConnect(); onConnected() }, highlight = true) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(44.dp).clip(CircleShape).background(Brand.ElectricBlue), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Bolt, null, tint = androidx.compose.ui.graphics.Color.White)
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("AUTO CONNECT", color = Abg.colors.text, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                        Text("Best server by real measured latency & health", color = Abg.colors.textDim, fontSize = 12.5.sp)
                    }
                }
            }
            if (servers.isEmpty()) {
                Spacer(Modifier.height(10.dp))
                NoServerCard(onAdd)
            }
            if (favs.isNotEmpty()) {
                SectionTitle("Favourites")
                favs.forEach { ServerRow(it, vm, v.server?.id == it.id && v.status.isActive) { vm.connectTo(it); onConnected() } }
            }
            if (recent.isNotEmpty()) {
                SectionTitle("Recently connected")
                recent.forEach { ServerRow(it, vm, v.server?.id == it.id && v.status.isActive) { vm.connectTo(it); onConnected() } }
            }
            SectionTitle("All countries (${rows.size})")
        }
        items(rows, key = { it.country.code }) { row ->
            val open = expanded == row.country.code
            GlassCard(
                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                onClick = { expanded = if (open) null else row.country.code },
                radius = 18.dp, padding = PaddingValues(14.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(row.country.flag, fontSize = 28.sp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(row.country.name, color = Abg.colors.text, fontWeight = FontWeight.Medium)
                        Text(
                            if (row.servers.isNotEmpty()) row.servers.joinToString { it.city }.take(60) else row.country.cities.joinToString().ifBlank { row.country.continent.label },
                            color = Abg.colors.textDim, fontSize = 12.sp, maxLines = 1,
                        )
                    }
                    AvailabilityBadge(row)
                }
                AnimatedVisibility(open) {
                    Column(Modifier.padding(top = 10.dp)) {
                        if (row.servers.isEmpty()) {
                            Text(
                                if (row.country.continent == Continent.ANTARCTICA) "Informational only – no commercial server availability is assumed."
                                else "Coming Soon – no verified server in this country yet. Add your own server to enable it.",
                                color = Abg.colors.textDim, fontSize = 13.sp,
                            )
                        }
                        row.servers.forEach { s -> ServerRow(s, vm, v.server?.id == s.id && v.status.isActive) { vm.connectTo(s); onConnected() } }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun AvailabilityBadge(row: CountryRow) {
    val ok = row.connectable
    Box(
        Modifier.clip(CircleShape).background((if (ok) Brand.Success else Abg.colors.textDim).copy(alpha = 0.14f)).padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            if (ok) "Connectable · ${row.servers.size}" + (row.bestPing?.let { " · $it ms" } ?: "") else "Coming Soon",
            color = if (ok) Brand.Success else Abg.colors.textDim, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
fun ServerRow(s: ServerEntity, vm: MainViewModel, active: Boolean, onConnect: () -> Unit) {
    var confirmDelete by remember { mutableStateOf(false) }
    val flag = CountryData.find(s.countryCode)?.flag ?: "🌐"
    GlassCard(Modifier.fillMaxWidth().padding(vertical = 3.dp), onClick = onConnect, radius = 16.dp, highlight = active, padding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(flag, fontSize = 22.sp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("${s.city} · ${s.name}", color = Abg.colors.text, fontWeight = FontWeight.Medium, maxLines = 1)
                Text(
                    "${s.lastPingMs?.let { "$it ms" } ?: "ping n/a"} · load ${s.loadPercent?.let { "$it%" } ?: "unknown"}" +
                        when (s.lastOk) { true -> " · healthy"; false -> " · last attempt failed"; null -> "" },
                    color = Abg.colors.textDim, fontSize = 12.sp,
                )
            }
            IconButton(onClick = { vm.toggleFavorite(s) }) {
                Icon(if (s.favorite) Icons.Rounded.Star else Icons.Rounded.StarBorder, "Favourite", tint = if (s.favorite) Brand.Warning else Abg.colors.textDim)
            }
            IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Rounded.DeleteOutline, "Delete", tint = Abg.colors.textDim) }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            confirmButton = { TextButton({ confirmDelete = false; vm.deleteServer(s) }) { Text("Delete", color = Brand.Danger) } },
            dismissButton = { TextButton({ confirmDelete = false }) { Text("Cancel") } },
            title = { Text("Delete ${s.name}?") },
            text = { Text("The encrypted config for this server will be removed from this device.") },
        )
    }
}
