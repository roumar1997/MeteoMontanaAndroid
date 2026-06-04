package com.meteomontana.android.ui.screens.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meteomontana.android.data.api.SchoolApi
import com.meteomontana.android.data.api.dto.PrivateProfileDto
import com.meteomontana.android.data.api.dto.UpdateProfileRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface EditState {
    data object Loading : EditState
    data class Editing(val profile: PrivateProfileDto) : EditState
    data object Saving : EditState
    data object Saved : EditState
    data class Error(val message: String) : EditState
}

@HiltViewModel
class EditProfileViewModel @Inject constructor(
    private val api: SchoolApi
) : ViewModel() {
    private val _state = MutableStateFlow<EditState>(EditState.Loading)
    val state: StateFlow<EditState> = _state.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.value = try {
                EditState.Editing(api.getMyProfile())
            } catch (t: Throwable) {
                EditState.Error(t.message ?: "Error")
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
                EditState.Error(t.message ?: "Error")
            }
        }
    }
}
