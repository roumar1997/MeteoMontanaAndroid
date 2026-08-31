package com.meteomontana.android.ui.components

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meteomontana.android.BuildConfig
import com.meteomontana.android.domain.usecase.suggestion.SubmitSuggestionUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class SuggestionSendState { IDLE, SENDING, SENT, ERROR }

@HiltViewModel
class SuggestionViewModel @Inject constructor(
    private val submitSuggestion: SubmitSuggestionUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(SuggestionSendState.IDLE)
    val state: StateFlow<SuggestionSendState> = _state.asStateFlow()

    fun send(message: String) {
        if (message.isBlank()) return
        _state.value = SuggestionSendState.SENDING
        viewModelScope.launch {
            _state.value = runCatching {
                submitSuggestion(message.trim(), "ANDROID", BuildConfig.VERSION_NAME)
            }.fold(
                onSuccess = { SuggestionSendState.SENT },
                onFailure = { SuggestionSendState.ERROR }
            )
        }
    }

    fun reset() { _state.value = SuggestionSendState.IDLE }
}
