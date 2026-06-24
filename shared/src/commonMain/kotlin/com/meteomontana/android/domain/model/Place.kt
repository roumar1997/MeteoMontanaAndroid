package com.meteomontana.android.domain.model

/** Localidad geocodificada (resultado del buscador del tiempo por pueblos). */
data class Place(val name: String, val lat: Double, val lon: Double)
