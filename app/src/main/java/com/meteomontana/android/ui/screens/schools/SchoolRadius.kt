package com.meteomontana.android.ui.screens.schools

import com.meteomontana.android.domain.model.School
import com.meteomontana.android.domain.util.Geo

/**
 * Radio de distancia que de verdad se aplica a la lista.
 *
 * El radio por defecto (50 km) se mide desde la posición del móvil. Si el móvil está lejísimos de todas las
 * escuelas (emulador en EE. UU., viaje, ubicación falsa) ese radio dejaba «0 escuelas» nada más abrir la app, hasta
 * pulsar QUITAR FILTROS. Un radio que no contiene NINGUNA escuela del catálogo no sirve de filtro: se ignora
 * (devuelve null = sin límite). Si contiene alguna, se respeta tal cual.
 */
internal fun effectiveMaxDistanceKm(
    schools: List<School>,
    userLat: Double,
    userLon: Double,
    maxDistanceKm: Double?
): Double? {
    if (maxDistanceKm == null) return null
    val anyInside = schools.any { Geo.haversineKm(userLat, userLon, it.lat, it.lon) <= maxDistanceKm }
    return if (anyInside) maxDistanceKm else null
}
