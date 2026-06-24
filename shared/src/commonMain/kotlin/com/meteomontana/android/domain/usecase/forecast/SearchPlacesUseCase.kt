package com.meteomontana.android.domain.usecase.forecast

import com.meteomontana.android.data.api.KtorGeocodeApi
import com.meteomontana.android.domain.model.Place

/** Busca localidades por nombre (buscador del tiempo). Vacío si la query lo es. */
class SearchPlacesUseCase(private val api: KtorGeocodeApi) {
    @Throws(Exception::class)
    suspend operator fun invoke(query: String): List<Place> =
        if (query.isBlank()) emptyList()
        else api.geocode(query.trim()).map { Place(it.name, it.lat, it.lon) }
}
