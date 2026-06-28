package com.meteomontana.android.ui.screens.meetups

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meteomontana.android.domain.util.Geo
import com.meteomontana.android.domain.model.Meetup
import com.meteomontana.android.domain.model.CreateMeetupRequest
import com.meteomontana.android.domain.usecase.meetups.CreateMeetupUseCase
import com.meteomontana.android.domain.usecase.meetups.DeleteMeetupUseCase
import com.meteomontana.android.domain.usecase.meetups.GetMeetupUseCase
import com.meteomontana.android.domain.usecase.meetups.GetMeetupsUseCase
import com.meteomontana.android.domain.usecase.meetups.JoinMeetupUseCase
import com.meteomontana.android.domain.model.School
import com.meteomontana.android.domain.usecase.meetups.GetMeetupAlertUseCase
import com.meteomontana.android.domain.usecase.meetups.KickMeetupMemberUseCase
import com.meteomontana.android.domain.usecase.meetups.MeetupAlertState
import com.meteomontana.android.domain.usecase.meetups.ReportMeetupUseCase
import com.meteomontana.android.domain.usecase.meetups.SetMeetupAlertUseCase
import com.meteomontana.android.domain.usecase.meetups.LeaveMeetupUseCase
import com.meteomontana.android.domain.usecase.meetups.UpdateMeetupUseCase
import com.meteomontana.android.domain.usecase.schools.GetRangeScoresUseCase
import com.meteomontana.android.domain.usecase.schools.SearchSchoolsUseCase
import com.meteomontana.android.domain.usecase.profile.GetMyProfileUseCase
import android.content.Context
import android.content.SharedPreferences
import com.meteomontana.android.domain.port.LocationProvider
import com.meteomontana.android.domain.port.PhotoUploader
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
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
    val filterSchoolName: String? = null,
    val filterDate: String? = null,
    val filterRelation: String? = null,   // "following" | null (all)
    val filterPrivacy: String? = null,    // "OPEN" | "FOLLOWERS" | "WOMEN" | null
    val maxDistanceKm: Int? = null,       // null = sin límite de distancia
    val filterDays: Set<String> = emptySet(),  // ISO dates, empty = any day
    val filterDiscipline: String? = null  // BOULDER | ROUTE | BOTH | null

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
    private val deleteMeetupUseCase: DeleteMeetupUseCase,
    private val updateMeetup: UpdateMeetupUseCase,
    private val kickMeetupMember: KickMeetupMemberUseCase,
    private val reportMeetup: ReportMeetupUseCase,
    private val getMeetupAlert: GetMeetupAlertUseCase,
    private val setMeetupAlert: SetMeetupAlertUseCase,
    private val searchSchoolsUseCase: SearchSchoolsUseCase,
    private val getRangeScores: GetRangeScoresUseCase,
    private val getMyProfile: GetMyProfileUseCase,
    private val locationProvider: LocationProvider,
    private val photoUploader: PhotoUploader,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

    private val prefs: SharedPreferences =
        appContext.getSharedPreferences("meetup_filters", Context.MODE_PRIVATE)

    private val _list = MutableStateFlow(loadSavedFilters())
    private val _userLat = MutableStateFlow<Double?>(null)
    private val _userLon = MutableStateFlow<Double?>(null)
    val userLat = _userLat.asStateFlow()
    val userLon = _userLon.asStateFlow()
    val listState = _list.asStateFlow()

    private val _detail = MutableStateFlow(MeetupDetailUiState())
    val detailState = _detail.asStateFlow()

    private val _createError = MutableStateFlow<String?>(null)
    val createError = _createError.asStateFlow()

    private val _myGender = MutableStateFlow<String?>(null)
    val myGender = _myGender.asStateFlow()

    init {
        loadMeetups(relation = _list.value.filterRelation)
        viewModelScope.launch {
            try { _myGender.value = getMyProfile().gender }
            catch (_: Exception) {}
        }
        viewModelScope.launch {
            try {
                val loc = locationProvider.current()
                if (loc != null) { _userLat.value = loc.lat; _userLon.value = loc.lon }
            } catch (_: Exception) {}
        }
    }

    fun loadMeetups(
        schoolId: String? = _list.value.filterSchoolId,
        schoolName: String? = _list.value.filterSchoolName,
        date: String? = _list.value.filterDate,
        relation: String? = _list.value.filterRelation
    ) {
        _list.update { it.copy(isLoading = true, error = null, filterSchoolId = schoolId,
            filterSchoolName = schoolName, filterDate = date, filterRelation = relation) }
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

    private val _savingDescription = MutableStateFlow(false)
    val savingDescription = _savingDescription.asStateFlow()

    /** Editar la descripción (solo el organizador). Actualiza el detalle al volver. */
    fun updateDescription(meetupId: String, description: String?) {
        _savingDescription.value = true
        viewModelScope.launch {
            try {
                val updated = updateMeetup.execute(meetupId, description)
                _detail.update { it.copy(meetup = updated) }
                _list.update { s ->
                    s.copy(meetups = s.meetups.map { m -> if (m.id == meetupId) updated else m })
                }
            } catch (e: Exception) {
                _detail.update { it.copy(error = e.message) }
            } finally {
                _savingDescription.value = false
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

    fun deleteMeetup(meetupId: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            try {
                deleteMeetupUseCase.execute(meetupId)
                _list.update { s -> s.copy(meetups = s.meetups.filter { it.id != meetupId }) }
                onSuccess()
            } catch (e: Exception) {
                _detail.update { it.copy(error = e.message) }
            }
        }
    }

    fun create(req: CreateMeetupRequest, onSuccess: (Meetup) -> Unit, onError: () -> Unit = {}) {
        _createError.value = null
        viewModelScope.launch {
            try {
                val meetup = createMeetup.execute(req)
                loadMeetups()
                onSuccess(meetup)
            } catch (e: Exception) {
                _createError.value = when {
                    e.message?.contains("GENDER_REQUIRED") == true ->
                        "Para crear o unirte a quedadas No Mixto necesitas indicar tu género " +
                        "como Mujer en tu perfil. Ve a Perfil → Editar perfil → Género."
                    else -> e.message ?: "Error al crear la quedada"
                }
                onError()
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

    fun saveAlert(enabled: Boolean, daysCsv: String?, schoolId: String?,
                  privacy: String?, discipline: String?, radiusKm: Int?) {
        viewModelScope.launch {
            try { _alertState.value = setMeetupAlert.execute(enabled, daysCsv) }
            catch (e: Exception) { _list.update { it.copy(error = e.message) } }
        }
    }

    private val _schoolResults = MutableStateFlow<List<School>>(emptyList())
    val schoolResults = _schoolResults.asStateFlow()

    fun searchSchools(query: String) {
        if (query.length < 2) { _schoolResults.value = emptyList(); return }
        viewModelScope.launch {
            try { _schoolResults.value = searchSchoolsUseCase(query, 8) }
            catch (_: Exception) {}
        }
    }

    fun clearSchoolSearch() { _schoolResults.value = emptyList() }

    private val _uploadingPhoto = MutableStateFlow(false)
    val uploadingPhoto = _uploadingPhoto.asStateFlow()

    /** Sube una foto para la quedada (temporal: usa "new" como ID; el backend usará la URL tal cual). */
    fun uploadMeetupPhoto(bytes: ByteArray, mimeType: String, onResult: (String?) -> Unit) {
        _uploadingPhoto.value = true
        viewModelScope.launch {
            try {
                val url = photoUploader.uploadMeetupPhoto(bytes, mimeType, "new_${System.currentTimeMillis()}")
                onResult(url)
            } catch (_: Exception) {
                onResult(null)
            } finally {
                _uploadingPhoto.value = false
            }
        }
    }

    fun clearError() {
        _list.update { it.copy(error = null) }
        _detail.update { it.copy(error = null) }
        _createError.value = null
    }

    fun setFilterRelation(relation: String?) {
        loadMeetups(relation = relation)
        saveFilters()
    }

    fun setFilterDate(date: String?) {
        loadMeetups(date = date)
    }

    fun setFilterSchool(schoolId: String?, schoolName: String?) {
        loadMeetups(schoolId = schoolId, schoolName = schoolName)
    }

    fun setFilterPrivacy(privacy: String?) {
        _list.update { it.copy(filterPrivacy = privacy) }
        saveFilters()
    }

    fun setMaxDistance(km: Int?) {
        _list.update { it.copy(maxDistanceKm = km) }
        saveFilters()
    }

    fun toggleFilterDay(day: String) {
        _list.update { s ->
            val new = if (s.filterDays.contains(day)) s.filterDays - day else s.filterDays + day
            s.copy(filterDays = new)
        }
        // Los días NO se persisten (cambian cada día)
    }

    fun clearFilterDays() {
        _list.update { it.copy(filterDays = emptySet()) }
    }

    fun setFilterDiscipline(discipline: String?) {
        _list.update { it.copy(filterDiscipline = discipline) }
        saveFilters()
    }

    fun getUserLat(): Double? = _userLat.value
    fun getUserLon(): Double? = _userLon.value

    fun distanceToMeetup(meetup: Meetup): Double? {
        val uLat = _userLat.value ?: return null
        val uLon = _userLon.value ?: return null
        val sLat = meetup.schoolLat ?: return null
        val sLon = meetup.schoolLon ?: return null
        return Geo.haversineKm(uLat, uLon, sLat, sLon)
    }

    // ── Scores de días para el formulario de crear quedada ─────────────────
    private val _dayScores = MutableStateFlow<Map<String, Int>>(emptyMap())
    val dayScores = _dayScores.asStateFlow()

    fun loadMeetupDayScores(schoolId: String, days: Collection<String>) {
        if (schoolId.isBlank() || days.isEmpty()) { _dayScores.value = emptyMap(); return }
        viewModelScope.launch {
            try {
                val results = getRangeScores(listOf(schoolId), days.toList())
                val school = results.firstOrNull() ?: return@launch
                _dayScores.value = school.days.associate { it.date to it.score }
            } catch (_: Exception) {}
        }
    }

    fun loadAllDayScores(schoolIds: List<String>, days: List<String>) {
        if (schoolIds.isEmpty() || days.isEmpty()) { _dayScores.value = emptyMap(); return }
        viewModelScope.launch {
            try {
                val results = getRangeScores(schoolIds, days)
                val map = mutableMapOf<String, Int>()
                results.forEach { school ->
                    school.days.forEach { day ->
                        map["${school.id}_${day.date}"] = day.score
                    }
                }
                _dayScores.value = map
            } catch (_: Exception) {}
        }
    }

    fun clearDayScores() { _dayScores.value = emptyMap() }

    // ── Persistir filtros ────────────────────────────────────────────────────

    private fun saveFilters() {
        val s = _list.value
        prefs.edit()
            .putString("relation", s.filterRelation)
            .putString("privacy", s.filterPrivacy)
            .putInt("maxDistanceKm", s.maxDistanceKm ?: -1)
            .putString("discipline", s.filterDiscipline)
            .apply()
    }

    private fun loadSavedFilters(): MeetupsUiState {
        val relation = prefs.getString("relation", null)
        val privacy = prefs.getString("privacy", null)
        val distRaw = prefs.getInt("maxDistanceKm", -1)
        val dist = if (distRaw > 0) distRaw else null
        val discipline = prefs.getString("discipline", null)
        return MeetupsUiState(
            filterRelation = relation,
            filterPrivacy = privacy,
            maxDistanceKm = dist,
            filterDiscipline = discipline
        )
    }
}
