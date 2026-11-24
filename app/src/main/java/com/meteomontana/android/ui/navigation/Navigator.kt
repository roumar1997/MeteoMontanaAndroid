package com.meteomontana.android.ui.navigation

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.navigation.NavHostController

/** Puerto de navegación: las pantallas dicen ADÓNDE quieren ir, no CÓMO. */
interface Navigator {
    fun navigate(destination: Destination)

    /** Atrás: cierra la pantalla actual (o el overlay entero si era la última). */
    fun back()

    /** Sustituye la pantalla actual por [destination] (p. ej. al crear un grupo o una quedada). */
    fun replace(destination: Destination)
}

/** Navigator del overlay en curso, para las piezas que no reciben callbacks. */
val LocalNavigator = staticCompositionLocalOf<Navigator?> { null }

/**
 * Implementación del overlay: casi todo se apila en el NavHost del overlay
 * (`sheetNav`); el editor de topos va a pantalla completa en el NavHost
 * principal, sin cerrar el overlay, para volver al detalle al terminar.
 */
class SheetNavigator(
    private val sheetNav: NavHostController,
    private val mainNav: NavHostController,
    private val onBack: () -> Unit,
) : Navigator {
    override fun navigate(destination: Destination) {
        when (destination) {
            is Destination.TopoEditor -> mainNav.navigate(destination.route)
            else -> sheetNav.navigate(destination.route)
        }
    }

    override fun back() = onBack()

    override fun replace(destination: Destination) {
        sheetNav.popBackStack()
        navigate(destination)
    }
}
