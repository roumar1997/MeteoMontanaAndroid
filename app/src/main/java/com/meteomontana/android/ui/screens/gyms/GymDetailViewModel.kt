package com.meteomontana.android.ui.screens.gyms

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meteomontana.android.domain.model.SchoolPresence
import com.meteomontana.android.domain.model.gym.Gym
import com.meteomontana.android.domain.usecase.gym.ClearGymPresenceUseCase
import com.meteomontana.android.domain.usecase.gym.GetCachedGymsUseCase
import com.meteomontana.android.domain.usecase.gym.GetGymFavoritesUseCase
import com.meteomontana.android.domain.usecase.gym.GetGymPresenceUseCase
import com.meteomontana.android.domain.usecase.gym.GetGymUseCase
import com.meteomontana.android.domain.usecase.gym.MarkGymPresenceUseCase
import com.meteomontana.android.domain.usecase.gym.SetGymFavoriteUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class GymDetailState(
    val gym: Gym? = null,
    val loadFailed: Boolean = false,
    val favorite: Boolean = false,
    val people: List<SchoolPresence> = emptyList(),
    val iAmHere: Boolean = false,
    val presenceBusy: Boolean = false,
    val presenceError: Boolean = false
)

/** Ficha de un rocódromo: la ficha (de la copia local al instante), el favorito y «Quién va hoy». */
@HiltViewModel
class GymDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getCachedGyms: GetCachedGymsUseCase,
    private val getGym: GetGymUseCase,
    private val getFavorites: GetGymFavoritesUseCase,
    private val setFavorite: SetGymFavoriteUseCase,
    private val getPresence: GetGymPresenceUseCase,
    private val markPresence: MarkGymPresenceUseCase,
    private val clearPresence: ClearGymPresenceUseCase
) : ViewModel() {

    private val gymId: Long = savedStateHandle.get<String>("gymId")?.toLongOrNull() ?: -1L

    private val _state = MutableStateFlow(GymDetailState(gym = getCachedGyms().firstOrNull { it.id == gymId }))
    val state: StateFlow<GymDetailState> = _state

    init {
        viewModelScope.launch {
            // La copia local ya pinta la ficha; el servidor la refresca (precio, horario, verificación).
            runCatching { getGym.execute(gymId) }
                .onSuccess { g -> _state.update { it.copy(gym = g, loadFailed = false) } }
                .onFailure { _state.update { s -> s.copy(loadFailed = s.gym == null) } }
            runCatching { getFavorites.execute() }.onSuccess { ids -> _state.update { it.copy(favorite = gymId in ids) } }
        }
    }

    fun loadPresence(myUid: String?) {
        viewModelScope.launch {
            runCatching { getPresence.execute(gymId) }
                .onSuccess { list ->
                    _state.update {
                        it.copy(people = list, iAmHere = myUid != null && list.any { p -> p.uid == myUid }, presenceError = false)
                    }
                }
                .onFailure { _state.update { it.copy(presenceError = true) } }
        }
    }

    fun togglePresence(myUid: String?) {
        if (_state.value.presenceBusy) return
        val here = _state.value.iAmHere
        _state.update { it.copy(presenceBusy = true, presenceError = false) }
        viewModelScope.launch {
            val ok = runCatching { if (here) clearPresence.execute(gymId) else markPresence.execute(gymId) }.isSuccess
            _state.update { it.copy(presenceBusy = false, presenceError = !ok, iAmHere = if (ok) !here else here) }
            loadPresence(myUid)
        }
    }

    fun toggleFavorite() {
        val now = !_state.value.favorite
        _state.update { it.copy(favorite = now) }
        viewModelScope.launch {
            runCatching { setFavorite.execute(gymId, now) }
                .onFailure { _state.update { s -> s.copy(favorite = !now) } }
        }
    }
}
