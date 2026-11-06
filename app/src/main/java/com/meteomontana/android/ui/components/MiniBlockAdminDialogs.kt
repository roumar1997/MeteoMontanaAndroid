package com.meteomontana.android.ui.components

import com.meteomontana.android.util.AppText
import com.meteomontana.android.ui.theme.terraFillColor
import com.meteomontana.android.data.map.MapStyles
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.background
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
import com.meteomontana.android.ui.guide.guideTarget
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

/** Admin desde la mini-ficha de parking/sector: editarlo o eliminarlo (con confirmación). */
@Composable
internal fun MiniBlockAdminDialogs(
    editing: Block?,
    deleting: Block?,
    viewModel: com.meteomontana.android.ui.screens.detail.SchoolDetailViewModel,
    scope: kotlinx.coroutines.CoroutineScope,
    onCloseEdit: () -> Unit,
    onCloseDelete: () -> Unit,
    /** Hecho: se cierran el diálogo y la mini-ficha. */
    onDone: () -> Unit
) {
    editing?.let { mb ->
        EditBlockDialog(
            block = mb,
            onSave = { req ->
                scope.launch {
                    viewModel.adminUpdateBlock(mb.id, req)
                    onDone()
                }
            },
            onDismiss = onCloseEdit
        )
    }
    deleting?.let { mb ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = onCloseDelete,
            title = { Text(stringResource(R.string.school_map_view_v2_eliminar_1_s, mb.name.ifBlank { if (mb.type == "PARKING") "parking" else "sector" })) },
            text = { Text(stringResource(R.string.school_map_view_esta_accion_no_se)) },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    viewModel.deleteBlock(mb.id) {}
                    onDone()
                }) { Text(stringResource(R.string.school_map_view_v2_eliminar), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = onCloseDelete) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }

}
