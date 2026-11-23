package com.abgmie.vpn.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.automirrored.rounded.Login
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.abgmie.vpn.BuildConfig
import com.abgmie.vpn.auth.UserProfile
import com.abgmie.vpn.core.Abg
import com.abgmie.vpn.core.Brand
import com.abgmie.vpn.domain.AppSettings
import com.abgmie.vpn.domain.ThemeMode

enum class SubPage { NONE, DNS, UPDATES, PRIVACY, DATA, SECURITY, ABOUT, KILLSWITCH }

@Composable
fun SettingsContent(
    settings: AppSettings,
    user: UserProfile?,
    authConfigured: Boolean,
    vm: MainViewModel,
    open: (SubPage) -> Unit,
) {
    val ctx = LocalContext.current
    Column {
        SectionTitle("Account")
        AccountCard(user, authConfigured, onSignIn = { vm.signIn(ctx) }, onSignOut = { vm.signOut() })

        SectionTitle("Connection")
        SettingRow("Auto Connect", "Connect on app launch to the last or best server", Icons.Rounded.Bolt, settings.autoConnect, { vm.setAutoConnect(!settings.autoConnect) })
        SettingRow("Kill Switch", if (settings.killSwitch) "Enabled – tap for Android setup" else "Block traffic outside the VPN", Icons.Rounded.GppGood, settings.killSwitch, {
            if (!settings.killSwitch) vm.setKillSwitch(true)
            open(SubPage.KILLSWITCH)
        })
        SettingRow("Reconnect automatically", "Re-establish the tunnel after drops & network changes", Icons.Rounded.Autorenew, settings.autoReconnect, { vm.setAutoReconnect(!settings.autoReconnect) })
        SettingRow("DNS settings", settings.dnsMode.name.lowercase().replaceFirstChar { it.uppercase() }, Icons.Rounded.Dns, onClick = { open(SubPage.DNS) })

        SectionTitle("Appearance")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf(ThemeMode.LIGHT to "Light", ThemeMode.DARK to "Dark", ThemeMode.SYSTEM to "System").forEach { (m, label) ->
                GlassCard(Modifier.weight(1f), onClick = { vm.setTheme(m) }, radius = 18.dp, highlight = settings.theme == m, padding = PaddingValues(14.dp)) {
                    Icon(
                        when (m) { ThemeMode.LIGHT -> Icons.Rounded.LightMode; ThemeMode.DARK -> Icons.Rounded.DarkMode; else -> Icons.Rounded.SettingsBrightness },
                        null, tint = if (settings.theme == m) Brand.ElectricBlue else Abg.colors.textDim,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(label, color = Abg.colors.text, fontWeight = if (settings.theme == m) FontWeight.SemiBold else FontWeight.Normal)
                }
            }
        }

        SectionTitle("Application")
        SettingRow("Check for Updates", "From official GitHub Releases", Icons.Rounded.SystemUpdate, onClick = { open(SubPage.UPDATES) })
        SettingRow("Current Version", BuildConfig.VERSION_NAME, Icons.Rounded.Info, onClick = { open(SubPage.UPDATES) })
        SettingRow("Release Notes", "What's new in the latest release", Icons.AutoMirrored.Rounded.Notes, onClick = { open(SubPage.UPDATES) })
        SettingRow("About AbgMieVPN", "One World. One Connection.", Icons.Rounded.Public, onClick = { open(SubPage.ABOUT) })

        SectionTitle("Privacy")
        SettingRow("Privacy Policy", null, Icons.Rounded.PrivacyTip, onClick = { open(SubPage.PRIVACY) })
        SettingRow("Data Handling", null, Icons.Rounded.Storage, onClick = { open(SubPage.DATA) })
        SettingRow("Security Information", null, Icons.Rounded.Lock, onClick = { open(SubPage.SECURITY) })
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun AccountCard(user: UserProfile?, configured: Boolean, onSignIn: () -> Unit, onSignOut: () -> Unit) {
    GlassCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(54.dp).clip(CircleShape).background(Brand.ElectricBlue.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                if (user?.photoUrl != null) AsyncImage(model = user.photoUrl, contentDescription = "Profile picture", modifier = Modifier.fillMaxSize())
                else Icon(Icons.Rounded.Person, null, tint = Brand.ElectricBlue)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(user?.name ?: "Local mode", color = Abg.colors.text, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                Text(
                    user?.email ?: if (configured) "Sign in with Google (optional)" else "Google Sign-In not configured – VPN works fully offline of accounts",
                    color = Abg.colors.textDim, fontSize = 12.5.sp,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        if (user == null) {
            PrimaryButton("Sign in with Google", onSignIn, Modifier.fillMaxWidth(), enabled = configured, icon = Icons.AutoMirrored.Rounded.Login)
        } else {
            SecondaryButton("Sign out", onSignOut, Modifier.fillMaxWidth(), Icons.AutoMirrored.Rounded.Logout)
        }
    }
}

@Composable
fun KillSwitchContent(enabled: Boolean, onToggle: (Boolean) -> Unit) {
    val ctx = LocalContext.current
    var err by remember { mutableStateOf<String?>(null) }
    Column {
        SettingRow("Kill Switch", if (enabled) "Enabled" else "Disabled", Icons.Rounded.GppGood, enabled, { onToggle(!enabled) })
        InfoBlock(
            "How the kill switch works on Android",
            """
            Android does not let any app block your internet by itself. A real kill switch is provided by the system:

            1. Open Android VPN settings (button below).
            2. Tap the gear ⚙ next to AbgMieVPN.
            3. Turn ON "Always-on VPN".
            4. Turn ON "Block connections without VPN".

            With this enabled, Android blocks all traffic whenever the AbgMieVPN tunnel is down, so nothing silently falls back to your normal connection. Inside the app, the kill switch also keeps retrying the tunnel instead of giving up.

            VPN Gate (OpenVPN): the tunnel belongs to "OpenVPN for Android", so set Always-on + Block connections on THAT app instead.

            Limitations: some TV boxes and custom ROMs (Android TV, some Xiaomi/Honor builds) hide this screen or the "Block connections" option. In that case only the in-app reconnect protection is available.
            """.trimIndent()
        )
        Spacer(Modifier.height(12.dp))
        PrimaryButton("Open Android VPN settings", {
            try { ctx.startActivity(Intent(Settings.ACTION_VPN_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            catch (e: ActivityNotFoundException) { err = "This device does not provide the Android VPN settings screen." }
        }, Modifier.fillMaxWidth(), icon = Icons.AutoMirrored.Rounded.OpenInNew)
        err?.let { Text(it, color = Brand.Danger, modifier = Modifier.padding(8.dp)) }
    }
}

@Composable
fun InfoBlock(title: String, body: String) {
    GlassCard(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(title, color = Abg.colors.text, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
        Spacer(Modifier.height(8.dp))
        Text(body, color = Abg.colors.textDim, fontSize = 14.sp, lineHeight = 21.sp)
    }
}

object InfoText {
    val privacy = """
        AbgMieVPN is a personal VPN client. It connects to WireGuard servers you configure yourself, or – if you choose – to VPN Gate public relays.

        • No browsing activity is logged.
        • No DNS queries are logged.
        • No user data is sold. No advertising SDKs. No analytics trackers.
        • VPN Gate relays are run by volunteers (University of Tsukuba academic experiment). The relay operator and VPN Gate can see and log your connection (most list a 2-week log policy). Do not use them for banking or private accounts.
        • A VPN improves privacy on untrusted networks, but it does NOT make you completely anonymous: your VPN server provider, websites you sign in to and browser fingerprinting can still identify you.
    """.trimIndent()

    val data = """
        Stored on this device only:
        • Your WireGuard server configs – encrypted with AES-256-GCM using a key inside the Android Keystore.
        • Settings (theme, DNS, toggles) in Android DataStore.
        • Usage statistics: per-session start/end time, server name and byte totals. You can reset them any time.

        Google Sign-In (optional, via Firebase Authentication):
        • Firebase stores your Google account name, email, profile picture URL and a user ID.
        • Your Google password is never seen or stored by AbgMieVPN.
        • Cloud sync is not enabled in this version – server configs and statistics never leave your device.

        Network requests made by the app:
        • Your own VPN servers (WireGuard, UDP).
        • api.ipify.org – only to display your current public IP.
        • api.github.com / github.com – only when you check for updates.
        • www.vpngate.net – to download the public relay list (only if you use VPN Gate).

        VPN Gate connections run through the free app "OpenVPN for Android" (de.blinkt.openvpn) via its official remote-control API. AbgMieVPN passes it the relay's OpenVPN config and reads the connection status.
    """.trimIndent()

    val security = """
        • WireGuard (ChaCha20-Poly1305, Curve25519) via the official WireGuard Android library and Android VpnService.
        • "Connected" is shown only after a real WireGuard handshake with your server is verified.
        • Private keys are never written to logs and never bundled in the APK.
        • No administrator credentials are embedded in the app.
        • Updates are downloaded only from this project's GitHub Releases and verified with SHA-256 before Android's installer opens. Nothing is installed silently.
        • Use the Kill Switch (Android Always-on VPN + Block connections without VPN) to prevent leaks.
    """.trimIndent()

    val about = """
        AbgMieVPN ${BuildConfig.VERSION_NAME}
        One World. One Connection.

        A personal WireGuard VPN for Android phones, tablets, Android TV, Google TV and TV boxes (e.g. Xiaomi Mi Box S).

        No subscriptions, no ads, no quotas, no throttling by the app. Real speed and data allowance depend on your own server (VPS capacity, hosting provider limits, network quality and bandwidth).
    """.trimIndent()
}
