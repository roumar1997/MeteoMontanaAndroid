package com.meteomontana.android.ui.screens.profile
import com.meteomontana.android.util.toUserMessage

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import com.meteomontana.android.data.api.dto.UpdateProfileRequest
import com.meteomontana.android.domain.model.FileRef
import com.meteomontana.android.domain.model.PrivateProfile
import com.meteomontana.android.domain.port.FileReader
import com.meteomontana.android.domain.port.PhotoUploader
import com.meteomontana.android.domain.usecase.profile.GetMyProfileUseCase
import com.meteomontana.android.domain.usecase.profile.UpdateMyProfileUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import javax.inject.Inject

sealed interface EditState {
    data object Loading : EditState
    data class Editing(val profile: PrivateProfile, val uploadingPhoto: Boolean = false) : EditState
    data object Saving : EditState
    data object Saved : EditState
    data class Error(val message: String) : EditState
}

@HiltViewModel
class EditProfileViewModel @Inject constructor(
    private val getMyProfile: GetMyProfileUseCase,
    private val updateMyProfile: UpdateMyProfileUseCase,
    private val photoUploader: PhotoUploader,
    private val fileReader: FileReader
) : ViewModel() {
    private val _state = MutableStateFlow<EditState>(EditState.Loading)
    val state: StateFlow<EditState> = _state.asStateFlow()
    private val log = Logger.withTag("EditProfileVM")

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.value = try {
                EditState.Editing(getMyProfile())
            } catch (t: Throwable) {
                EditState.Error(t.toUserMessage())
            }
        }
    }

    fun save(req: UpdateProfileRequest) {
        _state.value = EditState.Saving
        viewModelScope.launch {
            _state.value = try {
                updateMyProfile(req)
                EditState.Saved
            } catch (t: Throwable) {
                EditState.Error(t.toUserMessage())
            }
        }
    }

    fun uploadPhoto(uri: Uri) {
        val cur = _state.value as? EditState.Editing ?: return
        _state.value = cur.copy(uploadingPhoto = true)
        viewModelScope.launch {
            val t0 = System.currentTimeMillis()
            try {
                log.i("uploadPhoto: start uri=$uri")
                // 1) Compresión vía FileReader (impl Android usa Bitmap; iOS usará UIImage).
                val bytes = fileReader.readImageCompressed(FileRef(uri.toString()))
                log.i("compressed ${bytes.size}B in ${System.currentTimeMillis() - t0}ms")

                // 2) Subida a Firebase Storage con timeout 30s.
                val url = try {
                    withTimeout(30_000) {
                        photoUploader.uploadProfilePhoto(bytes, "image/jpeg")
                    }
                } catch (e: TimeoutCancellationException) {
                    error("Subida a Storage tardó >30s. Revisa reglas de Storage y conexión WiFi.")
                }
                log.i("storage upload ok url=$url")

                // 3) PUT /api/me con la photoUrl — timeout 15s.
                val updated = try {
                    withTimeout(15_000) {
                        updateMyProfile(UpdateProfileRequest(photoUrl = url))
                    }
                } catch (e: TimeoutCancellationException) {
                    error("Backend tardó >15s en /me. Verifica que esté arrancado.")
                }
                log.i("PUT /me ok photoUrl=${updated.photoUrl}")

                _state.value = EditState.Editing(updated, uploadingPhoto = false)
            } catch (t: Throwable) {
                log.e("uploadPhoto failed", t)
                _state.value = EditState.Error("Foto: ${t.message ?: t.javaClass.simpleName}")
            }
        }
    }
}
