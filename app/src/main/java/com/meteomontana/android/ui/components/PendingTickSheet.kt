package com.meteomontana.android.ui.components

import com.meteomontana.android.util.AppText
import androidx.compose.foundation.background
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.CloseFullscreen
import androidx.compose.material.icons.outlined.GpsFixed
import androidx.compose.material.icons.outlined.OpenInFull
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.width
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.meteomontana.android.R
import com.meteomontana.android.domain.model.Block
import com.meteomontana.android.ui.screens.detail.SchoolDetailViewModel
import com.meteomontana.android.ui.screens.detail.ProposeContributionFlow
import com.meteomontana.android.ui.screens.detail.AddLinesFlow
import com.meteomontana.android.ui.theme.EyebrowTextStyle
import com.meteomontana.android.ui.theme.Mono
import com.meteomontana.android.ui.theme.Spacing
import com.meteomontana.android.ui.theme.Terra
import org.maplibre.android.annotations.IconFactory
import org.maplibre.android.annotations.Marker
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.annotations.Polyline
import org.maplibre.android.annotations.PolylineOptions
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import com.meteomontana.android.domain.util.Geo

/** Hoja de ascenso al marcar una vía como hecha: guarda en el diario, publica si se pide y vota el grado. Sacada de SchoolMap (2026-11-05). */
@Composable
internal fun PendingTickSheet(pt: PendingTick, viewModel: com.meteomontana.android.ui.screens.detail.SchoolDetailViewModel, onClose: () -> Unit) {
    val fichaCtx = LocalContext.current
    val ascentVoteVm: CommunityVoteViewModel = androidx.hilt.navigation.compose.hiltViewModel()
    val myHeight by ascentVoteVm.myHeight.collectAsStateWithLifecycle()
    androidx.compose.runtime.LaunchedEffect(Unit) { ascentVoteVm.loadMyHeight() }
    AscentSheet(
        lineName = pt.line.name.ifBlank { stringResource(R.string.school_map_v3_via_2, pt.index + 1) },
        setterGrade = pt.line.grade,
        wasProject = pt.wasProject,
        myHeight = myHeight,
        onSetHeight = { ascentVoteVm.setMyHeight(it) },
        initialShare = com.meteomontana.android.data.local.FeedSharePref.enabled(fichaCtx),
        onSave = { choice ->
            onClose()
            viewModel.viewModelScope.launch {
                // Primero el diario (el voto de grado exige tener la vía en él), luego publicar y votar.
                val r = viewModel.toggleLine(
                    pt.block, pt.line, pt.index, pt.schoolName, pt.sectorName, markDone = true,
                    sessionDate = choice.sessionDate, aVista = choice.aVista, alFlash = choice.alFlash)
                if (r.getOrNull() == true) {
                    if (choice.publish) {
                        viewModel.publishTickToFeed(
                            pt.block, pt.line, pt.wasProject, choice.caption,
                            photoUri = choice.photoUri?.toString(),
                            onPublishFailed = {
                                android.widget.Toast.makeText(
                                    fichaCtx,
                                    AppText.get(R.string.school_map_v3_no_se_pudo_publicar_el),
                                    android.widget.Toast.LENGTH_LONG
                                ).show()
                            },
                            onPhotoUploadFailed = {
                                android.widget.Toast.makeText(
                                    fichaCtx, R.string.feed_photo_upload_failed,
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                            }
                        )
                    }
                    val grade = choice.gradeVote
                    if (grade != null && pt.line.id.isNotEmpty()) ascentVoteVm.voteGrade(pt.line.id, grade)
                }
            }
        },
        onDismiss = { onClose() }
    )
}
