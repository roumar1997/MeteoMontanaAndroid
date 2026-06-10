package com.meteomontana.android.ui.screens.profile
import com.meteomontana.android.util.toUserMessage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meteomontana.android.data.api.dto.CreateJournalRequest
import com.meteomontana.android.data.auth.AuthManager
import com.meteomontana.android.domain.model.JournalStats
import com.meteomontana.android.domain.model.PrivateProfile
import com.meteomontana.android.domain.usecase.journal.CreateJournalEntryUseCase
import com.meteomontana.android.domain.usecase.journal.GetMyJournalStatsUseCase
import com.meteomontana.android.domain.usecase.profile.GetMyProfileUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface ProfileUiState {
    data object Loading : ProfileUiState
    data class Success(
        val profile: PrivateProfile,
        val stats: JournalStats,
        val followers: Long = 0,
        val following: Long = 0
    ) : ProfileUiState
    data class Error(val message: String) : ProfileUiState
}

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val getMyProfile: GetMyProfileUseCase,
    private val getMyJournalStats: GetMyJournalStatsUseCase,
    private val createJournalEntry: CreateJournalEntryUseCase,
    private val getFollowStatus: com.meteomontana.android.domain.usecase.social.GetFollowStatusUseCase,
    private val updateMyProfile: com.meteomontana.android.domain.usecase.profile.UpdateMyProfileUseCase,
    private val authManager: AuthManager
) : ViewModel() {

    private val _uiState = MutableStateFlow<ProfileUiState>(ProfileUiState.Loading)
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init { load() }

    fun load() {
        // Solo mostramos Loading si NO había datos previos. Así un refresh (ON_RESUME
        // al volver de Editar perfil) mantiene la pantalla pintada y no parpadea.
        if (_uiState.value !is ProfileUiState.Success) {
            _uiState.value = ProfileUiState.Loading
        }
        viewModelScope.launch {
            _uiState.value = try {
                // Perfil y stats no dependen entre sí: en paralelo ahorramos
                // un round-trip completo al backend (~350 ms en remoto).
                // coroutineScope confina el fallo de un async para que lo
                // capture el catch en vez de cancelar el ViewModel scope.
                coroutineScope {
                    val profileDeferred = async { getMyProfile() }
                    val statsDeferred = async { getMyJournalStats() }
                    val profile = profileDeferred.await()
                    val follow = runCatching { getFollowStatus(profile.uid) }
                        .getOrDefault(com.meteomontana.android.domain.model.FollowStatus(0, 0, false, false))
                    ProfileUiState.Success(profile, statsDeferred.await(), follow.followers, follow.following)
                }
            } catch (t: Throwable) {
                ProfileUiState.Error(t.toUserMessage())
            }
        }
    }

    fun addBlock(req: CreateJournalRequest, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            try {
                createJournalEntry(req)
                // Auto-actualiza el grado máximo del perfil si es mayor que el actual.
                val cur = _uiState.value as? ProfileUiState.Success
                val curTop = cur?.profile?.topGrade
                val newGrade = req.grade
                if (!newGrade.isNullOrBlank() && gradeRank(newGrade) > gradeRank(curTop)) {
                    runCatching {
                        updateMyProfile(com.meteomontana.android.data.api.dto.UpdateProfileRequest(topGrade = newGrade))
                    }
                }
                load()
                onDone()
            } catch (_: Throwable) {}
        }
    }

    /** Devuelve un entero comparable para ordenar grados ("6a"<"6b"<"6c"<"6a+"<"7a"…). */
    private fun gradeRank(g: String?): Int {
        if (g.isNullOrBlank()) return -1
        val s = g.lowercase().trim()
        val n = s.takeWhile { it.isDigit() }.toIntOrNull() ?: return -1
        val letter = s.firstOrNull { it.isLetter() }?.let { it - 'a' } ?: 0
        val plus = if (s.endsWith("+")) 1 else 0
        return n * 100 + letter * 10 + plus * 5
    }

    fun signOut() {
        viewModelScope.launch { authManager.signOut() }
    }
}
