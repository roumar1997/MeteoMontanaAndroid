package com.meteomontana.android.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

// Cumbre apenas usa radius: 0/2/4 px EN EL CONTENIDO (cards, diálogos, fichas).
// Es a propósito — ver DESIGN.md §1: diferencia a Cumbre de la app "genérica
// redondeada de iOS de fábrica".
val CumbreShapes = Shapes(
    extraSmall = RoundedCornerShape(0.dp),
    small      = RoundedCornerShape(2.dp),
    medium     = RoundedCornerShape(2.dp),
    large      = RoundedCornerShape(4.dp),
    extraLarge = RoundedCornerShape(4.dp),
)

/**
 * Forma de píldora para CONTROLES interactivos (botones, chips de filtro,
 * pestañas) — no para cards ni diálogos, esos siguen con [CumbreShapes].
 * Decisión de Rodrigo, 2026-08-21: los controles se sienten más "app nativa
 * de iOS" redondeados, pero el contenido se queda recto porque es lo que
 * diferencia a Cumbre de una app genérica (ver DESIGN.md §1.10).
 */
val CumbrePillShape = CircleShape

/**
 * Radio suave para tarjetas de ESTADÍSTICA (bloques, vías, máx grado…) que
 * quieren sentirse parte del mismo repaso redondeado sin llegar a píldora —
 * a diferencia de un chip, no son alargadas: una píldora completa en algo
 * casi cuadrado se ve como un óvalo forzado, no como una tarjeta.
 */
val CumbreStatCardShape = RoundedCornerShape(14.dp)
