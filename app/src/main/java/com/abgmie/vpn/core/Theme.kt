package com.abgmie.vpn.core

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object Brand {
    val ElectricBlue = Color(0xFF2F7BFF)
    val DeepNavy = Color(0xFF0B1D4A)
    val Cyan = Color(0xFF00D4FF)
    val CyanSoft = Color(0xFF7DEBFF)
    val Success = Color(0xFF2EE6A6)
    val Warning = Color(0xFFFFB547)
    val Danger = Color(0xFFFF5C7A)
}

@Immutable
data class AbgColors(
    val bg: Color,
    val bgAlt: Color,
    val card: Color,
    val cardStroke: Color,
    val text: Color,
    val textDim: Color,
    val isDark: Boolean,
)

val LocalAbg = staticCompositionLocalOf {
    AbgColors(Color.Black, Color.Black, Color.DarkGray, Color.Gray, Color.White, Color.LightGray, true)
}

private val LightAbg = AbgColors(
    bg = Color(0xFFFFFFFF), bgAlt = Color(0xFFE9EEF6), card = Color(0xFFF1F4F9),
    cardStroke = Color(0xFFD7DEEA), text = Color(0xFF0A1330), textDim = Color(0xFF5B6680), isDark = false,
)
private val DarkAbg = AbgColors(
    bg = Color(0xFF030408), bgAlt = Color(0xFF0A1024), card = Color(0xFF14171F),
    cardStroke = Color(0xFF232836), text = Color(0xFFFFFFFF), textDim = Color(0xFF8C95AB), isDark = true,
)

private val Type = Typography(
    displaySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 34.sp, letterSpacing = (-0.5).sp),
    headlineSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 24.sp),
    titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 20.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 16.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 14.sp),
    labelMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 12.sp, letterSpacing = 0.4.sp),
)

@Composable
private fun anim(c: Color) = animateColorAsState(c, tween(450), label = "theme").value

/** Light / Dark theme with smooth animated transitions between them. */
@Composable
fun AbgMieTheme(dark: Boolean, content: @Composable () -> Unit) {
    val t = if (dark) DarkAbg else LightAbg
    val colors = AbgColors(
        bg = anim(t.bg), bgAlt = anim(t.bgAlt), card = anim(t.card), cardStroke = anim(t.cardStroke),
        text = anim(t.text), textDim = anim(t.textDim), isDark = dark,
    )
    val scheme = (if (dark) darkColorScheme() else lightColorScheme()).copy(
        primary = Brand.ElectricBlue, onPrimary = Color.White, secondary = Brand.DeepNavy, tertiary = Brand.Cyan,
        background = colors.bg, surface = colors.card, onBackground = colors.text, onSurface = colors.text,
        surfaceVariant = colors.bgAlt, onSurfaceVariant = colors.textDim, outline = colors.cardStroke,
        error = Brand.Danger,
    )
    CompositionLocalProvider(LocalAbg provides colors) {
        MaterialTheme(colorScheme = scheme, typography = Type, content = content)
    }
}

object Abg {
    val colors: AbgColors @Composable get() = LocalAbg.current
}
