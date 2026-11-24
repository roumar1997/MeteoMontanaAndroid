package com.meteomontana.android.navigation

import com.meteomontana.android.R
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DynamicFeed
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Rutas de la app y configuración de tabs.
 */
sealed class Tab(val route: String, @androidx.annotation.StringRes val labelRes: Int, val icon: ImageVector) {
    // La primera pestaña se llama "Radar" (su vista principal es el radar);
    // dentro, el conmutador TIEMPO ⇄ RADAR da acceso a la previsión.
    data object Weather  : Tab("weather",  R.string.tab_radar,    Icons.Filled.Sensors)
    data object Schools  : Tab("schools",  R.string.tab_schools, Icons.Filled.List)
    data object Meetups  : Tab("meetups",  R.string.tab_meetups, Icons.Filled.Groups)
    // "Feed" (mismo nombre ES/EN); icono de tarjetas apiladas, distinto de
    // las personas de Quedadas/Perfil. La ruta sigue siendo "community"
    // (estado guardado / enlaces existentes).
    data object Community : Tab("community", R.string.tab_feed, Icons.Filled.DynamicFeed)
    data object Profile  : Tab("profile-tab", R.string.tab_profile, Icons.Filled.Person)
}

// Perfil como pestaña desde 2026-07-03; Feed (feed social + ranking) desde
// 2026-07-10 (renombrado de "Comunidad" el 2026-07-13). Orden:
// Radar, Feed, Escuelas, Quedadas, Perfil.
val mainTabs = listOf(Tab.Weather, Tab.Community, Tab.Schools, Tab.Meetups, Tab.Profile)
