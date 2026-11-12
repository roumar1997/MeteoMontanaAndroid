package com.meteomontana.android.domain.usecase.titleeffects

import com.meteomontana.android.domain.model.TitleEffectCatalog
import com.meteomontana.android.domain.repository.TitleEffectRepository
import kotlinx.coroutines.flow.StateFlow

/** Catálogo de efectos en vivo (Android lo observa; iOS lee [current]). Nunca espera a la red. */
class ObserveTitleEffectsUseCase(private val repository: TitleEffectRepository) {
    operator fun invoke(): StateFlow<TitleEffectCatalog> = repository.catalog

    /** Lectura puntual para Swift. */
    fun current(): TitleEffectCatalog = repository.catalog.value
}

/** Renueva el catálogo detrás (con límite de frecuencia; si falla, no pasa nada). */
class RefreshTitleEffectsUseCase(private val repository: TitleEffectRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke() { runCatching { repository.refresh() } }
}
