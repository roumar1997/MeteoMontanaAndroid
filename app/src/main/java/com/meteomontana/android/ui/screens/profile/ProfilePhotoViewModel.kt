package com.meteomontana.android.ui.screens.profile

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import com.meteomontana.android.data.api.dto.UpdateProfileRequest
import com.meteomontana.android.domain.model.FileRef
import com.meteomontana.android.domain.port.FileReader
import com.meteomontana.android.domain.port.PhotoUploader
import com.meteomontana.android.domain.usecase.profile.UpdateMyProfileUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import javax.inject.Inject

/**
 * Cambiar tu foto tocándola en el perfil (Álvaro, 2026-10-23), sin pasar por Editar perfil. Una sola
 * responsabilidad: comprimir, subir y guardar SOLO la foto (el servidor deja igual lo que no se manda).
 * Espejo de AccountViewModel.changePhoto en iOS.
 */
@HiltViewModel
class ProfilePhotoViewModel @Inject constructor(
    private val photoUploader: PhotoUploader,
    private val fileReader: FileReader,
    private val updateMyProfile: UpdateMyProfileUseCase
) : ViewModel() {
    private val log = Logger.withTag("ProfilePhotoVM")

    private val _uploading = MutableStateFlow(false)
    val uploading: StateFlow<Boolean> = _uploading.asStateFlow()

    /** true para enseñar el aviso «No se pudo cambiar la foto»; la pantalla lo vuelve a false al cerrarlo. */
    private val _failed = MutableStateFlow(false)
    val failed: StateFlow<Boolean> = _failed.asStateFlow()

    fun dismissError() { _failed.value = false }

    /** Sube la foto ya recortada y, si va bien, llama a [onSaved] para que el perfil se recargue. */
    fun change(uri: Uri, onSaved: () -> Unit) {
        if (_uploading.value) return
        _uploading.value = true
        viewModelScope.launch {
            try {
                val bytes = fileReader.readImageCompressed(FileRef(uri.toString()))
                val url = withTimeout(30_000) { photoUploader.uploadProfilePhoto(bytes, "image/jpeg") }
                withTimeout(15_000) { updateMyProfile(UpdateProfileRequest(photoUrl = url)) }
                onSaved()
            } catch (t: Throwable) {
                log.e("change photo failed", t)
                _failed.value = true
            } finally {
                _uploading.value = false
            }
        }
    }
}
