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
        val following: Long = 0,
        /** true cuando los datos vienen de la caché local (sin conexión). */
        val offline: Boolean = false,
        /** Propuestas + contribuciones pendientes de revisar (solo admin). */
        val pendingReview: Int = 0
    ) : ProfileUiState
    data class Error(val message: String) : ProfileUiState
}

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val getMyProfile: GetMyProfileUseCase,
    private val getMyJournalStats: GetMyJournalStatsUseCase,
    private val createJournalEntry: CreateJournalEntryUseCase,
    private val getPendingSubmissions: com.meteomontana.android.domain.usecase.admin.GetPendingSubmissionsUseCase,
    private val getPendingContributions: com.meteomontana.android.domain.usecase.admin.GetPendingContributionsUseCase,
    private val getFollowStatus: com.meteomontana.android.domain.usecase.social.GetFollowStatusUseCase,
    private val updateMyProfile: com.meteomontana.android.domain.usecase.profile.UpdateMyProfileUseCase,
    private val profileCache: com.meteomontana.android.data.local.ProfileCache,
    private val deleteMyAccount: com.meteomontana.android.domain.usecase.profile.DeleteMyAccountUseCase,
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
                    val stats = statsDeferred.await()
                    val follow = runCatching { getFollowStatus(profile.uid) }
                        .getOrDefault(com.meteomontana.android.domain.model.FollowStatus(0, 0, false, false))
                    // Si soy admin, cuento las propuestas/contribuciones pendientes
                    // para marcar el botón de admin con un aviso (en paralelo).
                    val pending = if (profile.isAdmin) {
                        val subs = async { runCatching { getPendingSubmissions().size }.getOrDefault(0) }
                        val contribs = async { runCatching { getPendingContributions().size }.getOrDefault(0) }
                        subs.await() + contribs.await()
                    } else 0
                    // Cachea para poder ver el perfil SIN conexión la próxima vez.
                    runCatching { profileCache.save(profile, stats, follow.followers, follow.following) }
                    ProfileUiState.Success(withPhotoFallback(profile), stats, follow.followers, follow.following, pendingReview = pending)
                }
            } catch (t: Throwable) {
                // Sin conexión: cae a la última copia cacheada (si existe). Si nunca
                // se cacheó (p.ej. primera vez tras reinstalar), construimos un perfil
                // MÍNIMO desde Firebase Auth (disponible offline) en vez de dar error.
                val cached = profileCache.load()
                when {
                    cached != null -> ProfileUiState.Success(
                        withPhotoFallback(cached.profile), cached.stats, cached.followers, cached.following, offline = true
                    )
                    authManager.currentUid() != null -> ProfileUiState.Success(
                        PrivateProfile(
                            uid = authManager.currentUid()!!,
                            email = authManager.currentEmail(),
                            username = null,
                            displayName = authManager.currentDisplayName(),
                            photoUrl = authManager.currentPhotoUrl(),
                            bio = null, topGrade = null,
                            isPublic = true, isAdmin = false, isPremium = false
                        ),
                        JournalStats(0, 0, 0, 0, null, null, null, emptyList()),
                        offline = true
                    )
                    else -> ProfileUiState.Error(t.toUserMessage())
                }
            }
        }
    }

    /** Si el perfil del backend no tiene foto, usa la de la cuenta de Google
     *  (Firebase Auth) para que el avatar propio no salga vacío. */
    private fun withPhotoFallback(p: PrivateProfile): PrivateProfile =
        if (p.photoUrl.isNullOrBlank()) p.copy(photoUrl = authManager.currentPhotoUrl()) else p

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

    /** Borra la cuenta en el backend (datos + Firebase Auth) y cierra sesión.
     *  [onDone] se llama al terminar (éxito o no) para volver al login. */
    fun deleteAccount(onDone: () -> Unit = {}) {
        viewModelScope.launch {
            runCatching { deleteMyAccount() }
            runCatching { authManager.signOut() }
            onDone()
        }
    }
}
