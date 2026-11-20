package com.meteomontana.android.ui.screens.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meteomontana.android.R
import com.meteomontana.android.ui.theme.Serif
import com.meteomontana.android.ui.theme.Spacing

/**
 * Cabecera UNICA de todos los chats (quedada, privados, escuelas y rocodromos; maqueta 7A de la ronda 5): volver,
 * avatar, titulo + subtitulo (opcional, se toca con [onTitleClick]) y acciones a la derecha. Cada chat solo cambia el
 * avatar, los textos y las acciones.
 */
@Composable
fun ChatTopBar(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: (@Composable () -> Unit)? = null,
    onTitleClick: (() -> Unit)? = null,
    avatar: @Composable () -> Unit,
    actions: @Composable RowScope.() -> Unit = {}
) {
    Row(
        modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).padding(horizontal = Spacing.xs, vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.common_back), tint = MaterialTheme.colorScheme.onBackground)
        }
        Row(
            Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).let { m -> if (onTitleClick != null) m.clickable(onClick = onTitleClick) else m },
            verticalAlignment = Alignment.CenterVertically
        ) {
            avatar()
            Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                Text(title, fontFamily = Serif, fontWeight = FontWeight.Bold, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                subtitle?.invoke()
            }
        }
        actions()
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
}

/** Subtitulo de una linea de [ChatTopBar]. */
@Composable
fun ChatSubtitle(text: String) {
    Text(text, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

/** Avatar de letra (rocodromo, escuela): circulo de color con la inicial. */
@Composable
fun ChatInitialAvatar(name: String, color: Color) {
    Box(Modifier.size(42.dp).clip(CircleShape).background(color), contentAlignment = Alignment.Center) {
        Text(name.take(1).uppercase(), color = Color.White, fontFamily = Serif, fontWeight = FontWeight.Bold, fontSize = 18.sp)
    }
}

/** Burbujas con cola: las ajenas la llevan abajo a la izquierda, las mias abajo a la derecha. */
fun chatBubbleShape(isMine: Boolean) =
    if (isMine) RoundedCornerShape(18.dp, 18.dp, 6.dp, 18.dp) else RoundedCornerShape(18.dp, 18.dp, 18.dp, 6.dp)
