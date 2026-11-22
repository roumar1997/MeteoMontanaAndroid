package com.meteomontana.android.ui.screens.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meteomontana.android.R
import com.meteomontana.android.ui.theme.Serif
import com.meteomontana.android.ui.theme.terraFillColor

// Estilo comun de TODOS los chats (quedada, privado, escuela, rocodromo; maqueta 7A): papel blanco para cabecera, campo y
// burbujas ajenas sobre el fondo hueso, para que se distingan del resto de la app.

/** Blanco de las piezas del chat (en oscuro, el papel oscuro de la app). */
@Composable
fun chatCardColor(): Color =
    if (MaterialTheme.colorScheme.background.luminance() < 0.5f) MaterialTheme.colorScheme.surfaceVariant else Color.White

/** Burbuja con cola: las mias terracota; las ajenas blancas con borde fino y una sombra suave. */
@Composable
fun Modifier.chatBubbleSurface(isMine: Boolean): Modifier {
    val shape = chatBubbleShape(isMine)
    return if (isMine) shadow(2.dp, shape, clip = false).clip(shape).background(terraFillColor())
    else shadow(1.dp, shape, clip = false).clip(shape).background(chatCardColor())
        .border(1.dp, MaterialTheme.colorScheme.outline, shape)
}

/** Chat vacio: icono en circulo, titulo y una frase para animar a escribir el primero. */
@Composable
fun ChatEmptyState(
    modifier: Modifier = Modifier,
    title: String = stringResource(R.string.chat_empty_title),
    subtitle: String = stringResource(R.string.chat_empty_sub)
) {
    Column(modifier.fillMaxSize().padding(horizontal = 32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(84.dp).clip(CircleShape).background(terraFillColor().copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.ChatBubbleOutline, null, tint = terraFillColor(), modifier = Modifier.size(38.dp))
        }
        Text(
            title, fontFamily = Serif, fontWeight = FontWeight.Bold, fontSize = 20.sp,
            textAlign = TextAlign.Center, modifier = Modifier.padding(top = 16.dp), color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            subtitle, fontSize = 14.sp, textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp)
        )
    }
}
