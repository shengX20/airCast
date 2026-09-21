package com.airplay.cast.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Design Tokens for AirCast (Android)
 * Defined per design.md and index.html specifications.
 */
object AirCastTheme {
    // 4.1 Color Tokens
    val BgBase = Color(0xFF0B0C0F)
    val BgSurface = Color(0xFF15171C)
    val BgElevated = Color(0xFF1D2027)
    val BgDialCenter = Color(0xFF242830)
    val BgDialEdge = Color(0xFF191C22)
    val BgHubCenter = Color(0xFF181B21)
    val BgHubEdge = Color(0xFF131519)

    val TextPrimary = Color(0xFFF5F5F7)
    val TextSecondary = Color(0xFF98989F)
    val TextTertiary = Color(0xFF6C6C72)

    val Accent = Color(0xFFE0A458)
    val AccentPressed = Color(0xFFC98A3D)
    val AccentGlow = Color(0x40E0A458) // rgba(224,164,88, 0.25)
    val AccentSubtle = Color(0x10E0A458) // rgba(224,164,88, 0.06)

    val DeviceIconGray = Color(0xFF8E8E93)
    val Border = Color(0x14FFFFFF) // rgba(255,255,255, 0.08)
    val BorderSubtle = Color(0x0DFFFFFF) // rgba(255,255,255, 0.05)

    val Success = Color(0xFF30D158)
    val Danger = Color(0xFFFF453A)
    val DangerSubtle = Color(0x26FF453A) // rgba(255,69,58, 0.15)
    val DangerBorder = Color(0x4DFF453A) // rgba(255,69,58, 0.3)

    val EmbossDark = Color(0x80000000) // rgba(0,0,0, 0.5)
    val EmbossLight = Color(0x0DFFFFFF) // rgba(255,255,255, 0.05)

    // Corner Radii
    val RadiusCard = 16.dp
    val RadiusSheet = 24.dp
    val RadiusButton = 12.dp
    val RadiusIconTile = 12.dp
}
