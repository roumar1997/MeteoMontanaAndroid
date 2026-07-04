package com.meteomontana.android.ui.screens.admin
import com.meteomontana.android.util.toUserMessage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meteomontana.android.data.api.dto.CreateBlockRequest
import com.meteomontana.android.domain.model.AdminLog
import com.meteomontana.android.domain.model.AdminStats
import com.meteomontana.android.domain.model.Block
import com.meteomontana.android.domain.model.Contribution
import com.meteomontana.android.domain.model.MeetupReport
import com.meteomontana.android.domain.model.School
import com.meteomontana.android.domain.model.Submission
import com.meteomontana.android.domain.usecase.schools.GetSchoolsUseCase
import com.meteomontana.android.domain.usecase.admin.ApproveContributionUseCase
import com.meteomontana.android.domain.usecase.admin.ApproveSubmissionUseCase
import com.meteomontana.android.domain.usecase.admin.GetAdminLogsUseCase
import com.meteomontana.android.domain.usecase.admin.GetAdminStatsUseCase
import com.meteomontana.android.domain.usecase.admin.GetPendingContributionsUseCase
import com.meteomontana.android.domain.usecase.admin.GetPendingSubmissionsUseCase
import com.meteomontana.android.domain.usecase.admin.GetPendingReportsUseCase
import com.meteomontana.android.domain.usecase.admin.RejectContributionUseCase
import com.meteomontana.android.domain.usecase.admin.RejectSubmissionUseCase
import com.meteomontana.android.domain.usecase.admin.ResolveReportUseCase
import com.meteomontana.android.domain.usecase.admin.SendPushUseCase
import com.meteomontana.android.domain.usecase.blocks.DeleteBlockUseCase
import com.meteomontana.android.domain.usecase.blocks.GetBlocksUseCase
import com.meteomontana.android.domain.usecase.blocks.UpdateBlockUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AdminUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val stats: AdminStats? = null,
    val pending: List<Submission> = emptyList(),
    val contributions: List<Contribution> = emptyList(),
    val logs: List<AdminLog> = emptyList(),
    val pushBusy: Boolean = false,
    val pushResult: String? = null,
    val schoolBlocks: Map<String, List<Block>> = emptyMap(),
    val allSchools: List<School> = emptyList(),
    val schoolsLoading: Boolean = false,
    val reports: List<MeetupReport> = emptyList(),
    /** Denuncias de contenido (comentarios/notas/usuarios). */
    val contentReports: List<com.meteomontana.android.data.api.ContentReportDto> = emptyList()
)

@HiltViewModel
class AdminViewModel @Inject constructor(
    private val getStats: GetAdminStatsUseCase,
    private val getPendingSubmissions: GetPendingSubmissionsUseCase,
    private val getPendingContributions: GetPendingContributionsUseCase,
    private val getLogs: GetAdminLogsUseCase,
    private val approveSubmission: ApproveSubmissionUseCase,
    private val rejectSubmission: RejectSubmissionUseCase,
    private val approveContributionUseCase: ApproveContributionUseCase,
    private val rejectContributionUseCase: RejectContributionUseCase,
    private val sendPushUseCase: SendPushUseCase,
    private val getPendingReportsUseCase: GetPendingReportsUseCase,
    private val resolveReportUseCase: ResolveReportUseCase,
    private val moderationApi: com.meteomontana.android.data.api.KtorModerationApi,
    private val searchUsers: com.meteomontana.android.domain.usecase.social.SearchUsersUseCase,
    private val getBlocks: GetBlocksUseCase,
    private val updateBlockUseCase: UpdateBlockUseCase,
    private val deleteBlockUseCase: DeleteBlockUseCase,
    private val getSchoolsUseCase: GetSchoolsUseCase
) : ViewModel() {
    private val _state = MutableStateFlow(AdminUiState())
    val state: StateFlow<AdminUiState> = _state.asStateFlow()

    init { load() }

    fun load() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                // Las 4 llamadas son independientes: en paralelo, no en serie.
                coroutineScope {
                    val statsD = async { getStats() }
                    val pendingD = async { runCatching { getPendingSubmissions() }.getOrDefault(emptyList()) }
                    val contributionsD = async { runCatching { getPendingContributions() }.getOrDefault(emptyList()) }
                    val logsD = async { runCatching { getLogs() }.getOrDefault(emptyList()) }
                    val reportsD = async { runCatching { getPendingReportsUseCase() }.getOrDefault(emptyList()) }
                    val contentReportsD = async { runCatching { moderationApi.getContentReports() }.getOrDefault(emptyList()) }
                    _state.update { it.copy(loading = false, stats = statsD.await(), pending = pendingD.await(),
                        contributions = contributionsD.await(), logs = logsD.await(),
                        reports = reportsD.await(),
                        contentReports = contentReportsD.await()) }
                }
            } catch (t: Throwable) {
                _state.update { it.copy(loading = false, error = t.toUserMessage()) }
            }
        }
    }

    fun approve(id: String) {
        viewModelScope.launch {
            runCatching { approveSubmission(id) }
            load()
        }
    }

    fun reject(id: String, reason: String?) {
        viewModelScope.launch {
            runCatching { rejectSubmission(id, reason) }
            load()
        }
    }

    fun approveContribution(id: String) {
        viewModelScope.launch {
            runCatching { approveContributionUseCase(id) }
            load()
        }
    }

    fun rejectContribution(id: String, reason: String?) {
        viewModelScope.launch {
            runCatching { rejectContributionUseCase(id, reason) }
            load()
        }
    }

    fun fetchSchoolBlocks(schoolId: String) {
        if (_state.value.schoolBlocks.containsKey(schoolId)) return
        viewModelScope.launch {
            try {
                val blocks = getBlocks(schoolId)
                _state.update { it.copy(schoolBlocks = it.schoolBlocks + (schoolId to blocks)) }
            } catch (_: Throwable) {}
        }
    }

    fun loadAllSchools() {
        if (_state.value.allSchools.isNotEmpty() || _state.value.schoolsLoading) return
        _state.update { it.copy(schoolsLoading = true) }
        viewModelScope.launch {
            try {
                val schools = getSchoolsUseCase()
                _state.update { it.copy(allSchools = schools, schoolsLoading = false) }
            } catch (_: Throwable) {
                _state.update { it.copy(schoolsLoading = false) }
            }
        }
    }

    fun deleteBlock(blockId: String, schoolId: String) {
        viewModelScope.launch {
            runCatching { deleteBlockUseCase(blockId) }
            try {
                val blocks = getBlocks(schoolId)
                _state.update { it.copy(schoolBlocks = it.schoolBlocks + (schoolId to blocks)) }
            } catch (_: Throwable) {}
        }
    }

    fun updateBlock(
        blockId: String,
        schoolId: String,
        req: CreateBlockRequest,
        onDone: (Boolean) -> Unit = {}
    ) {
        viewModelScope.launch {
            val ok = runCatching { updateBlockUseCase(blockId, req) }.isSuccess
            try {
                val blocks = getBlocks(schoolId)
                _state.update { it.copy(schoolBlocks = it.schoolBlocks + (schoolId to blocks)) }
            } catch (_: Throwable) {}
            onDone(ok)
        }
    }

    fun resolveReport(id: String, action: String) {
        viewModelScope.launch {
            runCatching { resolveReportUseCase(id, action) }
            load()
        }
    }

    /** Buscador de destinatario del push (por @usuario o nombre). */
    private val _userResults = kotlinx.coroutines.flow.MutableStateFlow<List<com.meteomontana.android.domain.model.PublicProfile>>(emptyList())
    val userResults: kotlinx.coroutines.flow.StateFlow<List<com.meteomontana.android.domain.model.PublicProfile>> = _userResults
    private var searchJob: kotlinx.coroutines.Job? = null
    fun searchPushTarget(q: String) {
        searchJob?.cancel()
        if (q.trim().length < 2) { _userResults.value = emptyList(); return }
        searchJob = viewModelScope.launch {
            kotlinx.coroutines.delay(250)
            _userResults.value = runCatching { searchUsers(q.trim()) }.getOrDefault(emptyList())
        }
    }
    fun clearPushTargets() { _userResults.value = emptyList() }

    /** Listas de STATS (usuarios / notas), bajo demanda. */
    private val _adminUsers = kotlinx.coroutines.flow.MutableStateFlow<List<com.meteomontana.android.data.api.AdminUserRowDto>?>(null)
    val adminUsers: kotlinx.coroutines.flow.StateFlow<List<com.meteomontana.android.data.api.AdminUserRowDto>?> = _adminUsers
    private val _adminNotes = kotlinx.coroutines.flow.MutableStateFlow<List<com.meteomontana.android.data.api.AdminNoteRowDto>?>(null)
    val adminNotes: kotlinx.coroutines.flow.StateFlow<List<com.meteomontana.android.data.api.AdminNoteRowDto>?> = _adminNotes
    fun loadAdminUsers() {
        viewModelScope.launch {
            _adminUsers.value = runCatching { moderationApi.getAdminUsers() }.getOrDefault(emptyList())
        }
    }
    fun loadAdminNotes() {
        viewModelScope.launch {
            _adminNotes.value = runCatching { moderationApi.getAdminNotes() }.getOrDefault(emptyList())
        }
    }

    /** Denuncia de CONTENIDO: REMOVE (borra el contenido) / IGNORE. */
    fun resolveContentReport(id: String, action: String) {
        viewModelScope.launch {
            runCatching { moderationApi.resolveContentReport(id, action) }
            load()
        }
    }

    fun sendPush(targetUid: String?, title: String, body: String) {
        _state.update { it.copy(pushBusy = true, pushResult = null) }
        viewModelScope.launch {
            try {
                val r = sendPushUseCase(targetUid, title, body)
                _state.update { it.copy(pushBusy = false, pushResult = "Enviado a ${r.sent}/${r.recipients}") }
            } catch (t: Throwable) {
                _state.update { it.copy(pushBusy = false, pushResult = "Error: ${t.message}") }
            }
        }
    }
}
