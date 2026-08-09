package com.meteomontana.android.domain.usecase.geo

import com.meteomontana.android.domain.model.Country
import com.meteomontana.android.domain.repository.CountryRepository

/**
 * Países abiertos, con sus regiones, para los desplegables de proponer escuela.
 */
class GetCountriesUseCase(private val repository: CountryRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(): List<Country> = repository.countries()
}
