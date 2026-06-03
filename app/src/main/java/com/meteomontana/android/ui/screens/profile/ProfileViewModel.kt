package com.meteomontana.android.ui.screens.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meteomontana.android.data.api.SchoolApi
import com.meteomontana.android.data.api.dto.CreateJournalRequest
import com.meteomontana.android.data.api.dto.JournalStatsDto
import com.meteomontana.android.data.api.dto.PrivateProfileDto
import com.meteomontana.android.data.auth.AuthManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface ProfileUiState {
    data object Loading : ProfileUiState
    data class Success(val profile: PrivateProfileDto, val stats: JournalStatsDto) : ProfileUiState
    data class Error(val message: String) : ProfileUiState
}

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val api: SchoolApi,
    private val authManager: AuthManager
) : ViewModel() {

    private val _uiState = MutableStateFlow<ProfileUiState>(ProfileUiState.Loading)
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init { load() }

    fun load() {
        _uiState.value = ProfileUiState.Loading
        viewModelScope.launch {
            _uiState.value = try {
                val profile = api.getMyProfile()
                val stats = api.getMyJournalStats()
                ProfileUiState.Success(profile, stats)
            } catch (t: Throwable) {
                ProfileUiState.Error(t.message ?: "Error")
            }
        }
    }

    fun addBlock(req: CreateJournalRequest, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            try {
                api.createJournalSession(req)
                load()
                onDone()
            } catch (_: Throwable) {}
        }
    }

    fun signOut() {
        viewModelScope.launch { authManager.signOut() }
    }
}
