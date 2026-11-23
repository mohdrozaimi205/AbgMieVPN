package com.abgmie.vpn.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.abgmie.vpn.core.Abg
import com.abgmie.vpn.core.Brand
import com.abgmie.vpn.core.Fmt
import com.abgmie.vpn.data.countries.CountryData
import com.abgmie.vpn.data.db.SessionEntity
import com.abgmie.vpn.domain.VpnState
import com.abgmie.vpn.statistics.UsageSummary
import kotlinx.coroutines.delay

@Composable
fun rememberNow(active: Boolean): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(active) { while (active) { now = System.currentTimeMillis(); delay(1000) } }
    return now
}

/** Statistics dashboard – every figure is measured from the WireGuard tunnel and stored only on this device. */
@Composable
fun StatsContent(vpn: VpnState, usage: UsageSummary, sessions: List<SessionEntity>, onReset: () -> Unit, wide: Boolean) {
    val now = rememberNow(vpn.status.isActive)
    var confirm by remember { mutableStateOf(false) }
    Column {
        SectionTitle("Live")
        val tiles = listOf(
            Triple("Download speed", Fmt.rate(vpn.rxRate), Icons.Rounded.SouthEast),
            Triple("Upload speed", Fmt.rate(vpn.txRate), Icons.Rounded.NorthEast),
            Triple("Downloaded (session)", Fmt.bytes(vpn.rxBytes), Icons.Rounded.CloudDownload),
            Triple("Uploaded (session)", Fmt.bytes(vpn.txBytes), Icons.Rounded.CloudUpload),
            Triple("Duration", vpn.connectedAt?.let { Fmt.duration(now - it) } ?: "—", Icons.Rounded.Timer),
            Triple("Status", vpn.status.label, Icons.Rounded.Shield),
        )
        TileGrid(tiles, if (wide) 3 else 2)

        SectionTitle("Usage")
        GlassCard(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                UsageCol("Today", usage.todayRx, usage.todayTx)
                UsageCol("7 days", usage.weekRx, usage.weekTx)
                UsageCol("This month", usage.monthRx, usage.monthTx)
            }
            Spacer(Modifier.height(18.dp))
            UsageBars(usage.last7.map { Fmt.day(it.first) to it.second })
        }

        SectionTitle("Server usage")
        val perServer = sessions.groupBy { it.serverName }.map { (k, v) -> Triple(k, v.size, v.sumOf { it.rxBytes + it.txBytes }) }
            .sortedByDescending { it.third }
        if (perServer.isEmpty()) EmptyNote("No sessions recorded yet.")
        perServer.take(8).forEach { (name, count, bytes) ->
            GlassCard(Modifier.fillMaxWidth().padding(vertical = 4.dp), radius = 18.dp, padding = PaddingValues(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(name, color = Abg.colors.text, modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
                    Text("$count sessions · ${Fmt.bytes(bytes)}", color = Abg.colors.textDim, fontSize = 13.sp)
                }
            }
        }

        SectionTitle("Connection history")
        if (sessions.isEmpty()) EmptyNote("Your connection history will appear here.")
        sessions.take(25).forEach { s ->
            val flag = CountryData.find(s.countryCode)?.flag ?: "🌐"
            GlassCard(Modifier.fillMaxWidth().padding(vertical = 4.dp), radius = 18.dp, padding = PaddingValues(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(flag, fontSize = 24.sp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("${s.serverName} · ${s.city}", color = Abg.colors.text, fontWeight = FontWeight.Medium)
                        Text(Fmt.date(s.startedAt) + "  ·  " + (s.endedAt?.let { Fmt.duration(it - s.startedAt) } ?: "active"), color = Abg.colors.textDim, fontSize = 12.sp)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("↓ " + Fmt.bytes(s.rxBytes), color = Abg.colors.text, fontSize = 12.sp)
                        Text("↑ " + Fmt.bytes(s.txBytes), color = Abg.colors.textDim, fontSize = 12.sp)
                    }
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        SecondaryButton("Reset local statistics", { confirm = true }, Modifier.fillMaxWidth(), Icons.Rounded.DeleteSweep)
        Text(
            "Only byte counters and connection times are stored, on this device only. No browsing history or DNS queries are ever recorded.",
            color = Abg.colors.textDim, fontSize = 12.sp, modifier = Modifier.padding(8.dp),
        )
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            confirmButton = { TextButton({ confirm = false; onReset() }) { Text("Reset", color = Brand.Danger) } },
            dismissButton = { TextButton({ confirm = false }) { Text("Cancel") } },
            title = { Text("Reset statistics?") },
            text = { Text("This permanently deletes the locally stored usage history.") },
        )
    }
}

@Composable
private fun UsageCol(label: String, rx: Long, tx: Long) {
    Column {
        Text(label, color = Abg.colors.textDim, fontSize = 12.sp)
        Text(Fmt.bytes(rx + tx), color = Abg.colors.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Text("↓${Fmt.bytes(rx)} ↑${Fmt.bytes(tx)}", color = Abg.colors.textDim, fontSize = 11.sp)
    }
}

@Composable
fun TileGrid(tiles: List<Triple<String, String, androidx.compose.ui.graphics.vector.ImageVector>>, cols: Int) {
    tiles.chunked(cols).forEach { row ->
        Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            row.forEach { (l, v, i) -> MetricTile(l, v, i, Modifier.weight(1f)) }
            repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

@Composable
fun EmptyNote(text: String) {
    Text(text, color = Abg.colors.textDim, modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp))
}
