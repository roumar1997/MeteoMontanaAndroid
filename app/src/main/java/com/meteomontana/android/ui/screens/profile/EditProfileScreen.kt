package com.meteomontana.android.ui.screens.profile

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
import androidx.compose.material3.Switch
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
import com.meteomontana.android.data.api.dto.UpdateProfileRequest

@Composable
fun EditProfileScreen(
    onBack: () -> Unit,
    viewModel: EditProfileViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(state) {
        if (state is EditState.Saved) onBack()
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
            Text("Editar perfil", style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)

        when (val s = state) {
            EditState.Loading, EditState.Saving ->
                Box(Modifier.fillMaxSize(), Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            is EditState.Error ->
                Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Text(s.message, color = MaterialTheme.colorScheme.error)
                }
            is EditState.Editing -> EditForm(s, viewModel::save)
            EditState.Saved -> {}
        }
    }
}

@Composable
private fun EditForm(s: EditState.Editing, onSave: (UpdateProfileRequest) -> Unit) {
    var username by remember { mutableStateOf(s.profile.username ?: "") }
    var displayName by remember { mutableStateOf(s.profile.displayName ?: "") }
    var bio by remember { mutableStateOf(s.profile.bio ?: "") }
    var topGrade by remember { mutableStateOf(s.profile.topGrade ?: "") }
    var isPublic by remember { mutableStateOf(s.profile.isPublic) }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Field("USERNAME", username, { username = it.lowercase().replace(" ", "_") },
            placeholder = "ej: alvaro_jara")
        Field("NOMBRE PARA MOSTRAR", displayName, { displayName = it },
            placeholder = "Alvaro Jara")
        Field("BIO (max 150)", bio, { if (it.length <= 150) bio = it },
            placeholder = "Cuéntate en una línea", height = 80.dp)
        Field("GRADO MÁXIMO", topGrade, { topGrade = it },
            placeholder = "ej: 7c")

        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Perfil público",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground)
                Text("Otros podrán verte por @username",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = isPublic, onCheckedChange = { isPublic = it })
        }

        Spacer(Modifier.height(8.dp))

        Button(
            onClick = {
                onSave(UpdateProfileRequest(
                    username = username.takeIf { it.isNotBlank() },
                    displayName = displayName.takeIf { it.isNotBlank() },
                    bio = bio,  // permite vaciar
                    topGrade = topGrade.takeIf { it.isNotBlank() },
                    isPublic = isPublic
                ))
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF1C1C1A),
                contentColor = Color.White
            ),
            shape = MaterialTheme.shapes.small
        ) { Text("GUARDAR") }
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
