package com.meteomontana.android.ui.screens.submissions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meteomontana.android.data.api.SchoolApi
import com.meteomontana.android.data.api.dto.SubmissionDto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface MySubmissionsUiState {
    data object Loading : MySubmissionsUiState
    data class Success(val items: List<SubmissionDto>) : MySubmissionsUiState
    data class Error(val message: String) : MySubmissionsUiState
}

@HiltViewModel
class MySubmissionsViewModel @Inject constructor(
    private val api: SchoolApi
) : ViewModel() {
    private val _state = MutableStateFlow<MySubmissionsUiState>(MySubmissionsUiState.Loading)
    val state: StateFlow<MySubmissionsUiState> = _state.asStateFlow()

    init { load() }

    fun load() {
        _state.value = MySubmissionsUiState.Loading
        viewModelScope.launch {
            _state.value = try {
                MySubmissionsUiState.Success(api.getMySubmissions())
            } catch (t: Throwable) {
                MySubmissionsUiState.Error(t.message ?: "Error")
            }
        }
    }
}
