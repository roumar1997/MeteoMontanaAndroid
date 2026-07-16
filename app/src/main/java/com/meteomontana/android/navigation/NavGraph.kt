package com.meteomontana.android.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DynamicFeed
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.List
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Radar
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Rutas de la app y configuración de tabs.
 */
sealed class Tab(val route: String, val label: String, val icon: ImageVector) {
    // La primera pestaña se llama "Radar" (su vista principal es el radar);
    // dentro, el conmutador TIEMPO ⇄ RADAR da acceso a la previsión.
    data object Weather  : Tab("weather",  "Radar",    Icons.Outlined.Radar)
    data object Schools  : Tab("schools",  "Escuelas", Icons.Outlined.List)
    data object Meetups  : Tab("meetups",  "Quedadas", Icons.Outlined.Groups)
    // "Feed" (mismo nombre ES/EN); icono de tarjetas apiladas, distinto de
    // las personas de Quedadas/Perfil. La ruta sigue siendo "community"
    // (estado guardado / enlaces existentes).
    data object Community : Tab("community", "Feed", Icons.Outlined.DynamicFeed)
    data object Profile  : Tab("profile-tab", "Perfil", Icons.Outlined.Person)
}

// Perfil como pestaña desde 2026-07-03; Feed (feed social + ranking) desde
// 2026-07-10 (renombrado de "Comunidad" el 2026-07-13). Orden:
// Radar, Feed, Escuelas, Quedadas, Perfil.
val mainTabs = listOf(Tab.Weather, Tab.Community, Tab.Schools, Tab.Meetups, Tab.Profile)

object Routes {
    const val SCHOOL_DETAIL = "schools/{schoolId}?via={via}&viaId={viaId}&blockId={blockId}"
    fun schoolDetail(id: String, via: String? = null, viaId: String? = null, blockId: String? = null): String {
        val q = buildList {
            via?.takeIf { it.isNotBlank() }?.let { add("via=${android.net.Uri.encode(it)}") }
            viaId?.takeIf { it.isNotBlank() }?.let { add("viaId=${android.net.Uri.encode(it)}") }
            // Post de piedra nueva del feed: abre directamente esa piedra.
            blockId?.takeIf { it.isNotBlank() }?.let { add("blockId=${android.net.Uri.encode(it)}") }
        }
        return "schools/$id" + (if (q.isEmpty()) "" else "?" + q.joinToString("&"))
    }
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
    const val NEW_GROUP = "chats/new-group"
    const val GROUP_CHAT = "group-chats/{convId}"
    fun groupChat(convId: String) = "group-chats/$convId"
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
    // Pantalla intermedia: escuela → sectores → (al pulsar sector) sus bloques/vías.
    const val JOURNAL_SECTORS = "journal/sectors?school={school}&uid={uid}"
    fun journalSectors(schoolName: String, uid: String? = null): String =
        "journal/sectors?school=${android.net.Uri.encode(schoolName)}&uid=${uid ?: ""}"
    // Proyectos: bloques/vías que estás probando, aún no hechos.
    const val PROJECTS = "journal/projects?uid={uid}"
    fun projects(uid: String? = null): String = "journal/projects?uid=${uid ?: ""}"

    // schoolId opcional (vacío = tab Tiempo, ubicación actual)
    const val DAY_DETAIL = "day/{schoolId}/{dayIndex}"
    fun dayDetail(schoolId: String, dayIndex: Int) = "day/$schoolId/$dayIndex"
    const val DAY_DETAIL_BY_LOCATION = "day-loc/{lat}/{lon}/{dayIndex}"
    fun dayDetailByLocation(lat: Double, lon: Double, dayIndex: Int) = "day-loc/$lat/$lon/$dayIndex"

    const val SAVED_SCHOOLS = "saved"
    const val WEEKEND_ALERT = "weekend-alert"
    const val COMPARE = "compare/{ids}"
    fun compare(ids: List<String>) = "compare/${ids.joinToString(",")}"

    // Detalle de un post del feed Comunidad (push/campanita "feed_post").
    const val FEED_POST = "feed/{postId}"
    fun feedPost(postId: String) = "feed/$postId"

    // "Mis publicaciones": pantalla dedicada con el feed propio (scope=mine).
    const val MY_POSTS = "profile/my-posts"

    const val MEETUP_DETAIL = "meetups/{meetupId}"
    fun meetupDetail(id: String) = "meetups/$id"
    const val CREATE_MEETUP = "meetups/new"
    const val MEETUP_ALERT = "meetups/alert"

}
