package com.meteomontana.android.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

// Cumbre apenas usa radius: 0/2/4 px.
val CumbreShapes = Shapes(
    extraSmall = RoundedCornerShape(0.dp),
    small      = RoundedCornerShape(2.dp),
    medium     = RoundedCornerShape(2.dp),
    large      = RoundedCornerShape(4.dp),
    extraLarge = RoundedCornerShape(4.dp),
)
