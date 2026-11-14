package com.haziz.lamha

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val Light = lightColorScheme(
    primary = Color(0xFF0F766E), onPrimary = Color.White, secondary = Color(0xFFD9962B),
    primaryContainer = Color(0xFFCDEBE7), background = Color(0xFFF7F5F0), surface = Color.White,
    onBackground = Color(0xFF16201F), onSurface = Color(0xFF16201F),
    surfaceVariant = Color(0xFFE9E6DF), onSurfaceVariant = Color(0xFF5B6664)
)
private val Dark = darkColorScheme(
    primary = Color(0xFF5EC8BE), onPrimary = Color(0xFF00201D), secondary = Color(0xFFF0B85A),
    primaryContainer = Color(0xFF144A45), background = Color(0xFF0D1113), surface = Color(0xFF171D20),
    onBackground = Color(0xFFECF1F0), onSurface = Color(0xFFECF1F0),
    surfaceVariant = Color(0xFF232B2E), onSurfaceVariant = Color(0xFF9DAAA8)
)

@Composable
fun LamhaTheme(dark: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (dark) Dark else Light,
        shapes = Shapes(small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(18.dp), large = RoundedCornerShape(26.dp)),
        content = content
    )
}
