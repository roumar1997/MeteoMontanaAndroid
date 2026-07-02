package com.meteomontana.android.domain.model

/** Una báscula WH-C06 encontrada al escanear BLE (identificada por su
 *  dirección/id de anuncio, con la fuerza de señal para poder elegir cuál
 *  usar si hay varias cerca). */
data class GripScaleDevice(
    val id: String,
    val rssi: Int,
    /** Alias local que le puso el usuario (SharedPreferences/UserDefaults,
     *  no viene del dispositivo). Null si nunca se ha renombrado. */
    val alias: String? = null
)

/** Una lectura de peso de la báscula, ~8 Hz mientras está emitiendo. */
data class GripReading(
    val kg: Double,
    val timestampMs: Long
)
