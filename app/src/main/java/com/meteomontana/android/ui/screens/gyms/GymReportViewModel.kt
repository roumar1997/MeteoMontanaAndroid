package com.meteomontana.android.ui.screens.gyms

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meteomontana.android.domain.model.FileRef
import com.meteomontana.android.domain.model.gym.GymCorrection
import com.meteomontana.android.domain.model.gym.GymDiscipline
import com.meteomontana.android.domain.model.gym.GymKind
import com.meteomontana.android.domain.model.gym.GymReportIssue
import com.meteomontana.android.domain.model.gym.NewGymDraft
import com.meteomontana.android.domain.port.FileReader
import com.meteomontana.android.domain.usecase.gym.SubmitGymCorrectionUseCase
import com.meteomontana.android.domain.usecase.gym.SubmitNewGymUseCase
import com.meteomontana.android.domain.usecase.gym.UploadGymReportPhotoUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class ReportStatus { IDLE, SENDING, SENT, FAILED }

/** «Corregir datos» y «Añadir un muro que falta»: foto opcional (se sube primero) y envío al admin. */
@HiltViewModel
class GymReportViewModel @Inject constructor(
    private val submitCorrection: SubmitGymCorrectionUseCase,
    private val submitNewGym: SubmitNewGymUseCase,
    private val uploadPhoto: UploadGymReportPhotoUseCase,
    private val fileReader: FileReader
) : ViewModel() {

    private val _status = MutableStateFlow(ReportStatus.IDLE)
    val status: StateFlow<ReportStatus> = _status

    fun reset() { _status.value = ReportStatus.IDLE }

    fun sendCorrection(gymId: Long, issues: Set<GymReportIssue>, comment: String, photoUri: String?) {
        send(photoUri) { url ->
            submitCorrection.execute(
                GymCorrection(gymId, issues.toList(), comment.trim().ifEmpty { null }, url)
            )
        }
    }

    fun sendNewGym(
        name: String, lat: Double, lon: Double, kind: GymKind, boulder: Boolean, rope: Boolean, photoUri: String?
    ) {
        val discipline = when {
            boulder && rope -> GymDiscipline.BOTH
            boulder -> GymDiscipline.BOULDER
            rope -> GymDiscipline.ROPE
            else -> null
        }
        send(photoUri) { url ->
            submitNewGym.execute(
                NewGymDraft(
                    name = name.trim(), lat = lat, lon = lon, kind = kind, discipline = discipline,
                    outdoor = kind == GymKind.FREE, photoUrl = url
                )
            )
        }
    }

    private fun send(photoUri: String?, submit: suspend (photoUrl: String?) -> Unit) {
        if (_status.value == ReportStatus.SENDING) return
        _status.value = ReportStatus.SENDING
        viewModelScope.launch {
            val ok = runCatching {
                // readImageCompressed hornea la rotación EXIF (las fotos verticales no salen de lado).
                val url = photoUri?.let { uploadPhoto.execute(fileReader.readImageCompressed(FileRef(it))) }
                submit(url)
            }.isSuccess
            _status.value = if (ok) ReportStatus.SENT else ReportStatus.FAILED
        }
    }
}
