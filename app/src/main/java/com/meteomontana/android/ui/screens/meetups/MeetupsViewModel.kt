package com.meteomontana.android.ui.screens.meetups

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meteomontana.android.domain.model.Meetup
import com.meteomontana.android.domain.model.CreateMeetupRequest
import com.meteomontana.android.domain.usecase.meetups.CreateMeetupUseCase
import com.meteomontana.android.domain.usecase.meetups.GetMeetupUseCase
import com.meteomontana.android.domain.usecase.meetups.GetMeetupsUseCase
import com.meteomontana.android.domain.usecase.meetups.JoinMeetupUseCase
import com.meteomontana.android.domain.usecase.meetups.GetMeetupAlertUseCase
import com.meteomontana.android.domain.usecase.meetups.KickMeetupMemberUseCase
import com.meteomontana.android.domain.usecase.meetups.MeetupAlertState
import com.meteomontana.android.domain.usecase.meetups.ReportMeetupUseCase
import com.meteomontana.android.domain.usecase.meetups.SetMeetupAlertUseCase
import com.meteomontana.android.domain.usecase.meetups.LeaveMeetupUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MeetupsUiState(
    val meetups: List<Meetup> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val filterSchoolId: String? = null,
    val filterDate: String? = null,
    val filterRelation: String? = null    // "following" | null (all)
)

data class MeetupDetailUiState(
    val meetup: Meetup? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val joining: Boolean = false,
    val leaving: Boolean = false
)

@HiltViewModel
class MeetupsViewModel @Inject constructor(
    private val getMeetups: GetMeetupsUseCase,
    private val getMeetup: GetMeetupUseCase,
    private val createMeetup: CreateMeetupUseCase,
    private val joinMeetup: JoinMeetupUseCase,
    private val leaveMeetup: LeaveMeetupUseCase,
    private val kickMeetupMember: KickMeetupMemberUseCase,
    private val reportMeetup: ReportMeetupUseCase,
    private val getMeetupAlert: GetMeetupAlertUseCase,
    private val setMeetupAlert: SetMeetupAlertUseCase,
) : ViewModel() {

    private val _list = MutableStateFlow(MeetupsUiState())
    val listState = _list.asStateFlow()

    private val _detail = MutableStateFlow(MeetupDetailUiState())
    val detailState = _detail.asStateFlow()

    private val _createError = MutableStateFlow<String?>(null)
    val createError = _createError.asStateFlow()

    init {
        loadMeetups()
    }

    fun loadMeetups(
        schoolId: String? = _list.value.filterSchoolId,
        date: String? = _list.value.filterDate,
        relation: String? = _list.value.filterRelation
    ) {
        _list.update { it.copy(isLoading = true, error = null, filterSchoolId = schoolId, filterDate = date, filterRelation = relation) }
        viewModelScope.launch {
            try {
                val result = getMeetups.execute(schoolId, date, relation)
                _list.update { it.copy(meetups = result, isLoading = false) }
            } catch (e: Exception) {
                _list.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun loadMeetup(id: String) {
        _detail.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            try {
                val result = getMeetup.execute(id)
                _detail.update { it.copy(meetup = result, isLoading = false) }
            } catch (e: Exception) {
                _detail.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun join(id: String) {
        _detail.update { it.copy(joining = true) }
        viewModelScope.launch {
            try {
                val updated = joinMeetup.execute(id)
                _detail.update { it.copy(meetup = updated, joining = false) }
                // Actualizar también en la lista
                _list.update { s ->
                    s.copy(meetups = s.meetups.map { m -> if (m.id == id) updated else m })
                }
            } catch (e: Exception) {
                _detail.update { it.copy(joining = false, error = e.message) }
            }
        }
    }

    fun leave(id: String) {
        _detail.update { it.copy(leaving = true) }
        viewModelScope.launch {
            try {
                leaveMeetup.execute(id)
                // Actualizar estado joined en local (optimista)
                val updated = _detail.value.meetup?.copy(
                    joined = false,
                    memberCount = maxOf(0, (_detail.value.meetup?.memberCount ?: 1) - 1)
                )
                _detail.update { it.copy(meetup = updated, leaving = false) }
                _list.update { s ->
                    s.copy(meetups = s.meetups.map { m ->
                        if (m.id == id) m.copy(joined = false, memberCount = maxOf(0, m.memberCount - 1)) else m
                    })
                }
            } catch (e: Exception) {
                _detail.update { it.copy(leaving = false, error = e.message) }
            }
        }
    }

    fun kick(meetupId: String, targetUid: String) {
        viewModelScope.launch {
            try {
                kickMeetupMember.execute(meetupId, targetUid)
                // Refrescar detalle
                loadMeetup(meetupId)
            } catch (e: Exception) {
                _detail.update { it.copy(error = e.message) }
            }
        }
    }

    fun create(req: CreateMeetupRequest, onSuccess: (Meetup) -> Unit) {
        _createError.value = null
        viewModelScope.launch {
            try {
                val meetup = createMeetup.execute(req)
                loadMeetups()
                onSuccess(meetup)
            } catch (e: Exception) {
                _createError.value = when {
                    e.message?.contains("GENDER_REQUIRED") == true ->
                        "Solo puedes crear quedadas NO MIXTO si tienes género Mujer en tu perfil."
                    else -> e.message ?: "Error al crear la quedada"
                }
            }
        }
    }

    fun report(meetupId: String, reportedUid: String?, reason: String, context: String?,
               onSuccess: () -> Unit) {
        viewModelScope.launch {
            try {
                reportMeetup.execute(meetupId, reportedUid, reason, context)
                onSuccess()
            } catch (e: Exception) {
                _detail.update { it.copy(error = e.message) }
            }
        }
    }

    // ── Alerta de quedada nueva ──────────────────────────────────────────────

    private val _alertState = MutableStateFlow<MeetupAlertState?>(null)
    val alertState = _alertState.asStateFlow()

    fun loadAlertState() {
        viewModelScope.launch {
            try { _alertState.value = getMeetupAlert.execute() }
            catch (_: Exception) {}
        }
    }

    fun toggleAlert(enabled: Boolean, daysCsv: String? = null) {
        viewModelScope.launch {
            try { _alertState.value = setMeetupAlert.execute(enabled, daysCsv) }
            catch (e: Exception) { _list.update { it.copy(error = e.message) } }
        }
    }

    fun clearError() {
        _list.update { it.copy(error = null) }
        _detail.update { it.copy(error = null) }
        _createError.value = null
    }

    fun setFilterRelation(relation: String?) {
        loadMeetups(relation = relation)
    }

    fun setFilterDate(date: String?) {
        loadMeetups(date = date)
    }
}
