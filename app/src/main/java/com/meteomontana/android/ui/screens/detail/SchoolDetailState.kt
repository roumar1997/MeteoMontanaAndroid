package com.meteomontana.android.ui.screens.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meteomontana.android.data.api.dto.ContributionRequest
import com.meteomontana.android.data.api.dto.CreateBlockRequest
import com.meteomontana.android.data.stats.MonthlyStats
import com.meteomontana.android.data.stats.MonthlyStatsRepository
import com.meteomontana.android.data.saved.SavedSchoolRepository
import com.meteomontana.android.domain.model.FileRef
import com.meteomontana.android.domain.port.FileReader
import com.meteomontana.android.domain.port.PhotoUploader
import com.meteomontana.android.domain.model.Block
import com.meteomontana.android.domain.util.FotosDeEscuela
import com.meteomontana.android.domain.model.Forecast
import com.meteomontana.android.domain.model.Note
import com.meteomontana.android.domain.model.School
import com.meteomontana.android.domain.journal.journalViaKey
import com.meteomontana.android.domain.usecase.blocks.CreateBlockUseCase
import com.meteomontana.android.domain.usecase.blocks.DeleteBlockUseCase
import com.meteomontana.android.domain.usecase.blocks.GetBlocksUseCase
import com.meteomontana.android.domain.usecase.favorites.AddFavoriteUseCase
import com.meteomontana.android.domain.usecase.favorites.RemoveFavoriteUseCase
import com.meteomontana.android.domain.usecase.notes.CreateNoteUseCase
import com.meteomontana.android.domain.usecase.notes.GetNotesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

// Estado de la ficha de escuela, la oferta de fotos sin conexión y qué vías de una piedra tienes hechas.

sealed interface SchoolDetailUiState {
    data object Loading : SchoolDetailUiState
    data class Error(val message: String) : SchoolDetailUiState
    data class Success(
        val school: School,
        val forecast: Forecast?,
        val forecastError: String?,
        val notes: List<Note>,
        val isFavorite: Boolean,
        val blocks: List<Block>,
        val isCurrentUserAdmin: Boolean = false,
        val monthlyStats: MonthlyStats? = null,
        val monthlyLoading: Boolean = false,
        val isSavedOffline: Boolean = false,
        /** Si !=null, los datos provienen del snapshot offline guardado en esa fecha (epoch ms). */
        val offlineSnapshotAt: Long? = null,
        /** Si !=null, el forecast viene de la caché local y se bajó en esa fecha (epoch ms). */
        val forecastCachedAt: Long? = null,
        /** Boletín de montaña AEMET si la escuela cae en uno de los 9 macizos. */
        val mountainBulletin: com.meteomontana.android.domain.model.MountainBulletin? = null,
        /** Aproximaciones (parking → sector) — APPROACH_DESIGN.md, admin-gated. */
        val approaches: List<com.meteomontana.android.domain.model.Approach> = emptyList(),
        /** Si !=null, hay que preguntar al usuario si baja las fotos (puede ir
         *  por datos). Guardar los datos no se pregunta: pesan poco. */
        val ofertaFotosOffline: OfertaFotosOffline? = null,
        /** Progreso 0..1 mientras bajan las fotos; null = no hay descarga. */
        val descargaFotos: Float? = null,
        /** Cuántas fotos NO se pudieron bajar. Se avisa: prometer fotos que
         *  luego faltan se descubre ya sin cobertura, cuando no hay remedio. */
        val fotosOfflineFallidas: Int? = null
    ) : SchoolDetailUiState
}

/** Lo que se le ofrece al usuario descargar para ver la escuela sin cobertura. */
data class OfertaFotosOffline(val cuantas: Int, val bytesEstimados: Long)

/**
 * Ids de las vías de [block] cuyo diario casa con alguna clave de [keys]:
 * por lineId ("escuela|#id", aguanta homónimas — fix "La ola") o por nombre
 * como LEGADO (entradas antiguas sin lineId). Es la traducción diario → ✓ de
 * la ficha de piedra; función PURA para testearla sin Compose.
 */
fun matchedLineIds(
    block: com.meteomontana.android.domain.model.Block,
    keys: Set<String>
): Set<String> =
    block.lines.mapIndexedNotNull { idx, line ->
        val viaName = line.name.ifBlank { "Vía ${idx + 1}" }
        val idKey = journalViaKey(block.schoolId, line.id.takeIf { it.isNotBlank() }, viaName)
        val nameKey = "${block.schoolId}|${viaName.trim().lowercase()}"
        if (keys.contains(idKey) || keys.contains(nameKey)) line.id else null
    }.toSet()
