package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val ValorantCrimsonScheme = darkColorScheme(
    primary = ValorantCrimson,
    onPrimary = Color(0xFF180406),
    primaryContainer = Color(0xFF3B1016),
    onPrimaryContainer = Color(0xFFFFB3B8),
    secondary = RadianiteCyan,
    onSecondary = Color(0xFF00221D),
    secondaryContainer = Color(0xFF093832),
    onSecondaryContainer = Color(0xFFA6FFF2),
    tertiary = KilljoyGold,
    onTertiary = Color(0xFF241900),
    background = ValorantObsidian,
    onBackground = TacticalWhite,
    surface = ValorantSlate,
    onSurface = TacticalWhite,
    surfaceVariant = ValorantCard,
    onSurfaceVariant = TextSecondary,
    outline = DividerSlate
)

private val JettCyanScheme = ValorantCrimsonScheme.copy(
    primary = RadianiteCyan,
    onPrimary = Color(0xFF00201C),
    primaryContainer = Color(0xFF083A33),
    onPrimaryContainer = Color(0xFFA8FFF3),
    secondary = ValorantCrimson
)

private val KilljoyGoldScheme = ValorantCrimsonScheme.copy(
    primary = KilljoyGold,
    onPrimary = Color(0xFF221700),
    primaryContainer = Color(0xFF3D2B05),
    onPrimaryContainer = Color(0xFFFFE08A),
    secondary = RadianiteCyan
)

private val OmenVioletScheme = ValorantCrimsonScheme.copy(
    primary = OmenViolet,
    onPrimary = Color(0xFF190D33),
    primaryContainer = Color(0xFF2D1B54),
    onPrimaryContainer = Color(0xFFDDD6FE),
    secondary = RadianiteCyan
)

private val NightRedDarkScheme = ValorantCrimsonScheme.copy(
    primary = NightRed,
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF330A0E),
    onPrimaryContainer = Color(0xFFFF8A93),
    secondary = Color(0xFFFF6B81),
    tertiary = Color(0xFFFF4757),
    onBackground = Color(0xFFFF6B81),
    onSurface = Color(0xFFFF6B81),
    onSurfaceVariant = Color(0xFFB33951)
)

@Composable
fun MyApplicationTheme(
    nightRedMode: Boolean = false,
    agentTheme: String = "VALORANT",
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        nightRedMode -> NightRedDarkScheme
        agentTheme == "JETT" -> JettCyanScheme
        agentTheme == "KILLJOY" -> KilljoyGoldScheme
        agentTheme == "OMEN" -> OmenVioletScheme
        else -> ValorantCrimsonScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
