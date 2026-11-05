package com.meteomontana.android.ui.components

import com.meteomontana.android.R

/**
 * QUÉ BUSCAS junto al filtro de ALTURA (guía y Escuelas → Vías/Bloques): el sufijo que se añade a la altura
 * al pedir las vías al servidor ("TALL" + "_VIDEO") y su texto. "" = vídeo o encadenes.
 */
val BETA_SOURCES: List<Pair<String, Int>> = listOf(
    "" to R.string.filter_beta_any,
    "_VIDEO" to R.string.filter_beta_video,
    "_SENDS" to R.string.filter_beta_sends,
)
