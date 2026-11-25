package com.meteomontana.android.ui.screens.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.ui.draw.shadow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meteomontana.android.feature.chat.R
import com.meteomontana.android.ui.theme.terraFillColor

/**
 * Campo de escribir de TODOS los chats (quedada, privados, escuelas y rocódromos): píldora blanca con borde y botón de
 * enviar redondo terracota. No hay cámara porque ningún chat sabe enviar fotos todavía.
 */
@Composable
fun ChatInputPill(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    placeholder: String = stringResource(R.string.chat_write)
) {
    val canSend = enabled && text.isNotBlank()
    Row(
        modifier.fillMaxWidth().background(chatCardColor()).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        TextField(
            value = text, onValueChange = onTextChange,
            modifier = Modifier.weight(1f).clip(RoundedCornerShape(26.dp)).border(1.5.dp, if (text.isBlank()) MaterialTheme.colorScheme.outline else terraFillColor(), RoundedCornerShape(26.dp)),
            leadingIcon = { Icon(Icons.Outlined.Edit, null, tint = terraFillColor(), modifier = Modifier.size(18.dp)) },
            placeholder = { Text(placeholder, style = MaterialTheme.typography.bodyMedium) },
            maxLines = 4, shape = RoundedCornerShape(26.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.background, unfocusedContainerColor = MaterialTheme.colorScheme.background,
                disabledContainerColor = MaterialTheme.colorScheme.background,
                focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent, disabledIndicatorColor = Color.Transparent
            )
        )
        Box(
            Modifier.size(50.dp).shadow(if (canSend) 4.dp else 0.dp, CircleShape).clip(CircleShape)
                .background(terraFillColor().copy(alpha = if (canSend) 1f else 0.4f))
                .clickable(enabled = canSend, onClick = onSend),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.AutoMirrored.Outlined.Send, stringResource(R.string.common_send),
                tint = Color.White, modifier = Modifier.size(22.dp)
            )
        }
    }
}

/** Sustituye al campo cuando no estás «Aquí»: candado, «Solo escriben los que están aquí» y el botón para marcarlo. */
@Composable
fun ChatLockedBar(onHere: @Composable () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp).clip(shape).background(chatCardColor())
            .border(1.dp, MaterialTheme.colorScheme.outline, shape).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(Icons.Outlined.Lock, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.chat_locked_title), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onBackground)
            Text(stringResource(R.string.chat_locked_sub), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        onHere()
    }
}
