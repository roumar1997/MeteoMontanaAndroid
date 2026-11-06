package com.meteomontana.android.domain.repository

import com.meteomontana.android.domain.model.FeatureFlags
import kotlinx.coroutines.flow.StateFlow

/** Interruptores de funciones del servidor, con copia local para que funcionen sin red. */
interface FeatureFlagRepository {
    val flags: StateFlow<FeatureFlags>

    /** Pregunta al servidor (como mucho cada `refreshAfterSeconds`). Si falla, conserva lo último. */
    suspend fun refresh()
}
