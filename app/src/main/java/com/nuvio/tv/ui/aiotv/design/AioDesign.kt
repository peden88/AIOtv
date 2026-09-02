package com.nuvio.tv.ui.aiotv.design

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * AIOtv-owned visual language. Keep product styling here rather than leaking it
 * into upstream Nuvio theme tokens so upstream merges can stay mostly logic-only.
 */
object AioColors {
    val Canvas = Color(0xFF090B0E)
    val Surface = Color(0xFF101318)
    val SurfaceRaised = Color(0xFF171B21)
    val SurfaceFocused = Color(0xFF20252D)
    val SurfacePressed = Color(0xFF272D36)

    val TextPrimary = Color(0xFFF4F6F8)
    val TextSecondary = Color(0xFFA6ADB7)
    val TextMuted = Color(0xFF69717D)

    val Accent = Color(0xFF7467E8)
    val AccentSoft = Color(0x337467E8)
    val FocusBorder = Color(0xFF9B92F4)
    val Divider = Color(0x1AFFFFFF)
    val Success = Color(0xFF59C796)
    val Warning = Color(0xFFF2C66D)
    val Error = Color(0xFFE67575)
}

object AioSpacing {
    val ScreenHorizontal = 56.dp
    val ScreenVertical = 36.dp
    val Section = 28.dp
    val Row = 12.dp
    val Card = 12.dp
}

object AioRadii {
    val Small = 8.dp
    val Card = 12.dp
    val Large = 18.dp
}

object AioMotion {
    const val FocusMs = 140
    const val NavigationMs = 200
    const val BackdropMs = 360
    const val OverlayMs = 220

    const val FocusScale = 1.035f
}
