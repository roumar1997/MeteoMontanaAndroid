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
    modifier: Modifier = Modifier,
    /**
     * Accion principal a la DERECHA, en su propia pastilla (Guardar, Enviar...).
     *
     * Va en la cabecera y no al final del contenido porque la cabecera esta
     * siempre a la vista: si la hoja se abre a media altura o el contenido es
     * largo, un boton al fondo sencillamente no se ve —le paso a Rodrigo con
     * "Mi material"—. En iOS es asi desde siempre.
     */
    accion: (@Composable () -> Unit)? = null
) {
    // Row con peso, NO un Box con alineaciones absolutas: así el título ocupa
    // solo el hueco que queda entre los dos botones y es IMPOSIBLE que se
    // solapen. Con el Box, un botón ancho ("ENVIAR PROPUESTA") se comía el
    // título y se leía "Cancelar Editar piedr[ENVIAR PROPUESTA]" (Álvaro,
    // 2026-08-24).
    androidx.compose.foundation.layout.Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CumbrePillGroup {
            TextButton(onClick = onClose) {
                Text(
                    textoSalida,
                    maxLines = 1,
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
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
        )
        if (accion != null) {
            CumbrePillGroup { accion() }
        }
    }
}
