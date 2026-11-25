package com.meteomontana.android.ui.components

import com.meteomontana.android.core.ui.R

/** Texto del aviso de linea nueva del feed segun la disciplina. */

/** «BLOQUE NUEVO» / «VÍA NUEVA» / «ASCENSO NUEVO» según la modalidad del post. */
fun newLineLabelRes(discipline: String?): Int = when (com.meteomontana.android.domain.model.LineKind.of(discipline)) {
    com.meteomontana.android.domain.model.LineKind.BOULDER -> R.string.feed_kind_new_line_boulder
    com.meteomontana.android.domain.model.LineKind.ROUTE -> R.string.feed_kind_new_line_route
    com.meteomontana.android.domain.model.LineKind.UNKNOWN -> R.string.feed_kind_new_line_generic
}
