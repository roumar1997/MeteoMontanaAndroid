package com.meteomontana.android.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Arrangement
import com.meteomontana.android.R
import com.meteomontana.android.domain.util.SchoolKind
import com.meteomontana.android.ui.theme.Terra

/** Proporciones de los PNG (ancho / alto) para fijar el ancho a partir de la altura. */
private const val ROUTE_ASPECT = 310f / 420f
private const val BOULDER_ASPECT = 480f / 360f

/**
 * Iconos de vía/bloque junto al nombre de una escuela: mosquetón = vías,
 * crashpad = bloques, los dos si es mixta. Teñidos de terra; nada si no se sabe
 * el estilo. Espejo de `SchoolKindIcons` en iOS (2026-10-06).
 */
@Composable
fun SchoolKindIcons(kind: SchoolKind, modifier: Modifier = Modifier, height: Dp = 18.dp) {
    if (kind.isEmpty) return
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (kind.hasRoutes) {
            Image(
                painter = painterResource(R.drawable.kind_route),
                contentDescription = stringResource(R.string.w_route),
                colorFilter = ColorFilter.tint(Terra),
                contentScale = ContentScale.Fit,
                modifier = Modifier.height(height).aspectRatio(ROUTE_ASPECT)
            )
        }
        if (kind.hasBoulders) {
            Image(
                painter = painterResource(R.drawable.kind_boulder),
                contentDescription = stringResource(R.string.w_boulder),
                colorFilter = ColorFilter.tint(Terra),
                contentScale = ContentScale.Fit,
                modifier = Modifier.height(height).aspectRatio(BOULDER_ASPECT)
            )
        }
    }
}
