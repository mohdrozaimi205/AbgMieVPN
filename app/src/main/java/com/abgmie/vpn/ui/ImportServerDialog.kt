package com.abgmie.vpn.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.abgmie.vpn.core.Abg
import com.abgmie.vpn.core.Brand
import com.abgmie.vpn.data.countries.Continent
import com.abgmie.vpn.data.countries.Country
import com.abgmie.vpn.data.countries.CountryData

/**
 * Adds a real server from a WireGuard .conf (file / paste) or an HTTPS link.
 * The user tags it with the country & city where the server actually is.
 */
@Composable
fun ImportServerDialog(vm: MainViewModel, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    var tab by remember { mutableIntStateOf(0) }
    var text by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var city by remember { mutableStateOf("") }
    var country by remember { mutableStateOf<Country?>(null) }
    var countryQuery by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val validation = remember(text) { if (text.isBlank()) null else vm.validate(text) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            text = runCatching {
                ctx.contentResolver.openInputStream(uri)?.use { it.readBytes().take(64_000).toByteArray().toString(Charsets.UTF_8) }
            }.getOrNull().orEmpty()
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.fillMaxWidth(0.94f).widthIn(max = 720.dp).fillMaxHeight(0.92f)
                .clip(RoundedCornerShape(28.dp)).background(Abg.colors.bg).padding(20.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Add VPN server", color = Abg.colors.text, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, "Close", tint = Abg.colors.text) }
            }
            Text(
                "Import a WireGuard config from YOUR server (e.g. created by scripts/wireguard-install.sh). Nothing is added automatically.",
                color = Abg.colors.textDim, fontSize = 13.sp,
            )
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GlassCard(Modifier.weight(1f), onClick = { tab = 0 }, highlight = tab == 0, radius = 16.dp, padding = PaddingValues(12.dp)) {
                    Text("File / Paste", color = Abg.colors.text, fontWeight = FontWeight.Medium)
                }
                GlassCard(Modifier.weight(1f), onClick = { tab = 1 }, highlight = tab == 1, radius = 16.dp, padding = PaddingValues(12.dp)) {
                    Text("HTTPS link", color = Abg.colors.text, fontWeight = FontWeight.Medium)
                }
            }
            Spacer(Modifier.height(12.dp))
            if (tab == 0) {
                SecondaryButton("Choose .conf file", { picker.launch(arrayOf("*/*")) }, Modifier.fillMaxWidth(), Icons.Rounded.FileOpen)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = text, onValueChange = { text = it },
                    label = { Text("[Interface] … [Peer] …") },
                    textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp, max = 240.dp),
                )
                when {
                    text.isBlank() -> {}
                    validation == null -> Text("✓ Valid WireGuard config", color = Brand.Success, fontSize = 13.sp, modifier = Modifier.padding(4.dp))
                    else -> Text(validation, color = Brand.Danger, fontSize = 13.sp, modifier = Modifier.padding(4.dp))
                }
            } else {
                OutlinedTextField(
                    value = url, onValueChange = { url = it }, singleLine = true,
                    label = { Text("https://… (link to your .conf)") }, modifier = Modifier.fillMaxWidth(),
                )
                Text("Use a private link you control. Plain HTTP is refused.", color = Abg.colors.textDim, fontSize = 12.sp, modifier = Modifier.padding(4.dp))
            }

            SectionTitle("Location of this server")
            if (country == null) {
                OutlinedTextField(
                    value = countryQuery, onValueChange = { countryQuery = it }, singleLine = true,
                    label = { Text("Search country") }, leadingIcon = { Icon(Icons.Rounded.Search, null) },
                    modifier = Modifier.fillMaxWidth(),
                )
                val matches = remember(countryQuery) {
                    val q = countryQuery.trim().lowercase()
                    CountryData.all.filter { it.continent != Continent.ANTARCTICA && (q.isEmpty() || it.name.lowercase().contains(q) || it.code.lowercase() == q) }.take(6)
                }
                matches.forEach { c ->
                    GlassCard(Modifier.fillMaxWidth().padding(vertical = 3.dp), onClick = { country = c; if (city.isBlank()) city = c.cities.firstOrNull().orEmpty() }, radius = 14.dp, padding = PaddingValues(12.dp)) {
                        Text("${c.flag}  ${c.name}", color = Abg.colors.text)
                    }
                }
            } else {
                GlassCard(Modifier.fillMaxWidth(), onClick = { country = null }, highlight = true, radius = 16.dp, padding = PaddingValues(14.dp)) {
                    Text("${country!!.flag}  ${country!!.name}  ·  change", color = Abg.colors.text, fontWeight = FontWeight.Medium)
                }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = city, onValueChange = { city = it }, singleLine = true, label = { Text("City") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, label = { Text("Server name (optional)") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(16.dp))
            val ready = country != null && city.isNotBlank() && !busy && (if (tab == 0) text.isNotBlank() && validation == null else url.startsWith("https://"))
            PrimaryButton(if (busy) "Saving…" else "Save server", {
                busy = true
                val cc = country!!.code
                if (tab == 0) vm.importText(text, name, cc, city) { ok -> busy = false; if (ok) onDismiss() }
                else vm.importUrl(url, name, cc, city) { ok -> busy = false; if (ok) onDismiss() }
            }, Modifier.fillMaxWidth(), enabled = ready, icon = Icons.Rounded.Add)
        }
    }
}
