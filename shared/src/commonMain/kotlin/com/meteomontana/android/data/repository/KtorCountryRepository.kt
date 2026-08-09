package com.meteomontana.android.data.repository

import com.meteomontana.android.data.api.KtorGeoApi
import com.meteomontana.android.domain.model.Country
import com.meteomontana.android.domain.repository.CountryRepository

/** Traduce el catálogo del servidor al modelo de dominio. */
class KtorCountryRepository(private val api: KtorGeoApi) : CountryRepository {
    @Throws(Exception::class)
    override suspend fun countries(): List<Country> =
        api.countries().map { Country(it.code, it.name, it.regions) }
}
