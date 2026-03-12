package com.asksin.analyzer.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// ── Palette ─────────────────────────────────────────────────────────────────
val Background     = Color(0xFF0D0F14)
val Surface        = Color(0xFF161A22)
val SurfaceVariant = Color(0xFF1E2430)
val Border         = Color(0xFF2A3040)

val Accent       = Color(0xFF00E5B4)   // teal-green — signal OK
val AccentDim    = Color(0xFF00A884)
val Warning      = Color(0xFFFFB830)   // amber — duty cycle warning
val Danger       = Color(0xFFFF4757)   // red — RSSI bad / error
val TextPrimary  = Color(0xFFE8ECF4)
val TextSecondary= Color(0xFF7A8399)
val TextMuted    = Color(0xFF3D4557)

val RssiGood     = Color(0xFF00E5B4)
val RssiMed      = Color(0xFFFFB830)
val RssiBad      = Color(0xFFFF4757)

private val DarkColors = darkColorScheme(
    primary          = Accent,
    onPrimary        = Background,
    secondary        = AccentDim,
    background       = Background,
    surface          = Surface,
    surfaceVariant   = SurfaceVariant,
    onBackground     = TextPrimary,
    onSurface        = TextPrimary,
    outline          = Border,
    error            = Danger
)

val MonoFont = FontFamily.Monospace   // system mono for hex data
val DefaultFont = FontFamily.Default

@Composable
fun AskSinTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColors,
        content = content
    )
}
