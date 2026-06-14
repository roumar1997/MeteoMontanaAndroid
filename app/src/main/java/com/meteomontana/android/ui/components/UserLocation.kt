package com.meteomontana.android.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meteomontana.android.domain.port.LocationProvider
import com.meteomontana.android.domain.model.UserLocation
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Última ubicación conocida del usuario para pintar el punto azul en los mapas.
 * Null mientras carga o si no hay permiso.
 */
@HiltViewModel
class UserLocationViewModel @Inject constructor(
    private val locationProvider: LocationProvider
) : ViewModel() {
    private val _location = MutableStateFlow<UserLocation?>(null)
    val location: StateFlow<UserLocation?> = _location.asStateFlow()

    init {
        viewModelScope.launch {
            _location.value = runCatching { locationProvider.current() }.getOrNull()
        }
    }
}

@Composable
fun rememberUserLocation(): UserLocation? {
    val vm: UserLocationViewModel = hiltViewModel()
    val loc by vm.location.collectAsState()
    return loc
}
