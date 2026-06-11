package com.mert.dbrstudios.worldcup.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val WorldCupColorScheme = darkColorScheme(
    primary          = Gold,
    onPrimary        = BgDeep,
    primaryContainer = GoldGlow,
    secondary        = BlueAccent,
    background       = BgDeep,
    surface          = BgCard,
    onBackground     = TextPrimary,
    onSurface        = TextPrimary,
    outline          = BorderColor,
    error            = RedError
)

@Composable
fun WorldCupTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = WorldCupColorScheme,
        typography  = Typography,
        content     = content
    )
}