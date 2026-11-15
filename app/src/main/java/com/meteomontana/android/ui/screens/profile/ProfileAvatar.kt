package com.meteomontana.android.ui.screens.profile

import com.meteomontana.android.ui.components.SkeletonProfile
import com.meteomontana.android.ui.components.VerifiedCheck
import com.meteomontana.android.ui.components.userRing
import androidx.compose.material.icons.outlined.Language
import com.meteomontana.android.util.AppText
import android.content.Context
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import com.meteomontana.android.ui.guide.guideTarget
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Backpack
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.People
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.meteomontana.android.R
import com.meteomontana.android.domain.model.JournalStats
import com.meteomontana.android.domain.model.PrivateProfile

/** Avatar del perfil propio (foto con aureola, cambiar foto, subida). Se dibuja en la capa sin recorte de [com.meteomontana.android.ui.components.UnclippedAvatarHost]. */
@Composable
internal fun ProfileAvatar(p: PrivateProfile, canChangePhoto: Boolean, onPhotoChanged: () -> Unit) {
    // Foto a pantalla completa con zoom al tocar el avatar.
    var zoomPhoto by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<String?>(null) }
    zoomPhoto?.let { url ->
        com.meteomontana.android.ui.components.FullScreenPhotoDialog(url) { zoomPhoto = null }
    }
    // Tocar tu foto: menú «Cambiar foto / Ver foto» → selector → recorte → se sube (Álvaro, 2026-10-23; espejo de iOS).
    val photoVm: ProfilePhotoViewModel = androidx.hilt.navigation.compose.hiltViewModel()
    val uploading by photoVm.uploading.collectAsStateWithLifecycle()
    val photoFailed by photoVm.failed.collectAsStateWithLifecycle()
    var photoMenu by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val openPicker = rememberProfilePhotoPicker { uri -> photoVm.change(uri, onPhotoChanged) }
    if (photoFailed) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = photoVm::dismissError,
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = photoVm::dismissError) { Text(stringResource(android.R.string.ok)) }
            },
            title = { Text(stringResource(R.string.profile_photo_error_title)) },
            text = { Text(stringResource(R.string.profile_photo_error_body)) }
        )
    }
        // Avatar centrado (algo más pequeño: que el diario y ESTADÍSTICAS se vean sin bajar).
        val onAvatarTap = {
            if (canChangePhoto) photoMenu = true else if (p.photoUrl != null) zoomPhoto = p.photoUrl
        }
        Box {
            if (p.photoUrl != null) {
                androidx.compose.runtime.key(p.photoUrl) {
                    AsyncImage(
                        model = coil.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                            .data(p.photoUrl)
                            .memoryCachePolicy(coil.request.CachePolicy.WRITE_ONLY)
                            .crossfade(200)
                            .build(),
                        contentDescription = stringResource(R.string.profile_photo_title),
                        modifier = Modifier.userRing(p.uid, 76.dp).size(76.dp).clip(CircleShape)
                            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                            .clickable(onClick = onAvatarTap)
                    )
                }
            } else {
                Image(
                    painter = painterResource(R.drawable.logo_cumbre),
                    contentDescription = stringResource(R.string.profile_photo_title),
                    modifier = Modifier.size(76.dp).clip(CircleShape)
                        .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                        .clickable(enabled = canChangePhoto, onClick = onAvatarTap)
                )
            }
            if (uploading) {
                Box(
                    Modifier.size(76.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.35f)),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(24.dp)) }
            }
            if (canChangePhoto) {
                // Icono de cámara: que se vea que la foto se puede tocar.
                Box(
                    Modifier.align(Alignment.BottomEnd).size(24.dp)
                        .clip(CircleShape).background(MaterialTheme.colorScheme.background)
                        .padding(2.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.material3.Icon(
                        androidx.compose.material.icons.Icons.Filled.PhotoCamera, contentDescription = null,
                        tint = Color.White, modifier = Modifier.size(12.dp)
                    )
                }
            }
            com.meteomontana.android.ui.components.CumbreDropdownMenu(expanded = photoMenu, onDismissRequest = { photoMenu = false }) {
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text(stringResource(R.string.profile_photo_change)) },
                    onClick = { photoMenu = false; openPicker() }
                )
                if (p.photoUrl != null) {
                    androidx.compose.material3.DropdownMenuItem(
                        text = { Text(stringResource(R.string.profile_photo_view)) },
                        onClick = { photoMenu = false; zoomPhoto = p.photoUrl }
                    )
                }
            }
        }
}
