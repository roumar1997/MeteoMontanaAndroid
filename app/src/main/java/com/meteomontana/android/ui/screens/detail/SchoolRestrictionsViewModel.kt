package com.meteomontana.android.ui.screens.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meteomontana.android.domain.model.SchoolRestrictions
import com.meteomontana.android.domain.usecase.restriction.GetSchoolActiveRestrictionsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Restricciones en vigor de la escuela abierta. Si falla la red no se pinta nada (mejor sin "!" que con un error en
 * medio de la ficha); el siguiente intento vuelve a pedirlas.
 */
@HiltViewModel
class SchoolRestrictionsViewModel @Inject constructor(
    private val getRestrictions: GetSchoolActiveRestrictionsUseCase
) : ViewModel() {

    private val _state = MutableStateFlow<SchoolRestrictions?>(null)
    val state: StateFlow<SchoolRestrictions?> = _state.asStateFlow()

    private var loadedFor: String? = null

    fun load(schoolId: String) {
        if (loadedFor == schoolId) return
        loadedFor = schoolId
        viewModelScope.launch {
            _state.value = runCatching { getRestrictions(schoolId) }
                .onFailure { loadedFor = null }
                .getOrNull()
        }
    }
}
