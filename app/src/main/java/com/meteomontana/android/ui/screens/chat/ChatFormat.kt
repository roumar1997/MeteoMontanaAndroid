package com.meteomontana.android.ui.screens.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meteomontana.android.R
import com.meteomontana.android.domain.port.ChatService
import com.meteomontana.android.ui.theme.Mono
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// Formato y piezas comunes de los chats (lista, 1 a 1 y grupos). Espejo de chatListDate / chatDayStarts /
// ChatDayDivider / chatBubble de iOS (ChatListView.swift y ChatView.swift).

/** Burbuja redondeada de un mensaje (antes 8 dp, casi cuadrada). */
val ChatBubbleShape = RoundedCornerShape(16.dp)

private fun sameDay(a: Calendar, b: Calendar) =
    a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)

private fun cal(millis: Long) = Calendar.getInstance().apply { timeInMillis = millis }

/** Fecha para la lista de conversaciones: HH:mm si es hoy; si no, «06 sep» (el mes escrito no se confunde con día/mes). */
fun chatListDate(millis: Long?): String {
    if (millis == null || millis <= 0) return ""
    val pattern = if (sameDay(cal(millis), Calendar.getInstance())) "HH:mm" else "dd MMM"
    return SimpleDateFormat(pattern, Locale.getDefault()).format(Date(millis)).replace(".", "")
}

/** Mensajes que abren un día nuevo (id → su fecha), para pintar el separador una sola vez por día. */
fun chatDayStarts(messages: List<ChatService.ChatMessage>): Map<String, Long> {
    val out = HashMap<String, Long>()
    var last: Calendar? = null
    for (m in messages) {
        val ms = m.createdAtMillis ?: continue
        val c = cal(ms)
        if (last == null || !sameDay(c, last)) { out[m.id] = ms; last = c }
    }
    return out
}

/** «HOY», «AYER» o «6 SEP» centrado en una píldora, encima del primer mensaje de cada día. */
@Composable
fun ChatDayDivider(millis: Long) {
    val c = cal(millis)
    val today = Calendar.getInstance()
    val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
    val label = when {
        sameDay(c, today) -> stringResource(R.string.chat_day_today)
        sameDay(c, yesterday) -> stringResource(R.string.chat_day_yesterday)
        else -> SimpleDateFormat("d MMM", Locale.getDefault()).format(Date(millis)).replace(".", "")
    }
    Box(Modifier.fillMaxWidth().padding(vertical = 4.dp), contentAlignment = Alignment.Center) {
        Text(
            label.uppercase(Locale.getDefault()),
            fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 9.sp, letterSpacing = 1.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 10.dp, vertical = 3.dp)
        )
    }
}
