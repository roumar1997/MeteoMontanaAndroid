package com.meteomontana.android.ui.screens.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meteomontana.android.data.api.AdminApi
import com.meteomontana.android.data.api.dto.AdminLogDto
import com.meteomontana.android.data.api.dto.AdminPushRequest
import com.meteomontana.android.data.api.dto.AdminStatsDto
import com.meteomontana.android.data.api.dto.RejectReason
import com.meteomontana.android.data.api.dto.SubmissionDto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AdminUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val stats: AdminStatsDto? = null,
    val pending: List<SubmissionDto> = emptyList(),
    val logs: List<AdminLogDto> = emptyList(),
    val pushBusy: Boolean = false,
    val pushResult: String? = null
)

@HiltViewModel
class AdminViewModel @Inject constructor(
    private val api: AdminApi
) : ViewModel() {
    private val _state = MutableStateFlow(AdminUiState())
    val state: StateFlow<AdminUiState> = _state.asStateFlow()

    init { load() }

    fun load() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                val stats = api.stats()
                val pending = runCatching { api.pendingSubmissions() }.getOrDefault(emptyList())
                val logs = runCatching { api.logs() }.getOrDefault(emptyList())
                _state.update { it.copy(loading = false, stats = stats, pending = pending, logs = logs) }
            } catch (t: Throwable) {
                _state.update { it.copy(loading = false, error = t.message ?: "Error") }
            }
        }
    }

    fun approve(id: String) {
        viewModelScope.launch {
            runCatching { api.approve(id) }
            load()
        }
    }

    fun reject(id: String, reason: String?) {
        viewModelScope.launch {
            runCatching { api.reject(id, RejectReason(reason)) }
            load()
        }
    }

    fun sendPush(targetUid: String?, title: String, body: String) {
        _state.update { it.copy(pushBusy = true, pushResult = null) }
        viewModelScope.launch {
            try {
                val r = api.sendPush(AdminPushRequest(targetUid, title, body))
                _state.update { it.copy(pushBusy = false, pushResult = "Enviado a ${r.sent}/${r.recipients}") }
            } catch (t: Throwable) {
                _state.update { it.copy(pushBusy = false, pushResult = "Error: ${t.message}") }
            }
        }
    }
}
