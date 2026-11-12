package com.meteomontana.android.di

import com.meteomontana.android.data.api.KtorTitleEffectApi
import com.meteomontana.android.data.repository.TitleEffectRepositoryImpl
import com.meteomontana.android.domain.port.KeyValueStore
import com.meteomontana.android.domain.usecase.titleeffects.ObserveTitleEffectsUseCase
import com.meteomontana.android.domain.usecase.titleeffects.RefreshTitleEffectsUseCase
import io.ktor.client.HttpClient

/** Parte del grafo de iOS para el catálogo de efectos de título (aparte para no engordar [IosDependencyContainer]). */
class TitleEffectsModule(httpClient: HttpClient, store: KeyValueStore? = null) {
    private val repository = TitleEffectRepositoryImpl(
        KtorTitleEffectApi(httpClient),
        store ?: object : KeyValueStore {
            private val map = mutableMapOf<String, String>()
            override fun getString(key: String): String? = map[key]
            override fun putString(key: String, value: String) { map[key] = value }
        }
    )
    val observeTitleEffects = ObserveTitleEffectsUseCase(repository)
    val refreshTitleEffects = RefreshTitleEffectsUseCase(repository)
}
