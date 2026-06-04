package com.meteomontana.android.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.List
import androidx.compose.material.icons.outlined.Radar
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Rutas de la app y configuración de tabs.
 */
sealed class Tab(val route: String, val label: String, val icon: ImageVector) {
    data object Weather  : Tab("weather",  "Tiempo",   Icons.Outlined.Cloud)
    data object Schools  : Tab("schools",  "Escuelas", Icons.Outlined.List)
    data object Radar    : Tab("radar",    "Radar",    Icons.Outlined.Radar)
}

val mainTabs = listOf(Tab.Weather, Tab.Schools, Tab.Radar)

object Routes {
    const val SCHOOL_DETAIL = "schools/{schoolId}"
    fun schoolDetail(id: String) = "schools/$id"
    const val PROFILE = "profile"
    const val EDIT_PROFILE = "profile/edit"
    const val MY_SUBMISSIONS = "submissions/me"
    const val SUBMIT_SCHOOL = "submissions/new"
    const val SEARCH_USERS = "users/search"
    const val NOTIFICATIONS = "notifications"
    const val PUBLIC_PROFILE = "users/{uid}"
    fun publicProfile(uid: String) = "users/$uid"
    const val ADMIN = "admin"
}
