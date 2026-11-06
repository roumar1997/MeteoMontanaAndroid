package com.meteomontana.android.domain.port

/**
 * Almacén clave-valor mínimo para guardar cosas pequeñas entre arranques (no usa la base de
 * datos local: añadir una tabla allí borraría los datos sin conexión de los usuarios).
 * Android: SharedPreferences. iOS: NSUserDefaults.
 */
interface KeyValueStore {
    fun getString(key: String): String?
    fun putString(key: String, value: String)
}
