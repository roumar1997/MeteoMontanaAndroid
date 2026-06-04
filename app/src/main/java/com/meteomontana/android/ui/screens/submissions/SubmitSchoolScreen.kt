package com.meteomontana.android.ui.screens.submissions

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.meteomontana.android.data.api.dto.SubmitSchoolRequest

@Composable
fun SubmitSchoolScreen(
    onBack: () -> Unit,
    viewModel: SubmitSchoolViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    var name by remember { mutableStateOf("") }
    var region by remember { mutableStateOf("") }
    var style by remember { mutableStateOf("") }
    var rockType by remember { mutableStateOf("") }
    var lat by remember { mutableStateOf("") }
    var lon by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    LaunchedEffect(state) {
        if (state is SubmitState.Done) onBack()
    }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Outlined.ArrowBack, contentDescription = "Volver",
                    tint = MaterialTheme.colorScheme.onBackground)
            }
            Text("Proponer escuela", style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Field("NOMBRE", name, { name = it }, placeholder = "ej: La Pedriza")
            Field("REGIÓN", region, { region = it }, placeholder = "ej: Madrid")
            Field("ESTILO", style, { style = it }, placeholder = "Vía / Bloque")
            Field("TIPO DE ROCA", rockType, { rockType = it }, placeholder = "ej: Granito")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Box(Modifier.weight(1f)) {
                    Field("LATITUD", lat, { lat = it }, placeholder = "40.42")
                }
                Box(Modifier.weight(1f)) {
                    Field("LONGITUD", lon, { lon = it }, placeholder = "-3.70")
                }
            }
            Field("UBICACIÓN", location, { location = it }, placeholder = "Localidad")
            Field("NOTAS", notes, { notes = it }, placeholder = "Cualquier info útil", height = 80.dp)

            if (state is SubmitState.Error) {
                Text((state as SubmitState.Error).message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium)
            }

            Spacer(Modifier.height(8.dp))

            if (state is SubmitState.Submitting) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            } else {
                Button(
                    onClick = {
                        val latD = lat.replace(",", ".").toDoubleOrNull()
                        val lonD = lon.replace(",", ".").toDoubleOrNull()
                        if (name.isNotBlank() && latD != null && lonD != null) {
                            viewModel.submit(SubmitSchoolRequest(
                                name = name.trim(),
                                region = region.takeIf { it.isNotBlank() },
                                style = style.takeIf { it.isNotBlank() },
                                rockType = rockType.takeIf { it.isNotBlank() },
                                lat = latD, lon = lonD,
                                location = location.takeIf { it.isNotBlank() },
                                source = null,
                                notes = notes.takeIf { it.isNotBlank() }
                            ))
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF1C1C1A),
                        contentColor = Color.White
                    ),
                    shape = MaterialTheme.shapes.small
                ) {
                    Text("ENVIAR PROPUESTA")
                }
            }

            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun Field(label: String, value: String, onChange: (String) -> Unit,
                  placeholder: String = "", height: androidx.compose.ui.unit.Dp = 56.dp) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(
            value = value, onValueChange = onChange,
            placeholder = { Text(placeholder) },
            singleLine = height == 56.dp,
            modifier = Modifier.fillMaxWidth().height(height)
        )
    }
}
