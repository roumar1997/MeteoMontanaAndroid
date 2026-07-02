package com.meteomontana.android.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import android.app.Activity
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import com.yalantis.ucrop.UCrop
import java.io.File
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.meteomontana.android.R
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
                Icon(Icons.Outlined.ArrowBack, contentDescription = stringResource(R.string.common_back),
                    tint = MaterialTheme.colorScheme.onBackground)
            }
            Text(stringResource(R.string.profile_edit), style = MaterialTheme.typography.headlineLarge,
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
            is EditState.Editing -> EditForm(
                s, viewModel::save,
                onPickPhoto = viewModel::uploadPhoto
            )
            EditState.Saved -> {}
        }
    }
}

@Composable
private fun EditForm(
    s: EditState.Editing,
    onSave: (UpdateProfileRequest) -> Unit,
    onPickPhoto: (Uri) -> Unit
) {
    var username by remember { mutableStateOf(s.profile.username ?: "") }
    var displayName by remember { mutableStateOf(s.profile.displayName ?: "") }
    var bio by remember { mutableStateOf(s.profile.bio ?: "") }
    var topGrade by remember { mutableStateOf(s.profile.topGrade ?: "") }
    var isPublic by remember { mutableStateOf(s.profile.isPublic) }
    var gender by remember { mutableStateOf(s.profile.gender ?: "") }
    // Material propio: se autorrellena al unirte a una quedada (sigue siendo
    // editable ahí para esa quedada concreta). Mismo formato que meetup gear.
    val gearState = remember {
        mutableMapOf<String, Int>().apply {
            val current = com.meteomontana.android.ui.screens.meetups.parseGear(s.profile.gearJson)
            com.meteomontana.android.ui.screens.meetups.gearItemsForDiscipline(null).forEach { (key, _) ->
                put(key, current[key] ?: 0)
            }
        }
    }
    var gearVersion by remember { mutableStateOf(0) }

    val context = LocalContext.current

    // uCrop devuelve un Uri al fichero ya recortado.
    val cropLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val resultUri = result.data?.let { UCrop.getOutput(it) }
            resultUri?.let(onPickPhoto)
        }
    }

    // Picker normal — al elegir, lanzamos uCrop con la foto seleccionada.
    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val destFile = File(context.cacheDir, "profile_crop_${System.currentTimeMillis()}.jpg")
            val destUri = Uri.fromFile(destFile)
            val intent = UCrop.of(uri, destUri)
                .withAspectRatio(1f, 1f)
                .withMaxResultSize(1024, 1024)
                .withOptions(UCrop.Options().apply {
                    setCircleDimmedLayer(true)        // máscara circular
                    setShowCropFrame(false)
                    setShowCropGrid(false)
                    setHideBottomControls(false)
                    setFreeStyleCropEnabled(false)
                    setCompressionQuality(85)
                    setToolbarTitle("Recortar foto")
                })
                .getIntent(context)
            cropLauncher.launch(intent)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Foto perfil clickable
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(80.dp), contentAlignment = Alignment.Center) {
                if (s.profile.photoUrl != null) {
                    // key() fuerza re-creación del AsyncImage cuando cambia la URL,
                    // así Coil no se queda con la imagen vieja en memoria mientras carga la nueva.
                    androidx.compose.runtime.key(s.profile.photoUrl) {
                        AsyncImage(
                            model = coil.request.ImageRequest.Builder(LocalContext.current)
                                .data(s.profile.photoUrl)
                                .memoryCachePolicy(coil.request.CachePolicy.WRITE_ONLY)
                                .crossfade(200)
                                .build(),
                            contentDescription = null,
                            modifier = Modifier.size(80.dp).clip(CircleShape).clickable { picker.launch("image/*") }
                        )
                    }
                } else {
                    Image(
                        painter = painterResource(R.drawable.logo_cumbre),
                        contentDescription = null,
                        modifier = Modifier.size(80.dp).clip(CircleShape).clickable { picker.launch("image/*") }
                    )
                }
                if (s.uploadingPhoto) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
            Spacer(Modifier.padding(start = 16.dp))
            Text(
                if (s.uploadingPhoto) "Subiendo foto…" else "Tocar la foto para cambiarla",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Field("USERNAME", username, { username = it.lowercase().replace(" ", "_") },
            placeholder = "ej: alvaro_jara")
        Field("NOMBRE PARA MOSTRAR", displayName, { displayName = it },
            placeholder = "Alvaro Jara")
        Field("BIO (max 150)", bio, { if (it.length <= 150) bio = it },
            placeholder = "Cuéntate en una línea", height = 80.dp)
        // GRADO MÁXIMO: ahora se calcula automáticamente desde tu diario.
        Column(modifier = Modifier.fillMaxWidth()) {
            Text("GRADO MÁXIMO",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(if (topGrade.isBlank()) "Se calcula desde tu diario" else "$topGrade · automático",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary)
        }

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

        GenderSelector(selected = gender, onSelect = { gender = it })

        GearSelector(gearState = gearState, version = gearVersion, onChange = { gearVersion++ })

        Spacer(Modifier.height(8.dp))

        Button(
            onClick = {
                onSave(UpdateProfileRequest(
                    username = username.takeIf { it.isNotBlank() },
                    displayName = displayName.takeIf { it.isNotBlank() },
                    bio = bio,  // permite vaciar
                    topGrade = topGrade.takeIf { it.isNotBlank() },
                    isPublic = isPublic,
                    gender = gender.takeIf { it.isNotBlank() },
                    gearJson = com.meteomontana.android.ui.screens.meetups.buildGearJson(gearState)
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

@OptIn(ExperimentalLayoutApi::class)
/**
 * Material propio del perfil (cuerda/grigri sí-no, cintas/crashpads cantidad).
 * Al unirte a una quedada se usa para autorrellenar tu material (sigue siendo
 * editable ahí, para esa quedada concreta). Reutiliza los helpers de gear de
 * quedadas para no duplicar el formato/parsing del JSON.
 */
@Composable
private fun GearSelector(gearState: MutableMap<String, Int>, version: Int, onChange: () -> Unit) {
    @Suppress("UNUSED_VARIABLE") val v = version // fuerza recomposición al cambiar
    Column(modifier = Modifier.fillMaxWidth()) {
        Text("MI MATERIAL",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            com.meteomontana.android.ui.screens.meetups.gearItemsForDiscipline(null).forEach { (key, label) ->
                if (com.meteomontana.android.ui.screens.meetups.isBooleanGearKey(key)) {
                    com.meteomontana.android.ui.screens.meetups.GearToggle(
                        label = label,
                        checked = (gearState[key] ?: 0) > 0,
                        onToggle = { gearState[key] = if ((gearState[key] ?: 0) > 0) 0 else 1; onChange() }
                    )
                } else {
                    com.meteomontana.android.ui.screens.meetups.GearStepper(
                        label = label,
                        value = gearState[key] ?: 0,
                        onMinus = {
                            val cur = gearState[key] ?: 0
                            if (cur > 0) { gearState[key] = cur - 1; onChange() }
                        },
                        onPlus = { gearState[key] = (gearState[key] ?: 0) + 1; onChange() }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GenderSelector(selected: String, onSelect: (String) -> Unit) {
    val options = listOf("WOMAN" to "Mujer", "MAN" to "Hombre", "OTHER" to "Otro", "" to "No indicar")
    Column(modifier = Modifier.fillMaxWidth()) {
        Text("GÉNERO (privado — solo para quedadas no mixtas)",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { (value, label) ->
                FilterChip(
                    selected = selected == value,
                    onClick = { onSelect(value) },
                    label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                )
            }
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
