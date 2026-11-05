package com.meteomontana.android.ui.screens.detail

import com.meteomontana.android.util.AppText
import com.meteomontana.android.ui.theme.CumbrePillShape
import com.meteomontana.android.ui.theme.terraFillColor
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.meteomontana.android.domain.model.FileRef
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.meteomontana.android.R
import com.meteomontana.android.ui.components.CumbreSheetShape
import com.meteomontana.android.ui.components.cumbreSheetSurface
import com.meteomontana.android.domain.model.Block
import com.meteomontana.android.data.outbox.toQueued
import com.meteomontana.android.ui.components.TopoLine
import com.meteomontana.android.ui.components.TopoPhotoCanvas
import com.meteomontana.android.ui.theme.EyebrowTextStyle
import com.meteomontana.android.ui.theme.Serif
import com.meteomontana.android.ui.theme.Spacing
import com.meteomontana.android.ui.theme.Terra
import com.meteomontana.android.ui.theme.colorForGrade
import com.meteomontana.android.ui.theme.gradeStyle
import kotlinx.coroutines.launch

// Caras de una piedra al editarla: el modelo, la salida en el formato del formulario y las caras de partida.


/**
 * Una cara editable de la piedra dentro del editor: la foto actual (remota) y/o
 * una foto nueva elegida que la sustituye, más sus vías. Las vías existentes
 * llevan `existingLineId`; las nuevas no.
 */
internal data class EditFace(
    val existingPhotoPath: String?,   // foto remota actual (null si cara nueva)
    val newPhotoUri: Uri?,            // foto local elegida (sustituye a la anterior)
    val bloques: List<BoulderBloqueForm>
) {
    /** Modelo para Coil: la nueva si la hay, si no la URL remota. */
    val photoModel: Any? get() = newPhotoUri ?: existingPhotoPath?.takeIf { it.isNotBlank() }
    val hasPhoto: Boolean get() = photoModel != null
}

/** STAND/SIT/JUMP/TRAV (backend) → PIE/SIT/LANCE/TRAV (app). */
internal fun startTypeForBoulderUi(raw: String?): String? = when (raw?.uppercase()) {
    "STAND", "PIE"  -> "PIE"
    "SIT"           -> "SIT"
    "SEMI"          -> "SEMI"
    "JUMP", "LANCE" -> "LANCE"
    "TRAV"          -> "TRAV"
    else            -> null
}

/**
 * Construye el estado editable inicial (caras + vías) desde una piedra existente.
 * Lo usa el contenedor (SchoolMap) para elevar el estado del editor y que
 * sobreviva mientras el diálogo se oculta para trazar el muro en el mapa.
 */
internal fun initialEditFaces(block: Block): List<EditFace> =
    block.facesOrDerived().let { fs ->
        if (fs.isEmpty()) listOf(EditFace(block.photoPath, null, listOf(BoulderBloqueForm())))
        else fs.map { f ->
            EditFace(
                existingPhotoPath = f.photoPath,
                newPhotoUri = null,
                bloques = f.lines.map { l ->
                    BoulderBloqueForm(
                        name = l.name, grade = l.grade,
                        startType = startTypeForBoulderUi(l.startType?.toString()),
                        linePath = com.meteomontana.android.ui.screens.topo.parseLineStroke(l.linePath).points,
                        existingLineId = l.id,
                        description = l.lineDescription,
                        variant = l.variant
                    )
                }.ifEmpty { listOf(BoulderBloqueForm()) }
            )
        }
    }
