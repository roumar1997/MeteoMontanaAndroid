package com.meteomontana.android.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.meteomontana.android.R
import com.meteomontana.android.domain.model.FeatureFlags
import com.meteomontana.android.ui.theme.EyebrowTextStyle
import com.meteomontana.android.ui.theme.Terra

/**
 * Interruptores de funciones del servidor, disponibles en toda la app. Los provee AppRoot; sin proveedor
 * (previews, tests de capturas) todo cuenta como encendido.
 */
@Suppress("CompositionLocalAllowlist")
val LocalFeatureFlags = staticCompositionLocalOf { FeatureFlags.ALL_ON }

/**
 * Aviso de «esta función está en pausa». Tarjeta Cumbre (papel, borde de 1dp, radio 2dp) con el eyebrow
 * «EN PAUSA». Usa el mensaje del servidor si lo hay (ya viene traducido); si no, el texto de siempre.
 */
@Composable
fun FeatureOffNotice(message: String?, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(2.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, shape)
            .border(1.dp, MaterialTheme.colorScheme.outline, shape)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(stringResource(R.string.feature_off_eyebrow), style = EyebrowTextStyle, color = Terra)
        Text(
            message?.takeIf { it.isNotBlank() } ?: stringResource(R.string.feature_off_message),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}

/** Mismo aviso como Toast, para cuando un envío recibe el 503 y ya no hay pantalla donde pintar la tarjeta. */
fun showFeatureOffToast(context: android.content.Context, message: String?) {
    val text = message?.takeIf { it.isNotBlank() } ?: com.meteomontana.android.util.AppText.get(R.string.feature_off_message)
    android.widget.Toast.makeText(context, text, android.widget.Toast.LENGTH_LONG).show()
}
