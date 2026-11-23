package com.oxford5000.learn.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Light = lightColorScheme(
    primary = Color(0xFF1E3A8A),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE4FF),
    onPrimaryContainer = Color(0xFF0B1B4D),
    secondary = Color(0xFFB45309),
    secondaryContainer = Color(0xFFFFE8C7),
    background = Color(0xFFF7F8FC),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFE9ECF5),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF9DB4FF),
    onPrimary = Color(0xFF0B1B4D),
    primaryContainer = Color(0xFF263A7A),
    onPrimaryContainer = Color(0xFFDCE4FF),
    secondary = Color(0xFFFBBF24),
    secondaryContainer = Color(0xFF4A3510),
    background = Color(0xFF0F1220),
    surface = Color(0xFF171B2E),
    surfaceVariant = Color(0xFF242A42),
)

/** Цвета для «пройдено / правильно / неправильно». */
object Marks {
    fun passed(dark: Boolean) = if (dark) Color(0xFF2E7D4F) else Color(0xFF43A047)
    fun notPassed(dark: Boolean) = if (dark) Color(0xFF3A3F55) else Color(0xFFD5D8E0)
    fun correctBg(dark: Boolean) = if (dark) Color(0xFF1F5E3A) else Color(0xFFC8F2D4)
    fun correctBorder(dark: Boolean) = if (dark) Color(0xFF4ADE80) else Color(0xFF2E7D32)
    fun wrongBg(dark: Boolean) = if (dark) Color(0xFF6B2323) else Color(0xFFFFD4D4)
    fun wrongBorder(dark: Boolean) = if (dark) Color(0xFFF87171) else Color(0xFFC62828)
}

@Composable
fun Oxford5000Theme(dark: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) Dark else Light, content = content)
}
