package com.meteomontana.android.ui.screens.submissions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meteomontana.android.data.api.SchoolApi
import com.meteomontana.android.data.api.dto.SubmitSchoolRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface SubmitState {
    data object Idle : SubmitState
    data object Submitting : SubmitState
    data object Done : SubmitState
    data class Error(val message: String) : SubmitState
}

@HiltViewModel
class SubmitSchoolViewModel @Inject constructor(
    private val api: SchoolApi
) : ViewModel() {
    private val _state = MutableStateFlow<SubmitState>(SubmitState.Idle)
    val state: StateFlow<SubmitState> = _state.asStateFlow()

    fun submit(req: SubmitSchoolRequest) {
        _state.value = SubmitState.Submitting
        viewModelScope.launch {
            _state.value = try {
                api.submitSchool(req)
                SubmitState.Done
            } catch (t: Throwable) {
                SubmitState.Error(t.message ?: "Error")
            }
        }
    }
}
