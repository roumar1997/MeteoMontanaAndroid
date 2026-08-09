package com.meteomontana.android.ui.screens.submissions
import com.meteomontana.android.util.toUserMessage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meteomontana.android.data.api.dto.SubmitSchoolRequest
import com.meteomontana.android.domain.model.School
import com.meteomontana.android.domain.usecase.schools.GetSchoolsUseCase
import com.meteomontana.android.domain.usecase.submissions.SubmitSchoolUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface SubmitState {
    data object Idle : SubmitState
    data object Submitting : SubmitState
    data object Done : SubmitState
    data class Error(val message: String) : SubmitState
}

/** Opciones de los desplegables (valores únicos del catálogo) para evitar erratas. */
data class CatalogOptions(
    val regions: List<String> = emptyList(),
    val styles: List<String> = emptyList(),
    val rockTypes: List<String> = emptyList()
)

@HiltViewModel
class SubmitSchoolViewModel @Inject constructor(
    private val submitSchool: SubmitSchoolUseCase,
    private val getSchools: GetSchoolsUseCase,
    private val geoApi: com.meteomontana.android.data.api.KtorGeoApi
) : ViewModel() {
    private val _state = MutableStateFlow<SubmitState>(SubmitState.Idle)
    val state: StateFlow<SubmitState> = _state.asStateFlow()

    private val _options = MutableStateFlow(CatalogOptions())
    val options: StateFlow<CatalogOptions> = _options.asStateFlow()

    private var schools: List<School> = emptyList()

    /** Países abiertos, con sus regiones. Del servidor: ver [KtorGeoApi]. */
    private val _countries = MutableStateFlow<List<com.meteomontana.android.data.api.CountryDto>>(emptyList())
    val countries: StateFlow<List<com.meteomontana.android.data.api.CountryDto>> = _countries.asStateFlow()

    init {
        viewModelScope.launch {
            schools = runCatching { getSchools() }.getOrDefault(emptyList())
            _options.value = CatalogOptions(
                regions = unique(schools.map { it.region }),
                styles = unique(schools.map { it.style }),
                rockTypes = unique(schools.map { it.rockType })
            )
        }
        viewModelScope.launch {
            // Sin red se cae a España: es lo que había antes del catálogo y
            // cubre el 100% de las escuelas de hoy.
            _countries.value = runCatching { geoApi.countries() }.getOrDefault(
                listOf(com.meteomontana.android.data.api.CountryDto(
                    code = "ES", name = "España",
                    regions = unique(schools.map { it.region })))
            )
        }
    }

    /**
     * Regiones de un país. Salen del catálogo del servidor, NO de las escuelas
     * existentes: si se dedujeran de ellas, el primer país que se abre tendría
     * el desplegable vacío y nadie podría proponer su primera escuela.
     */
    fun regionOptions(countryCode: String): List<String> =
        _countries.value.firstOrNull { it.code == countryCode }?.regions ?: emptyList()

    /** Localidades del catálogo filtradas por región (mismo criterio que iOS). */
    fun locationOptions(region: String): List<String> {
        val scope = if (region.isBlank()) schools
        else schools.filter { it.region?.equals(region, ignoreCase = true) == true }
        return unique(scope.map { it.location })
    }

    private fun unique(raw: List<String?>): List<String> =
        raw.filterNotNull().filter { it.isNotBlank() }.distinct().sorted()

    fun submit(req: SubmitSchoolRequest) {
        _state.value = SubmitState.Submitting
        viewModelScope.launch {
            _state.value = try {
                submitSchool(req)
                SubmitState.Done
            } catch (t: Throwable) {
                SubmitState.Error(t.toUserMessage())
            }
        }
    }
}
