package com.meteomontana.android.domain.repository

import com.meteomontana.android.domain.model.TitleEffectCatalog
import kotlinx.coroutines.flow.StateFlow

/** Catálogo de efectos de título del servidor, con copia en disco (se enseña lo guardado y se refresca detrás). */
interface TitleEffectRepository {
    val catalog: StateFlow<TitleEffectCatalog>

    /** Pregunta al servidor si toca (como mucho cada `refreshAfterSeconds`). Si falla, conserva lo último. */
    suspend fun refresh()
}
