package com.meteomontana.android.ui.screens.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.meteomontana.android.ui.components.PersonAvatar

// Avatar pequeño junto a cada mensaje en los chats de GRUPO (quedada, escuela, rocódromo); en el 1 a 1 no se usa.

/** Lado del avatar; el hueco que se reserva a la izquierda en el resto de mensajes de la racha es el mismo. */
val ChatAvatarSize: Dp = 28.dp

/** Posición de un mensaje dentro de su racha (mensajes seguidos del mismo emisor, sin cruzar de día). */
data class ChatRunPos(val first: Boolean, val last: Boolean)

/**
 * [uids] va en orden cronológico (antiguo a nuevo). El nombre se enseña en el primero de la racha y el avatar
 * en el último. [startsDay] indica si el mensaje i abre un día nuevo (el separador corta la racha).
 */
fun chatRunPositions(uids: List<String>, startsDay: (Int) -> Boolean = { false }): List<ChatRunPos> =
    uids.indices.map { i ->
        ChatRunPos(
            first = i == 0 || uids[i - 1] != uids[i] || startsDay(i),
            last = i == uids.lastIndex || uids[i + 1] != uids[i] || startsDay(i + 1)
        )
    }

/** Hueco de [ChatAvatarSize]: con [show] pinta la foto (con su aureola) y abre el perfil al tocarla; si no, solo reserva el sitio. */
@Composable
fun ChatSenderAvatarSlot(show: Boolean, photoUrl: String?, name: String, uid: String, onClick: () -> Unit) {
    Box(Modifier.size(ChatAvatarSize)) {
        if (show) {
            Box(Modifier.size(ChatAvatarSize).clip(CircleShape).clickable(onClick = onClick)) {
                PersonAvatar(photoUrl, name, uid, ChatAvatarSize)
            }
        }
    }
}
