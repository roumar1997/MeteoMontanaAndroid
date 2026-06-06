package com.meteomontana.android.ui.screens.profile
import com.meteomontana.android.util.toUserMessage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meteomontana.android.data.api.dto.UpdateProfileRequest
import com.meteomontana.android.domain.model.PrivateProfile
import com.meteomontana.android.domain.usecase.profile.GetMyProfileUseCase
import com.meteomontana.android.domain.usecase.profile.UpdateMyProfileUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
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
    private val getMyProfile: GetMyProfileUseCase,
    private val updateMyProfile: UpdateMyProfileUseCase
) : ViewModel() {
    private val _state = MutableStateFlow<EditState>(EditState.Loading)
    val state: StateFlow<EditState> = _state.asStateFlow()

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

    // TODO(Fase 2.x): foto de perfil via Ktor multipart o PhotoUploader
}
