package com.meteomontana.android.domain.usecase.featureflags

import com.meteomontana.android.domain.model.FeatureFlags
import com.meteomontana.android.domain.repository.FeatureFlagRepository
import kotlinx.coroutines.flow.StateFlow

/** Interruptores de funciones en vivo (Android lo observa; iOS lee [current]). */
class ObserveFeatureFlagsUseCase(private val repository: FeatureFlagRepository) {
    operator fun invoke(): StateFlow<FeatureFlags> = repository.flags

    /** Lectura puntual para Swift. */
    fun current(): FeatureFlags = repository.flags.value
}

/** Pide los interruptores al servidor (con límite de frecuencia; si falla, no pasa nada). */
class RefreshFeatureFlagsUseCase(private val repository: FeatureFlagRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke() = repository.refresh()
}
