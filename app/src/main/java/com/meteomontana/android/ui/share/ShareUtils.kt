package com.meteomontana.android.ui.share

import android.content.Context
import android.content.Intent

private const val PLAY_URL = "https://play.google.com/store/apps/details?id=com.meteomontana.android"
private const val APPSTORE_URL = "https://apps.apple.com/app/id6785776686"

fun shareMeetup(
    context: Context,
    meetupName: String,
    schoolName: String?,
    days: List<String>,
    discipline: String?,
    memberCount: Int,
    memberLimit: Int?
) {
    val daysText = days.joinToString(", ") { formatShareDay(it) }
    val plazas = memberLimit?.let { "$memberCount/$it plazas" } ?: "$memberCount participantes"
    val discText = discipline?.let {
        when (it) {
            "BOULDER" -> " · Bloque"; "ROUTE" -> " · Vía"; "BOTH" -> " · Bloque + Vía"; else -> ""
        }
    } ?: ""

    val text = buildString {
        append("Quedada: $meetupName\n")
        schoolName?.let { append("Escuela: $it\n") }
        append("$daysText$discText · $plazas\n\n")
        append("Descarga Cumbre:\n")
        append("Android: $PLAY_URL\n")
        append("iOS: $APPSTORE_URL")
    }
    shareText(context, text, "Compartir quedada")
}

fun shareSchool(
    context: Context,
    schoolName: String,
    score: Int?,
    rockType: String?,
    style: String?,
    temperature: String?,
    optimalWindow: String?
) {
    val text = buildString {
        append("$schoolName")
        score?.let { append(" — $it/100 para escalar hoy") }
        append("\n")
        val details = listOfNotNull(rockType, style, temperature).joinToString(" · ")
        if (details.isNotBlank()) append("$details\n")
        optimalWindow?.let { append("Mejor momento: $it\n") }
        append("\nDescarga Cumbre:\n")
        append("Android: $PLAY_URL\n")
        append("iOS: $APPSTORE_URL")
    }
    shareText(context, text, "Compartir escuela")
}

private fun shareText(context: Context, text: String, title: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, title))
}

private fun formatShareDay(iso: String): String {
    val months = listOf("ene","feb","mar","abr","may","jun","jul","ago","sep","oct","nov","dic")
    val parts = iso.split("-")
    if (parts.size != 3) return iso
    val mo = parts[1].toIntOrNull() ?: return iso
    val d = parts[2].toIntOrNull() ?: return iso
    return "$d ${months.getOrElse(mo - 1) { "?" }}"
}
