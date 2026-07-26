package com.meteomontana.android.ui.screens.meetups

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

/**
 * RC3: diálogo del gate «No Mixto». Antes salía un texto de error críptico abajo;
 * ahora una ventana que lo explica y ofrece ir DIRECTAMENTE a editar el perfil
 * (por si aún no has indicado tu género). Compartido por crear y unirse.
 */
@Composable
fun GenderGateDialog(onEditProfile: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Quedadas «No Mixto»") },
        text = {
            Text(
                "Las quedadas «No Mixto» son solo para perfiles con género Mujer. " +
                    "Si eres mujer y aún no lo has indicado, ponlo en tu perfil y podrás " +
                    "crearlas y unirte.",
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            TextButton(onClick = { onDismiss(); onEditProfile() }) { Text("Editar perfil") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Entendido") }
        }
    )
}
