package com.abgmie.vpn.ui

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.abgmie.vpn.BuildConfig
import com.abgmie.vpn.core.Abg
import com.abgmie.vpn.core.Brand
import com.abgmie.vpn.core.Fmt
import com.abgmie.vpn.domain.AppSettings
import com.abgmie.vpn.domain.DnsMode

private data class DnsPreset(val name: String, val v4: String, val v4b: String, val v6: String, val note: String)

private val presets = listOf(
    DnsPreset("Cloudflare", "1.1.1.1", "1.0.0.1", "2606:4700:4700::1111", "Fast. Cloudflare states it does not sell data and purges logs within 24h."),
    DnsPreset("Quad9", "9.9.9.9", "149.112.112.112", "2620:fe::fe", "Blocks known malicious domains. Swiss non-profit."),
    DnsPreset("Google", "8.8.8.8", "8.8.4.4", "2001:4860:4860::8888", "Reliable, but Google keeps temporary logs."),
    DnsPreset("AdGuard", "94.140.14.14", "94.140.15.15", "2a10:50c0::ad1:ff", "Blocks ads and trackers at DNS level."),
)

@Composable
fun DnsContent(settings: AppSettings, vm: MainViewModel) {
    var mode by remember(settings.dnsMode) { mutableStateOf(settings.dnsMode) }
    var v4 by remember(settings.dnsV4) { mutableStateOf(settings.dnsV4) }
    var v4b by remember(settings.dnsV4b) { mutableStateOf(settings.dnsV4b) }
    var v6 by remember(settings.dnsV6) { mutableStateOf(settings.dnsV6) }
    Column {
        SettingRow("Automatic DNS", "Use the DNS from your server config", Icons.Rounded.AutoMode, mode == DnsMode.AUTOMATIC, { mode = DnsMode.AUTOMATIC })
        SettingRow("Custom DNS", "Choose a provider or type addresses", Icons.Rounded.Tune, mode == DnsMode.CUSTOM, { mode = DnsMode.CUSTOM })
        if (mode == DnsMode.CUSTOM) {
            SectionTitle("Providers")
            presets.forEach { p ->
                SettingRow(p.name, "${p.v4} · ${p.note}", Icons.Rounded.Dns, onClick = { v4 = p.v4; v4b = p.v4b; v6 = p.v6 })
            }
            SectionTitle("Addresses")
            DnsField("Primary IPv4 DNS", v4) { v4 = it }
            DnsField("Secondary IPv4 DNS", v4b) { v4b = it }
            DnsField("IPv6 DNS (optional)", v6) { v6 = it }
        }
        Spacer(Modifier.height(12.dp))
        PrimaryButton("Save DNS", { vm.saveDns(mode, v4, v4b, v6) }, Modifier.fillMaxWidth(), icon = Icons.Rounded.Check)
        SectionTitle("Status")
        InfoBlock(
            if (settings.dnsMode == DnsMode.AUTOMATIC) "Active: Automatic" else "Active: Custom – " + listOf(settings.dnsV4, settings.dnsV4b, settings.dnsV6).filter { it.isNotBlank() }.joinToString(", "),
            "DNS servers are written into the WireGuard [Interface], so Android sends DNS queries through the encrypted tunnel to these resolvers. Changes apply on the next connection.\n\n" +
                "Privacy: a third-party DNS provider can see every domain name you look up. Choose one you trust, or run your own resolver on your VPN server (e.g. 10.8.0.1) so lookups never leave your infrastructure. AbgMieVPN itself never logs DNS queries."
        )
    }
}

@Composable
private fun DnsField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    )
}

@Composable
fun UpdateContent(vm: UpdateViewModel) {
    val ui by vm.ui.collectAsState()
    val ctx = LocalContext.current
    LaunchedEffect(Unit) {
        vm.launches.collect { i: Intent -> runCatching { ctx.startActivity(i) } }
    }
    Column {
        GlassCard(Modifier.fillMaxWidth()) {
            Row {
                Column(Modifier.weight(1f)) {
                    Text("Current Version", color = Abg.colors.textDim, fontSize = 12.sp)
                    Text(BuildConfig.VERSION_NAME, color = Abg.colors.text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                }
                Column(Modifier.weight(1f)) {
                    Text("Latest Version", color = Abg.colors.textDim, fontSize = 12.sp)
                    Text(ui.release?.version ?: "—", color = Abg.colors.text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            Spacer(Modifier.height(10.dp))
            val r = ui.release
            val status = when {
                ui.checking -> "Checking GitHub…"
                r == null && ui.checked -> ui.error ?: "Unable to check."
                r == null -> "Not checked yet"
                r.isNewer -> "Update Available"
                else -> "Up to date"
            }
            Text(status, color = if (r?.isNewer == true) Brand.Cyan else Abg.colors.text, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(12.dp))
        PrimaryButton(if (ui.checking) "Checking…" else "Check for Updates", { vm.check() }, Modifier.fillMaxWidth(), enabled = !ui.checking, icon = Icons.Rounded.Refresh)

        ui.release?.let { r ->
            SectionTitle("Release ${r.version}")
            GlassCard(Modifier.fillMaxWidth()) {
                Text("Release date: ${r.publishedAt}", color = Abg.colors.textDim, fontSize = 13.sp)
                Text("Download size: ${Fmt.bytes(r.sizeBytes)}", color = Abg.colors.textDim, fontSize = 13.sp)
                Text("Checksum: " + if (r.sha256Url != null) "SHA-256 published ✓" else "missing – install blocked", color = Abg.colors.textDim, fontSize = 13.sp)
                Spacer(Modifier.height(10.dp))
                Text(r.notes, color = Abg.colors.text, fontSize = 14.sp, lineHeight = 20.sp)
            }
            Spacer(Modifier.height(12.dp))
            ui.progress?.let { p ->
                LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth(), color = Brand.ElectricBlue)
                Text("Downloading ${(p * 100).toInt()}%", color = Abg.colors.textDim, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
            }
            if (ui.verifiedFile != null) {
                PrimaryButton("Install (verified)", { vm.install() }, Modifier.fillMaxWidth(), icon = Icons.Rounded.InstallMobile)
            } else if (r.isNewer && ui.progress == null) {
                PrimaryButton("Download Update", { vm.download() }, Modifier.fillMaxWidth(), enabled = r.sha256Url != null, icon = Icons.Rounded.Download)
            }
        }
        ui.error?.takeIf { ui.release != null }?.let { Text(it, color = Brand.Danger, modifier = Modifier.padding(8.dp)) }
        Spacer(Modifier.height(10.dp))
        Text(
            "Updates come only from github.com/${BuildConfig.GITHUB_REPO}. Android always asks you to confirm installation; your settings and servers are kept.",
            color = Abg.colors.textDim, fontSize = 12.sp, modifier = Modifier.padding(6.dp),
        )
    }
}
