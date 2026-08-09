package com.meteomontana.android.domain.repository

import com.meteomontana.android.domain.model.Country

/** De dónde salen los países y sus regiones. */
interface CountryRepository {
    @Throws(Exception::class)
    suspend fun countries(): List<Country>
}
