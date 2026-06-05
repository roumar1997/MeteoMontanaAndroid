package com.meteomontana.android.ui.screens.admin
import com.meteomontana.android.util.toUserMessage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meteomontana.android.data.api.AdminApi
import com.meteomontana.android.data.api.SchoolApi
import com.meteomontana.android.data.api.dto.AdminLogDto
import com.meteomontana.android.data.api.dto.AdminPushRequest
import com.meteomontana.android.data.api.dto.AdminStatsDto
import com.meteomontana.android.data.api.dto.BlockDto
import com.meteomontana.android.data.api.dto.ContributionDto
import com.meteomontana.android.data.api.dto.CreateBlockRequest
import com.meteomontana.android.data.api.dto.RejectReason
import com.meteomontana.android.data.api.dto.SchoolDto
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
    val contributions: List<ContributionDto> = emptyList(),
    val logs: List<AdminLogDto> = emptyList(),
    val pushBusy: Boolean = false,
    val pushResult: String? = null,
    val schoolBlocks: Map<String, List<BlockDto>> = emptyMap(),
    val allSchools: List<SchoolDto> = emptyList(),
    val schoolsLoading: Boolean = false
)

@HiltViewModel
class AdminViewModel @Inject constructor(
    private val api: AdminApi,
    private val schoolApi: SchoolApi
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
                val contributions = runCatching { api.pendingContributions() }.getOrDefault(emptyList())
                val logs = runCatching { api.logs() }.getOrDefault(emptyList())
                _state.update { it.copy(loading = false, stats = stats, pending = pending,
                    contributions = contributions, logs = logs) }
            } catch (t: Throwable) {
                _state.update { it.copy(loading = false, error = t.toUserMessage()) }
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

    fun approveContribution(id: String) {
        viewModelScope.launch {
            runCatching { api.approveContribution(id) }
            load()
        }
    }

    fun rejectContribution(id: String, reason: String?) {
        viewModelScope.launch {
            runCatching { api.rejectContribution(id, RejectReason(reason)) }
            load()
        }
    }

    /** Carga los bloques de una escuela (una sola vez; se cachea en el estado). */
    fun fetchSchoolBlocks(schoolId: String) {
        if (_state.value.schoolBlocks.containsKey(schoolId)) return
        viewModelScope.launch {
            try {
                val blocks = schoolApi.getBlocks(schoolId)
                _state.update { it.copy(schoolBlocks = it.schoolBlocks + (schoolId to blocks)) }
            } catch (_: Throwable) {}
        }
    }

    /** Carga la lista completa de escuelas para el tab "GESTIONAR". */
    fun loadAllSchools() {
        if (_state.value.allSchools.isNotEmpty() || _state.value.schoolsLoading) return
        _state.update { it.copy(schoolsLoading = true) }
        viewModelScope.launch {
            try {
                val schools = schoolApi.getSchools()
                _state.update { it.copy(allSchools = schools, schoolsLoading = false) }
            } catch (_: Throwable) {
                _state.update { it.copy(schoolsLoading = false) }
            }
        }
    }

    /** Borra un bloque (admin). Refresca el cache local de bloques de esa escuela. */
    fun deleteBlock(blockId: String, schoolId: String) {
        viewModelScope.launch {
            runCatching { schoolApi.deleteBlock(blockId) }
            try {
                val blocks = schoolApi.getBlocks(schoolId)
                _state.update { it.copy(schoolBlocks = it.schoolBlocks + (schoolId to blocks)) }
            } catch (_: Throwable) {}
        }
    }

    /** Actualiza un bloque (admin): nombre, posición, descripción, líneas, etc. */
    fun updateBlock(
        blockId: String,
        schoolId: String,
        req: CreateBlockRequest,
        onDone: (Boolean) -> Unit = {}
    ) {
        viewModelScope.launch {
            val ok = runCatching { schoolApi.updateBlock(blockId, req) }.isSuccess
            try {
                val blocks = schoolApi.getBlocks(schoolId)
                _state.update { it.copy(schoolBlocks = it.schoolBlocks + (schoolId to blocks)) }
            } catch (_: Throwable) {}
            onDone(ok)
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
