package com.abgmie.vpn.mobile

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import com.abgmie.vpn.ui.*

private val tabs = listOf(
    "Home" to Icons.Rounded.Shield,
    "Locations" to Icons.Rounded.Public,
    "Statistics" to Icons.Rounded.BarChart,
    "Settings" to Icons.Rounded.Settings,
)

/** Phone + tablet experience. Tablets get wider, multi-column layouts (see `wide`). */
@Composable
fun MobileApp(vm: MainViewModel, snackbar: SnackbarHostState) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var sub by rememberSaveable { mutableStateOf(SubPage.NONE) }
    var importOpen by rememberSaveable { mutableStateOf(false) }
    val c = Abg.colors

    BackHandler(enabled = sub != SubPage.NONE || tab != 0) {
        if (sub != SubPage.NONE) sub = SubPage.NONE else tab = 0
    }

    BoxWithConstraints(Modifier.fillMaxSize().brandBackground(c.bg, c.bgAlt, c.isDark)) {
        val wide = maxWidth >= 600.dp
        Scaffold(
            containerColor = Color.Transparent,
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = { if (sub == SubPage.NONE) SlidingBottomBar(tab, { tab = it }, wide) },
        ) { pad ->
            Box(Modifier.fillMaxSize().padding(pad).statusBarsPadding()) {
                if (sub != SubPage.NONE) {
                    SubPageHost(sub, vm, wide) { sub = SubPage.NONE }
                } else {
                    Crossfade(targetState = tab, animationSpec = tween(220), label = "tab") { t ->
                        when (t) {
                            0 -> HomeScreen(vm, wide, onOpenLocations = { tab = 1 }, onAddServer = { importOpen = true })
                            1 -> LocationsScreen(vm, wide, onAdd = { importOpen = true }, onConnected = { tab = 0 })
                            2 -> ScrollPage("Statistics", wide) {
                                val v by vm.vpnState.collectAsStateWithLifecycle()
                                val u by vm.usage.collectAsStateWithLifecycle()
                                val s by vm.sessions.collectAsStateWithLifecycle()
                                StatsContent(v, u, s, onReset = { vm.resetStats() }, wide = wide)
                            }
                            else -> ScrollPage("Settings", wide) {
                                val s by vm.settings.collectAsStateWithLifecycle()
                                val user by vm.user.collectAsStateWithLifecycle()
                                SettingsContent(s, user, vm.authConfigured, vm) { sub = it }
                            }
                        }
                    }
                }
            }
        }
        if (importOpen) ImportServerDialog(vm) { importOpen = false }
    }
}

@Composable
fun ScrollPage(title: String, wide: Boolean, onBack: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = if (wide) 40.dp else 18.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) {
                IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBackIosNew, "Back", tint = Abg.colors.text) }
            }
            Text(title, style = MaterialTheme.typography.displaySmall, color = Abg.colors.text)
        }
        Spacer(Modifier.height(4.dp))
        Box(Modifier.fillMaxWidth().widthIn(max = 900.dp).align(Alignment.CenterHorizontally)) {
            Column(content = content)
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun SubPageHost(sub: SubPage, vm: MainViewModel, wide: Boolean, onBack: () -> Unit) {
    val s by vm.settings.collectAsStateWithLifecycle()
    when (sub) {
        SubPage.DNS -> ScrollPage("DNS", wide, onBack) { DnsContent(s, vm) }
        SubPage.UPDATES -> ScrollPage("Updates", wide, onBack) { UpdateContent(hiltViewModel()) }
        SubPage.KILLSWITCH -> ScrollPage("Kill Switch", wide, onBack) { KillSwitchContent(s.killSwitch) { vm.setKillSwitch(it) } }
        SubPage.PRIVACY -> ScrollPage("Privacy", wide, onBack) { InfoBlock("Privacy Policy", InfoText.privacy) }
        SubPage.DATA -> ScrollPage("Data", wide, onBack) { InfoBlock("Data Handling", InfoText.data) }
        SubPage.SECURITY -> ScrollPage("Security", wide, onBack) { InfoBlock("Security Information", InfoText.security) }
        SubPage.ABOUT -> ScrollPage("About", wide, onBack) { InfoBlock("AbgMieVPN", InfoText.about) }
        SubPage.NONE -> Unit
    }
}

/**
 * Premium bottom navigation: a blue rounded rectangle slides (spring) under the selected tab.
 * Width is measured from the actual bar, so it works on every phone size without layout jumps.
 */
@Composable
fun SlidingBottomBar(selected: Int, onSelect: (Int) -> Unit, wide: Boolean) {
    val c = Abg.colors
    Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp), contentAlignment = Alignment.Center) {
        BoxWithConstraints(
            Modifier.then(if (wide) Modifier.width(520.dp) else Modifier.fillMaxWidth())
                .height(66.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(c.card.copy(alpha = if (c.isDark) 0.94f else 0.97f))
                .border(1.dp, c.cardStroke, RoundedCornerShape(26.dp))
                .padding(6.dp)
        ) {
            val itemW = maxWidth / tabs.size
            val x by animateDpAsState(itemW * selected, spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow), label = "pill")
            Box(
                Modifier.offset(x = x).width(itemW).fillMaxHeight().clip(RoundedCornerShape(20.dp))
                    .background(Brush.linearGradient(listOf(Brand.ElectricBlue, Color(0xFF1F5FE0))))
            )
            Row(Modifier.fillMaxSize()) {
                tabs.forEachIndexed { i, (label, icon) ->
                    NavItem(label, icon, i == selected, Modifier.weight(1f)) { onSelect(i) }
                }
            }
        }
    }
}

@Composable
private fun NavItem(label: String, icon: ImageVector, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val dim = Abg.colors.textDim
    val iconColor by animateColorAsState(if (selected) Brand.CyanSoft else dim, tween(250), label = "ic")
    val textColor by animateColorAsState(if (selected) Color.White else dim, tween(250), label = "tc")
    Column(
        modifier.fillMaxHeight().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, label, tint = iconColor, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(2.dp))
        Text(label, color = textColor, fontSize = 11.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium, maxLines = 1)
    }
}
