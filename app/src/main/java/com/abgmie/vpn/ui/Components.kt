package com.abgmie.vpn.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.abgmie.vpn.core.Abg
import com.abgmie.vpn.core.Brand
import com.abgmie.vpn.domain.VpnStatus

/** Background used on every screen: soft brand gradient over the theme background. */
fun Modifier.brandBackground(bg: Color, alt: Color, dark: Boolean) = this.background(
    Brush.verticalGradient(
        listOf(alt, bg, bg),
    )
).background(
    Brush.radialGradient(
        listOf(Brand.ElectricBlue.copy(alpha = if (dark) 0.18f else 0.10f), Color.Transparent),
        center = Offset(200f, 0f), radius = 1100f,
    )
)

/**
 * Rounded glass card. Clickable cards are D-pad focusable on TV:
 * focus = blue rounded outline + slight spring scale.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    radius: Dp = 24.dp,
    padding: PaddingValues = PaddingValues(18.dp),
    highlight: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = Abg.colors
    val src = remember { MutableInteractionSource() }
    val focused by src.collectIsFocusedAsState()
    val scale by animateFloatAsState(if (focused) 1.04f else 1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow), label = "fs")
    val border by animateColorAsState(
        when { focused -> Brand.Cyan; highlight -> Brand.ElectricBlue; else -> c.cardStroke }, tween(220), label = "fb"
    )
    val shape = RoundedCornerShape(radius)
    Column(
        modifier
            .scale(scale)
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(c.card.copy(alpha = if (c.isDark) 0.92f else 0.95f), c.card.copy(alpha = if (c.isDark) 0.70f else 0.85f))
                )
            )
            .border(BorderStroke(if (focused) 2.5.dp else 1.dp, border), shape)
            .then(if (onClick != null) Modifier.clickable(interactionSource = src, indication = null, onClick = onClick) else Modifier)
            .padding(padding),
        content = content,
    )
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(), modifier = modifier.padding(start = 6.dp, top = 18.dp, bottom = 8.dp),
        color = Abg.colors.textDim, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.4.sp,
    )
}

@Composable
fun statusColor(s: VpnStatus): Color = when (s) {
    VpnStatus.CONNECTED -> Brand.Success
    VpnStatus.CONNECTING, VpnStatus.RECONNECTING, VpnStatus.DISCONNECTING -> Brand.Warning
    VpnStatus.FAILED -> Brand.Danger
    VpnStatus.DISCONNECTED -> Abg.colors.textDim
}

@Composable
fun StatusPill(status: VpnStatus, large: Boolean = false) {
    val col = statusColor(status)
    Row(
        Modifier.clip(CircleShape).background(col.copy(alpha = 0.14f)).padding(horizontal = if (large) 18.dp else 12.dp, vertical = if (large) 8.dp else 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val pulse = rememberInfiniteTransition(label = "p").animateFloat(0.35f, 1f, infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "pa")
        Box(Modifier.size(if (large) 10.dp else 8.dp).clip(CircleShape).background(col.copy(alpha = if (status.isBusy) pulse.value else 1f)))
        Spacer(Modifier.width(8.dp))
        Text(status.label, color = col, fontWeight = FontWeight.SemiBold, fontSize = if (large) 18.sp else 13.sp)
    }
}

/**
 * Large animated power button. Spinning arc while connecting, glowing blue halo when connected.
 */
@Composable
fun ConnectOrb(
    status: VpnStatus,
    size: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    interaction: MutableInteractionSource = remember { MutableInteractionSource() },
    focusRequester: FocusRequester? = null,
) {
    val connected = status == VpnStatus.CONNECTED
    val busy = status.isBusy
    val focused by interaction.collectIsFocusedAsState()
    val inf = rememberInfiniteTransition(label = "orb")
    val spin by inf.animateFloat(0f, 360f, infiniteRepeatable(tween(1400, easing = LinearEasing)), label = "spin")
    val breathe by inf.animateFloat(0.85f, 1.08f, infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "br")
    val press by animateFloatAsState(if (focused) 1.06f else 1f, spring(Spring.DampingRatioMediumBouncy), label = "pr")
    val core by animateColorAsState(
        when {
            connected -> Brand.ElectricBlue
            status == VpnStatus.FAILED -> Brand.Danger.copy(alpha = 0.85f)
            else -> Abg.colors.card
        }, tween(500), label = "core"
    )
    val iconTint by animateColorAsState(if (connected || status == VpnStatus.FAILED) Color.White else Brand.ElectricBlue, tween(400), label = "it")
    val dim = Abg.colors.cardStroke

    Box(modifier.size(size).scale(press), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val r = this.size.minDimension / 2
            if (connected) {
                drawCircle(Brush.radialGradient(listOf(Brand.ElectricBlue.copy(alpha = 0.45f), Brand.Cyan.copy(alpha = 0.10f), Color.Transparent), radius = r * breathe), radius = r * breathe)
            }
            drawCircle(color = if (focused) Brand.Cyan else dim.copy(alpha = 0.7f), radius = r * 0.80f, style = Stroke(width = if (focused) 6f else 3f))
            if (busy) {
                rotate(spin) {
                    drawArc(
                        brush = Brush.sweepGradient(listOf(Color.Transparent, Brand.Cyan, Brand.ElectricBlue)),
                        startAngle = 0f, sweepAngle = 260f, useCenter = false,
                        topLeft = Offset(r - r * 0.80f, r - r * 0.80f),
                        size = androidx.compose.ui.geometry.Size(r * 1.6f, r * 1.6f),
                        style = Stroke(width = 10f, cap = StrokeCap.Round),
                    )
                }
            }
        }
        Box(
            Modifier.size(size * 0.62f).clip(CircleShape)
                .background(if (connected) Brush.linearGradient(listOf(Brand.ElectricBlue, Brand.Cyan)) else Brush.linearGradient(listOf(core, core)))
                .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                .clickable(interactionSource = interaction, indication = null, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.PowerSettingsNew, contentDescription = if (status.isActive) "Disconnect" else "Connect", tint = iconTint, modifier = Modifier.size(size * 0.24f))
        }
    }
}

@Composable
fun MetricTile(label: String, value: String, icon: ImageVector, modifier: Modifier = Modifier, accent: Color = Brand.ElectricBlue) {
    GlassCard(modifier, padding = PaddingValues(14.dp), radius = 20.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(30.dp).clip(RoundedCornerShape(10.dp)).background(accent.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = accent, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(10.dp))
            Text(label, color = Abg.colors.textDim, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.height(10.dp))
        Text(value, color = Abg.colors.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun SettingRow(
    title: String,
    subtitle: String? = null,
    icon: ImageVector? = null,
    checked: Boolean? = null,
    onClick: () -> Unit,
    trailing: @Composable (RowScope.() -> Unit)? = null,
) {
    GlassCard(Modifier.fillMaxWidth().padding(vertical = 4.dp), onClick = onClick, radius = 18.dp, padding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Box(Modifier.size(34.dp).clip(RoundedCornerShape(11.dp)).background(Brand.ElectricBlue.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                    Icon(icon, null, tint = Brand.ElectricBlue, modifier = Modifier.size(19.dp))
                }
                Spacer(Modifier.width(14.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(title, color = Abg.colors.text, fontWeight = FontWeight.Medium, fontSize = 15.sp)
                if (subtitle != null) Text(subtitle, color = Abg.colors.textDim, fontSize = 12.5.sp)
            }
            if (checked != null) {
                Switch(
                    checked = checked, onCheckedChange = null, // row handles the click: single D-pad focus stop on TV
                    colors = SwitchDefaults.colors(checkedTrackColor = Brand.ElectricBlue, checkedThumbColor = Color.White),
                )
            }
            trailing?.invoke(this)
        }
    }
}

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, icon: ImageVector? = null) {
    val src = remember { MutableInteractionSource() }
    val focused by src.collectIsFocusedAsState()
    val scale by animateFloatAsState(if (focused) 1.05f else 1f, spring(Spring.DampingRatioMediumBouncy), label = "b")
    Row(
        modifier.scale(scale).clip(RoundedCornerShape(16.dp))
            .background(if (enabled) Brush.horizontalGradient(listOf(Brand.ElectricBlue, Brand.Cyan)) else Brush.horizontalGradient(listOf(Color.Gray, Color.Gray)))
            .border(if (focused) 2.5.dp else 0.dp, if (focused) Color.White else Color.Transparent, RoundedCornerShape(16.dp))
            .clickable(enabled = enabled, interactionSource = src, indication = null, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) { Icon(icon, null, tint = Color.White, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)) }
        Text(text, color = Color.White, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    GlassCard(modifier, onClick = onClick, radius = 16.dp, padding = PaddingValues(horizontal = 18.dp, vertical = 13.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
            if (icon != null) { Icon(icon, null, tint = Brand.ElectricBlue, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)) }
            Text(text, color = Abg.colors.text, fontWeight = FontWeight.Medium)
        }
    }
}

/** Simple, honest bar chart of measured bytes per day. */
@Composable
fun UsageBars(values: List<Pair<String, Long>>, modifier: Modifier = Modifier) {
    val max = (values.maxOfOrNull { it.second } ?: 0L).coerceAtLeast(1L)
    Row(modifier.fillMaxWidth().height(150.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
        values.forEach { (label, v) ->
            val target = v.toFloat() / max
            val h by animateFloatAsState(target, tween(700), label = "bar")
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                Box(Modifier.width(18.dp).height(110.dp), contentAlignment = Alignment.BottomCenter) {
                    Box(
                        Modifier.fillMaxWidth().height((110 * h).dp.coerceAtLeast(3.dp)).clip(RoundedCornerShape(8.dp))
                            .background(Brush.verticalGradient(listOf(Brand.Cyan, Brand.ElectricBlue)))
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(label, color = Abg.colors.textDim, fontSize = 11.sp)
            }
        }
    }
}
