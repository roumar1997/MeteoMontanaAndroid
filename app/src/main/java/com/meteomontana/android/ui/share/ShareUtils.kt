package com.meteomontana.android.ui.share

import com.meteomontana.android.util.AppText
import android.content.Context
import android.content.Intent
import com.meteomontana.android.R
import androidx.compose.ui.res.stringResource

private const val PLAY_URL = "https://play.google.com/store/apps/details?id=com.meteomontana.android"
private const val APPSTORE_URL = "https://apps.apple.com/app/id6785776686"

fun shareMeetup(
    context: Context,
    meetupName: String,
    schoolName: String?,
    days: List<String>,
    discipline: String?,
    memberCount: Int,
    memberLimit: Int?,
    /** Enlace de invitación (solo si somos miembros): con él quien lo reciba
     *  puede unirse aunque no haya relación de follows. Faltaba en Android —
     *  espejo de meetupShareText en MeetupDetailView.swift (Álvaro,
     *  2026-08-24: paridad con iOS). */
    inviteLink: String? = null
) {
    val daysText = days.joinToString(", ") { formatShareDay(it) }
    val plazas = memberLimit?.let { AppText.get(R.string.share_utils_v4_plazas, memberCount, it) } ?: "$memberCount participantes"
    val discText = discipline?.let {
        when (it) {
            "BOULDER" -> AppText.get(R.string.share_utils_v4_bloque); "ROUTE" -> AppText.get(R.string.share_utils_v4_via); "BOTH" -> AppText.get(R.string.share_utils_v4_bloque_via); else -> ""
        }
    } ?: ""

    val text = buildString {
        append("Quedada: $meetupName\n")
        schoolName?.let { append("Escuela: $it\n") }
        append("$daysText$discText · $plazas\n\n")
        if (!inviteLink.isNullOrBlank()) {
            append(AppText.get(R.string.share_utils_v4_unete_desde_aqui_n, inviteLink))
        } else {
            append(AppText.get(R.string.share_utils_v4_buscala_en_cumbre_pestana_quedadas))
            append(AppText.get(R.string.share_utils_v4_descarga_cumbre_n))
            append("Android: $PLAY_URL\n")
            append("iOS: $APPSTORE_URL")
        }
    }
    shareText(context, text, context.getString(R.string.share_utils_v3_compartir_quedada))
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
        score?.let { append(AppText.get(R.string.share_utils_v4_100_para_escalar_hoy, it)) }
        append("\n")
        val details = listOfNotNull(rockType, style, temperature).joinToString(" · ")
        if (details.isNotBlank()) append("$details\n")
        optimalWindow?.let { append(AppText.get(R.string.share_utils_v4_mejor_momento_n, it)) }
        append(AppText.get(R.string.share_utils_v4_ndescarga_cumbre_n))
        append("Android: $PLAY_URL\n")
        append("iOS: $APPSTORE_URL")
    }
    shareText(context, text, context.getString(R.string.share_utils_v3_compartir_escuela))
}

/** Comparte un perfil con su enlace /s/u/ (lo abre la app o lleva a la store). */
fun shareProfile(context: Context, handle: String, displayLabel: String) {
    val text = buildString {
        append(AppText.get(R.string.share_utils_v4_perfil_de_en_cumbre_n, displayLabel))
        append((com.meteomontana.android.ui.share.shareBaseUrl()) + "/s/u/$handle")
    }
    shareText(context, text, context.getString(R.string.share_utils_v3_compartir_perfil))
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

/**
 * P7: base de los enlaces compartidos SEGUN EL BUILD. El debug apunta a
 * staging (donde viven tus datos de prueba); release sigue en prod. OJO:
 * abrir la app directa desde el enlace (App Links) solo funciona con el
 * dominio de prod — en staging se abre la landing web, que es lo esperado.
 */
fun shareBaseUrl(): String =
    if (com.meteomontana.android.BuildConfig.DEBUG)
        "https://meteomontanaapi-staging.up.railway.app"
    else "https://api.climbingteams.com"
