package com.meteomontana.android.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.BackHand
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Hiking
import androidx.compose.material.icons.filled.Landscape
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

/** Cada título tiene su color y su icono (Álvaro, 2026-10-18: «que cada título tenga su propio estilo»). */
/** Color del título de un nivel (para la aureola de las fotos). */
fun titleColor(level: Int): Color = rewardStyle(level).first

fun rewardStyle(level: Int): Pair<Color, ImageVector> = when (level) {
    0 -> Color(0xFF73787F) to Icons.Filled.Eco
    1 -> Color(0xFFCC5C33) to Icons.Filled.BackHand
    5 -> Color(0xFF338C4D) to Icons.Filled.Hiking
    10 -> Color(0xFF2973BF) to Icons.Filled.Map
    15 -> Color(0xFF0D8C8C) to Icons.Filled.GpsFixed
    20 -> Color(0xFF5C599E) to Icons.Filled.Shield
    25 -> Color(0xFFD97A1A) to Icons.Filled.Gesture
    30 -> Color(0xFFB82E38) to Icons.Filled.Landscape
    35 -> Color(0xFF454FB3) to Icons.Filled.Timeline
    40 -> Color(0xFF8C409E) to Icons.Filled.MenuBook
    45 -> Color(0xFF4D738C) to Icons.Filled.Air
    else -> Color(0xFFB8860B) to Icons.Filled.WorkspacePremium
}

/** «#RRGGBB» → Color (null si no es válido). Lo usa también la aureola de las fotos. */
fun colorFromHexOrNull(hex: String?): Color? = runCatching {
    val h = hex?.trim()?.removePrefix("#") ?: return null
    if (h.length != 6) return null
    Color(0xFF000000 or h.toLong(16))
}.getOrNull()
