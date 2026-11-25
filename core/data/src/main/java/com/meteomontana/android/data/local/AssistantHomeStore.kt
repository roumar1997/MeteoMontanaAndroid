package com.meteomontana.android.data.local

import android.content.Context
import com.meteomontana.android.domain.model.UserLocation
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * La casa del usuario para «cerca de casa» / «donde vivo». Se guarda SOLO en este móvil (nunca en el servidor); al
 * preguntar se envía junto a la pregunta y el servidor la usa solo para medir distancias, como la ubicación.
 * Espejo de `AssistantHomeStore` (iOS).
 */
@Singleton
class AssistantHomeStore @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("assistant_home", Context.MODE_PRIVATE)

    val home: UserLocation?
        get() {
            if (!prefs.contains(LAT) || !prefs.contains(LON)) return null
            return UserLocation(Double.fromBits(prefs.getLong(LAT, 0)), Double.fromBits(prefs.getLong(LON, 0)))
        }

    fun save(lat: Double, lon: Double) = prefs.edit().putLong(LAT, lat.toRawBits()).putLong(LON, lon.toRawBits()).apply()

    fun clear() = prefs.edit().remove(LAT).remove(LON).apply()

    private companion object {
        const val LAT = "lat"
        const val LON = "lon"
    }
}
