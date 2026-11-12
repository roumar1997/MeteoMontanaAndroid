package com.meteomontana.android.ui.components

import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meteomontana.android.domain.port.LocationProvider
import com.meteomontana.android.domain.model.UserLocation
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Última ubicación conocida del usuario para pintar el punto azul en los mapas.
 * Null mientras carga o si no hay permiso.
 */
@HiltViewModel
class UserLocationViewModel @Inject constructor(
    private val locationProvider: LocationProvider
) : ViewModel() {
    /**
     * Refresco continuo SOLO mientras alguien lo mira con la pantalla visible: `collectAsStateWithLifecycle`
     * deja de recoger en segundo plano y `WhileSubscribed` para el bucle (antes seguía pidiendo GPS cada 5 s
     * aunque la app estuviera minimizada). Conserva el último punto para no parpadear al volver.
     */
    val location: StateFlow<UserLocation?> = flow {
        while (true) {
            runCatching { locationProvider.current() }.getOrNull()?.let { emit(it) }
            delay(5_000)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

@Composable
fun rememberUserLocation(): UserLocation? {
    val vm: UserLocationViewModel = hiltViewModel()
    val loc by vm.location.collectAsStateWithLifecycle()
    return loc
}
