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
    const val FOLLOW_LIST = "users/{uid}/follow-list/{mode}"
    fun followList(uid: String, mode: String) = "users/$uid/follow-list/$mode"
    const val FOLLOW_REQUESTS = "me/follow-requests"
    const val CHAT_LIST = "chats"
    const val CHAT = "chats/{uid}"
    fun chat(uid: String) = "chats/$uid"
    const val TOPO_EDITOR = "topo/{blockId}"
    fun topoEditor(blockId: String) = "topo/$blockId"
    const val JOURNAL_ENTRIES = "journal/entries?filter={filter}&uid={uid}"
    fun journalEntries(filter: String? = null, uid: String? = null): String {
        val f = filter ?: ""
        val u = uid ?: ""
        return "journal/entries?filter=$f&uid=$u"
    }
    const val JOURNAL_SCHOOLS = "journal/schools?uid={uid}"
    fun journalSchools(uid: String? = null): String = "journal/schools?uid=${uid ?: ""}"

    // schoolId opcional (vacío = tab Tiempo, ubicación actual)
    const val DAY_DETAIL = "day/{schoolId}/{dayIndex}"
    fun dayDetail(schoolId: String, dayIndex: Int) = "day/$schoolId/$dayIndex"
    const val DAY_DETAIL_BY_LOCATION = "day-loc/{lat}/{lon}/{dayIndex}"
    fun dayDetailByLocation(lat: Double, lon: Double, dayIndex: Int) = "day-loc/$lat/$lon/$dayIndex"

    const val SAVED_SCHOOLS = "saved"
}
