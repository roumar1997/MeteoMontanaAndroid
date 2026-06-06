package com.meteomontana.android.ui.screens.profile
import com.meteomontana.android.util.toUserMessage

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meteomontana.android.data.api.ProfileApi
import com.meteomontana.android.data.api.dto.UpdateProfileRequest
import com.meteomontana.android.data.api.dto.toDomain
import com.meteomontana.android.domain.model.PrivateProfile
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject

sealed interface EditState {
    data object Loading : EditState
    data class Editing(val profile: PrivateProfile) : EditState
    data object Saving : EditState
    data object Saved : EditState
    data class Error(val message: String) : EditState
}

@HiltViewModel
class EditProfileViewModel @Inject constructor(
    private val api: ProfileApi,
    @ApplicationContext private val context: Context
) : ViewModel() {
    private val _state = MutableStateFlow<EditState>(EditState.Loading)
    val state: StateFlow<EditState> = _state.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.value = try {
                EditState.Editing(api.getMyProfile().toDomain())
            } catch (t: Throwable) {
                EditState.Error(t.toUserMessage())
            }
        }
    }

    fun save(req: UpdateProfileRequest) {
        _state.value = EditState.Saving
        viewModelScope.launch {
            _state.value = try {
                api.updateMyProfile(req)
                EditState.Saved
            } catch (t: Throwable) {
                EditState.Error(t.toUserMessage())
            }
        }
    }

    fun uploadPhoto(uri: Uri) {
        _state.value = EditState.Saving
        viewModelScope.launch {
            _state.value = try {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: throw IllegalStateException("No se pudo leer la imagen")
                val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
                val ext = when {
                    mime.contains("png")  -> "png"
                    mime.contains("webp") -> "webp"
                    else                  -> "jpg"
                }
                val rb = bytes.toRequestBody(mime.toMediaTypeOrNull())
                val part = MultipartBody.Part.createFormData("file", "profile.$ext", rb)
                val updated = api.uploadMyPhoto(part).toDomain()
                EditState.Editing(updated)
            } catch (t: Throwable) {
                EditState.Error(t.message ?: "Error al subir foto")
            }
        }
    }
}
