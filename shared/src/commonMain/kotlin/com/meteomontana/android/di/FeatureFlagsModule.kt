package com.meteomontana.android.di

import com.meteomontana.android.data.api.KtorFeatureFlagApi
import com.meteomontana.android.data.repository.FeatureFlagRepositoryImpl
import com.meteomontana.android.domain.port.KeyValueStore
import com.meteomontana.android.domain.usecase.featureflags.ObserveFeatureFlagsUseCase
import com.meteomontana.android.domain.usecase.featureflags.RefreshFeatureFlagsUseCase
import io.ktor.client.HttpClient

/**
 * Parte del grafo de iOS dedicada a los interruptores de funciones (aparte para que
 * [IosDependencyContainer] no siga creciendo). Si no se le da almacén, los interruptores
 * viven solo en memoria (no se recuerdan entre arranques).
 */
class FeatureFlagsModule(httpClient: HttpClient, store: KeyValueStore? = null) {
    private val repository = FeatureFlagRepositoryImpl(
        KtorFeatureFlagApi(httpClient),
        store ?: InMemoryKeyValueStore()
    )
    val observeFeatureFlags = ObserveFeatureFlagsUseCase(repository)
    val refreshFeatureFlags = RefreshFeatureFlagsUseCase(repository)
}

private class InMemoryKeyValueStore : KeyValueStore {
    private val map = mutableMapOf<String, String>()
    override fun getString(key: String): String? = map[key]
    override fun putString(key: String, value: String) { map[key] = value }
}
