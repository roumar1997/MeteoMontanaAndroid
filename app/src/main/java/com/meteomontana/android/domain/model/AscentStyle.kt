package com.meteomontana.android.domain.model

/**
 * Estilo de un ascenso como UNA eleccion excluyente (Ninguno / A vista / Al flash).
 * En el servidor y en el diario viven como dos booleanos; esta clase es el puente
 * y garantiza que nunca queden los dos encendidos.
 */
enum class AscentStyle(val aVista: Boolean, val alFlash: Boolean) {
    NONE(false, false),
    ONSIGHT(true, false),
    FLASH(false, true);

    companion object {
        /** Si por datos viejos vinieran los dos a true, gana a vista (el mas exigente). */
        fun fromFlags(aVista: Boolean, alFlash: Boolean): AscentStyle = when {
            aVista -> ONSIGHT
            alFlash -> FLASH
            else -> NONE
        }
    }
}
