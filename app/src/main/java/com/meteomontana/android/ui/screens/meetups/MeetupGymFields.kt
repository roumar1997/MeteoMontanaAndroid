package com.meteomontana.android.ui.screens.meetups

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meteomontana.android.domain.model.gym.Gym
import com.meteomontana.android.domain.model.gym.GymFilters
import com.meteomontana.android.domain.model.gym.GymListItem
import com.meteomontana.android.domain.model.gym.GymOrigin
import com.meteomontana.android.domain.usecase.gym.FilterGymsUseCase
import com.meteomontana.android.domain.usecase.gym.GetCachedGymsUseCase
import com.meteomontana.android.domain.usecase.gym.GetGymsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

// Buscador de rocódromos del paso 1 de «Nueva quedada» (copia local al instante, luego la red).

private const val MAX_RESULTS = 8

@HiltViewModel
class MeetupGymPickerViewModel @Inject constructor(
    getCached: GetCachedGymsUseCase,
    getGyms: GetGymsUseCase,
    getFavorites: com.meteomontana.android.domain.usecase.gym.GetGymFavoritesUseCase,
    private val filterGyms: FilterGymsUseCase
) : ViewModel() {
    private val all = MutableStateFlow<List<Gym>>(getCached())
    private val query = MutableStateFlow("")
    private val origin = MutableStateFlow<GymOrigin?>(null)
    private val favorites = MutableStateFlow<Set<Long>>(emptySet())

    /** Con la ubicación y sin texto salen «cerca de ti» (los más cercanos); con texto, los que coinciden (favoritos primero). */
    val results: StateFlow<List<GymListItem>> = combine(all, query, origin, favorites) { gyms, q, o, favs ->
        filterGyms(gyms, GymFilters(query = q), o, favs).take(MAX_RESULTS)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch { runCatching { getGyms.execute() }.onSuccess { all.value = it } }
        viewModelScope.launch { runCatching { getFavorites.execute() }.onSuccess { favorites.value = it } }
    }

    fun search(q: String) { query.value = q }

    fun setOrigin(lat: Double?, lon: Double?) { origin.value = if (lat != null && lon != null) GymOrigin(lat, lon) else null }
}
