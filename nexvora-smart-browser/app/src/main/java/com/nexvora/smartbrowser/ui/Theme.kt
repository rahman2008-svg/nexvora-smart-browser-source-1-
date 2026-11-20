package com.nexvora.smartbrowser.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/** Central palette. Nothing else in the app hardcodes colours. */
object Nx {
    val Bg = Color(0xFF070812); val Bg2 = Color(0xFF0B1020); val Surface = Color(0xFF101528); val Card = Color(0xFF151B30)
    val Purple = Color(0xFF852FFF); val Purple2 = Color(0xFFB45AFF); val Cyan = Color(0xFF00D5FF); val Blue = Color(0xFF376CFF)
    val Text = Color(0xFFF5F6FF); val Text2 = Color(0xFFA1A8C2); val Muted = Color(0xFF737C98)
    val Success = Color(0xFF3DDC84); val Warning = Color(0xFFFFB020); val Error = Color(0xFFFF5A67)
    val Border = Color(0x33806BFF)
    val brand = Brush.linearGradient(listOf(Purple, Cyan))
}

val AccentChoices = listOf(0xFF852FFF, 0xFF00D5FF, 0xFF376CFF, 0xFFB45AFF, 0xFF3DDC84, 0xFFFFB020).map { it.toInt() }

@Composable
fun NexVoraTheme(mode: String, accent: Int, fontScale: Float, content: @Composable () -> Unit) {
    val dark = when (mode) { "light" -> false; "system" -> isSystemInDarkTheme(); else -> true }
    val amoled = mode == "amoled"
    val primary = Color(accent)
    val scheme = if (dark) darkColorScheme(
        primary = primary, secondary = Nx.Cyan, tertiary = Nx.Purple2,
        background = if (amoled) Color.Black else Nx.Bg, onBackground = Nx.Text,
        surface = if (amoled) Color(0xFF05060A) else Nx.Surface, onSurface = Nx.Text,
        surfaceVariant = if (amoled) Color(0xFF0B0D16) else Nx.Card, onSurfaceVariant = Nx.Text2,
        outline = Nx.Muted, outlineVariant = Nx.Border, error = Nx.Error, onPrimary = Color.White,
    ) else lightColorScheme(
        primary = primary, secondary = Color(0xFF0088A8), tertiary = Color(0xFF6A1FCC),
        background = Color(0xFFF6F7FF), onBackground = Color(0xFF14172B),
        surface = Color.White, onSurface = Color(0xFF14172B),
        surfaceVariant = Color(0xFFEAECFA), onSurfaceVariant = Color(0xFF4A5070),
        outline = Color(0xFF7A819F), outlineVariant = Color(0x33806BFF), error = Color(0xFFC62828), onPrimary = Color.White,
    )
    val d = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(d.density, d.fontScale * fontScale)) {
        MaterialTheme(colorScheme = scheme, typography = Typography().let {
            it.copy(
                titleLarge = it.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                titleMedium = it.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                labelSmall = it.labelSmall.copy(fontSize = 11.sp),
            )
        }, shapes = Shapes(small = RoundedCornerShape(10.dp), medium = RoundedCornerShape(16.dp), large = RoundedCornerShape(22.dp)), content = content)
    }
}
