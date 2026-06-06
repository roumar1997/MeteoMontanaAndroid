package com.meteomontana.android.ui.screens.submissions
import com.meteomontana.android.util.toUserMessage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meteomontana.android.domain.model.Submission
import com.meteomontana.android.domain.usecase.submissions.GetMySubmissionsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface MySubmissionsUiState {
    data object Loading : MySubmissionsUiState
    data class Success(val items: List<Submission>) : MySubmissionsUiState
    data class Error(val message: String) : MySubmissionsUiState
}

@HiltViewModel
class MySubmissionsViewModel @Inject constructor(
    private val getMySubmissions: GetMySubmissionsUseCase
) : ViewModel() {
    private val _state = MutableStateFlow<MySubmissionsUiState>(MySubmissionsUiState.Loading)
    val state: StateFlow<MySubmissionsUiState> = _state.asStateFlow()

    init { load() }

    fun load() {
        _state.value = MySubmissionsUiState.Loading
        viewModelScope.launch {
            _state.value = try {
                MySubmissionsUiState.Success(getMySubmissions())
            } catch (t: Throwable) {
                MySubmissionsUiState.Error(t.toUserMessage())
            }
        }
    }
}
