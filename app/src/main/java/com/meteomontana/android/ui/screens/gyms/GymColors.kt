package com.meteomontana.android.ui.screens.gyms

import androidx.compose.ui.graphics.Color

/** Parses a `#RRGGBB` gym level colour; falls back to neutral grey when the server sends something unparsable. */
internal fun parseHex(hex: String): Color =
    runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrDefault(Color(0xFF9AA0A6))
