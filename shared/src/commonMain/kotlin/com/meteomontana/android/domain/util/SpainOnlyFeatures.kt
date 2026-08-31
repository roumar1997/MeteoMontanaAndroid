package com.meteomontana.android.domain.util

/**
 * Qué funciones dependen de AEMET y, por tanto, solo valen en España.
 *
 * El radar es un compuesto de los quince radares de la agencia estatal y el
 * boletín de montaña cubre nueve macizos españoles. En una escuela de Francia o
 * Portugal no hay ni imagen ni boletín, así que se ESCONDEN: enseñar un hueco
 * vacío o un error es peor que no ofrecer la función.
 *
 * La regla vive aquí para que las dos apps la apliquen igual y para que no haya
 * que repetir la comparación de país por las pantallas. Espejo de
 * `CountryCatalog.hasSpanishWeatherServices` del backend.
 */
object SpainOnlyFeatures {

    const val SPAIN = "ES"

    /**
     * ¿Se enseña el boletín de montaña de AEMET para una escuela de este país?
     *
     * Con [countryCode] nulo o vacío se responde que SÍ: es lo que devuelve un
     * servidor anterior al catálogo, y todo lo que había entonces era español.
     */
    fun showsMountainBulletin(countryCode: String?): Boolean =
        countryCode.isNullOrBlank() || countryCode.trim().uppercase() == SPAIN

    /** Igual que [showsMountainBulletin]: el radar es de la misma agencia. */
    fun showsRadar(countryCode: String?): Boolean = showsMountainBulletin(countryCode)
}
