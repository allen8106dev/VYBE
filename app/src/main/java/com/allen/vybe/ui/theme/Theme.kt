package com.allen.vybe.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val MuseGreen = Color(0xFF88EDB0)
private val MuseColors = darkColorScheme(
    primary = MuseGreen, onPrimary = Color(0xFF062E19),
    primaryContainer = Color(0xFF244936), onPrimaryContainer = Color(0xFFB8F7D2),
    secondary = Color(0xFFB9D8C6), background = Color(0xFF090D0B),
    secondaryContainer = Color(0xFF244936), onSecondaryContainer = MuseGreen,
    surface = Color(0xFF111713), surfaceVariant = Color(0xFF202923),
    onBackground = Color(0xFFF2F6F0), onSurface = Color(0xFFF2F6F0),
    onSurfaceVariant = Color(0xFFA3B0A7), outline = Color(0xFF3A483E)
    ,outlineVariant = Color(0xFF303D34), inverseSurface = Color(0xFF293C31), inverseOnSurface = Color(0xFFF2F6F0), inversePrimary = MuseGreen
)
@Composable
fun VYBETheme(content: @Composable () -> Unit) { MaterialTheme(colorScheme = MuseColors, typography = Typography, content = content) }
