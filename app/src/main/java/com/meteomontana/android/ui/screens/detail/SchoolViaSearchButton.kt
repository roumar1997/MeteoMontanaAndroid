package com.meteomontana.android.ui.screens.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.meteomontana.android.R
import com.meteomontana.android.domain.model.Block
import com.meteomontana.android.ui.screens.schools.map.SchoolViaSearchBar
import com.meteomontana.android.ui.theme.Terra

/**
 * Lupa redonda junto al nombre de la escuela (mismo estilo que «? AYUDA»: borde terra, fondo terra suave, icono terra).
 * Abre el buscador de vías y bloques de la escuela — antes era una barra fija encima del mapa.
 */
@Composable
fun SchoolViaSearchButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val description = stringResource(R.string.school_search_lines_cd)
    Box(
        modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(Terra.copy(alpha = 0.10f))
            .border(1.2.dp, Terra, CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Outlined.Search, contentDescription = null, tint = Terra, modifier = Modifier.size(20.dp))
    }
}

/**
 * Buscador de vías y bloques de la escuela como diálogo anclado arriba (el teclado no lo tapa). Tocar fuera lo cierra;
 * elegir un resultado lo cierra y abre su piedra ([onPicked] lleva además a la pestaña del mapa).
 */
@Composable
fun SchoolViaSearchDialog(
    blocks: List<Block>,
    viewModel: SchoolDetailViewModel,
    onPicked: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        // Fondo a pantalla completa: tocar fuera cierra; la tarjeta de dentro se queda sus propios toques.
        Box(Modifier.fillMaxSize().clickable(onClick = onDismiss), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 48.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.background)
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
                    .clickable(enabled = false, onClick = {})
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                SchoolViaSearchBar(blocks = blocks, viewModel = viewModel, onPicked = onPicked)
            }
        }
    }
}
