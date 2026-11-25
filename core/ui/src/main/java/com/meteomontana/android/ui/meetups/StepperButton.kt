package com.meteomontana.android.ui.meetups

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meteomontana.android.ui.theme.EyebrowTextStyle
import com.meteomontana.android.ui.theme.Serif
import com.meteomontana.android.ui.theme.terraFillColor

@Composable
fun StepperButton(symbol: String, onClick: (() -> Unit)?, primary: Boolean, description: String, size: Int = 42) {
    val enabled = onClick != null
    val bg = if (primary) terraFillColor() else MaterialTheme.colorScheme.background
    Box(
        Modifier.size(size.dp).clip(CircleShape)
            .background(bg.copy(alpha = if (enabled) 1f else 0.4f), CircleShape)
            .then(if (primary) Modifier else Modifier.border(1.dp, MaterialTheme.colorScheme.outline, CircleShape))
            .clickable(enabled = enabled, role = Role.Button, onClick = { onClick?.invoke() }),
        contentAlignment = Alignment.Center
    ) {
        Text(
            symbol, fontWeight = FontWeight.Bold, fontSize = 22.sp, textAlign = TextAlign.Center,
            color = if (primary) Color.White else MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.4f)
        )
    }
}
