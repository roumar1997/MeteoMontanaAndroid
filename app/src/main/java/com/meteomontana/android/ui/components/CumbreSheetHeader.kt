package com.meteomontana.android.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Encabezado de una hoja: botón de salida en pastilla a la izquierda y título
 * CENTRADO.
 *
 * **Por qué un componente y no copiarlo.** Este patrón sale en Comparar,
 * Proponer mejora, Editar piedra y la ficha de piedra — cuatro sitios. Cada uno
 * lo tenía a su manera: unos con flecha, otros con ✕, el título pegado a la
 * izquierda o centrado, y el texto de salida cambiando entre "Cerrar",
 * "Cancelar" y nada. En iOS los cuatro son iguales, y esa uniformidad es
 * justo lo que hace que la app se sienta de una pieza.
 *
 * Ahora la decisión vive aquí: si mañana el botón pasa a ser un icono, o el
 * título cambia de tamaño, se toca un fichero.
 *
 * @param titulo lo que va centrado. Se recorta si no cabe — manda el botón.
 * @param textoSalida "Cerrar" en lo que se consulta, "Cancelar" en lo que se
 *   edita: si el usuario ha escrito algo, la palabra tiene que avisar de que
 *   lo va a descartar.
 */
@Composable
fun CumbreSheetHeader(
    titulo: String,
    onClose: () -> Unit,
    textoSalida: String = "Cerrar",
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
        CumbrePillGroup(modifier = Modifier.align(Alignment.CenterStart)) {
            TextButton(onClick = onClose) {
                Text(
                    textoSalida,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
        Text(
            titulo,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.Center).padding(horizontal = 96.dp)
        )
    }
}
