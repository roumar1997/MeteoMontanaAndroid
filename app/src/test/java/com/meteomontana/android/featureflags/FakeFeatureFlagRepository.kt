package com.meteomontana.android.featureflags

import com.meteomontana.android.domain.model.FeatureFlagState
import com.meteomontana.android.domain.model.FeatureFlags
import com.meteomontana.android.domain.repository.FeatureFlagRepository
import com.meteomontana.android.domain.usecase.featureflags.ObserveFeatureFlagsUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Repositorio falso de interruptores: por defecto todo encendido; [turnOff] apaga una clave. */
class FakeFeatureFlagRepository : FeatureFlagRepository {
    private val state = MutableStateFlow(FeatureFlags.ALL_ON)
    override val flags: StateFlow<FeatureFlags> = state

    fun turnOff(key: String, message: String? = null) {
        state.value = FeatureFlags(state.value.flags + (key to FeatureFlagState(false, message)))
    }

    override suspend fun refresh() = Unit

    fun observe() = ObserveFeatureFlagsUseCase(this)
}
