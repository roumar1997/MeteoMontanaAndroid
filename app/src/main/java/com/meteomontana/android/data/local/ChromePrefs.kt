package com.meteomontana.android.data.local

import android.content.Context
import com.meteomontana.android.ui.theme.ChromeTreatment
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Qué aspecto tiene el armazón de la app (barra de pestañas y hojas).
 *
 * Es una preferencia **temporal**, y conviene que quede dicho: existe para que
 * Rodrigo compare los tres tratamientos en el móvil, sobre contenido de verdad,
 * y elija. En cuanto haya elegido, esto se queda con el valor bueno fijo y el
 * selector desaparece de Ajustes — no tiene sentido pedirle a un usuario que
 * decida cómo se dibuja su propia app.
 *
 * Mismo patrón que [FeedPublishPrefs]: SharedPreferences, sin ceremonia.
 */
object ChromePrefs {
    private const val FILE = "chrome"
    private const val KEY = "treatment"

    /**
     * Por defecto, CRISTAL: es el que se quiere enseñar. En los móviles que no
     * pueden con él, [ChromeTreatment.paraApi] lo baja a sólido solo.
     */
    private val POR_DEFECTO = ChromeTreatment.CRISTAL

    private val _actual = MutableStateFlow<ChromeTreatment?>(null)

    /**
     * El tratamiento vigente, observable.
     *
     * Hace falta que sea un flujo y no una simple lectura: cambiarlo en Ajustes
     * tiene que redibujar la barra **al momento**, sin reiniciar la app. Si no,
     * comparar los tres tratamientos sería un suplicio.
     */
    fun flow(context: Context): StateFlow<ChromeTreatment?> {
        if (_actual.value == null) _actual.value = leer(context)
        return _actual.asStateFlow()
    }

    fun get(context: Context): ChromeTreatment = _actual.value ?: leer(context)

    fun set(context: Context, treatment: ChromeTreatment) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit().putString(KEY, treatment.name).apply()
        _actual.value = treatment
    }

    private fun leer(context: Context): ChromeTreatment {
        val raw = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .getString(KEY, null) ?: return POR_DEFECTO
        return runCatching { ChromeTreatment.valueOf(raw) }.getOrDefault(POR_DEFECTO)
    }
}
