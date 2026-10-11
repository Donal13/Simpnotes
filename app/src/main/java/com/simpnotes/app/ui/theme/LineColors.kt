package com.simpnotes.app.ui.theme

import androidx.compose.ui.graphics.Color
import com.simpnotes.app.data.LineColor

/**
 * Shades of each line colour for light and dark backgrounds, chosen to stay readable on both.
 * Shared by the app and the home screen widget so a line looks the same in each.
 */
data class LineColorShades(val light: Color, val dark: Color)

val LineColor.shades: LineColorShades?
    get() = when (this) {
        LineColor.DEFAULT -> null
        LineColor.RED -> LineColorShades(light = Color(0xFFC62828), dark = Color(0xFFFF8A80))
        LineColor.ORANGE -> LineColorShades(light = Color(0xFFB45309), dark = Color(0xFFFFB74D))
        LineColor.GREEN -> LineColorShades(light = Color(0xFF2E7D32), dark = Color(0xFF81C784))
        LineColor.BLUE -> LineColorShades(light = Color(0xFF1565C0), dark = Color(0xFF64B5F6))
        LineColor.PURPLE -> LineColorShades(light = Color(0xFF7B1FA2), dark = Color(0xFFCE93D8))
    }

/** The colour to draw with, or [Color.Unspecified] for [LineColor.DEFAULT] (use the normal text colour). */
fun LineColor.colorFor(darkTheme: Boolean): Color =
    shades?.let { if (darkTheme) it.dark else it.light } ?: Color.Unspecified

val LineColor.label: String
    get() = when (this) {
        LineColor.DEFAULT -> "Default"
        LineColor.RED -> "Red"
        LineColor.ORANGE -> "Orange"
        LineColor.GREEN -> "Green"
        LineColor.BLUE -> "Blue"
        LineColor.PURPLE -> "Purple"
    }
