package com.meteomontana.android.ui.navigation

import com.meteomontana.android.navigation.Routes

/**
 * Contrato de navegación: cada sitio al que se puede ir desde una pantalla.
 *
 * Las pantallas piden un [Destination] al [Navigator] en vez de conocer la
 * pantalla de destino; así cada función se podrá sacar a su propio módulo sin
 * arrastrar al resto. La cadena de ruta sigue saliendo de [Routes] y [GymNav]
 * (mismas rutas que antes: los deep links y avisos no cambian).
 */
sealed interface Destination {
    val route: String

    // ── Escuelas ──
    data class SchoolDetail(
        val schoolId: String,
        val via: String? = null,
        val viaId: String? = null,
        val blockId: String? = null,
    ) : Destination {
        override val route get() = Routes.schoolDetail(schoolId, via, viaId, blockId)
    }
    data class DayDetail(val schoolId: String, val dayIndex: Int) : Destination {
        override val route get() = Routes.dayDetail(schoolId, dayIndex)
    }
    data class SchoolChat(val schoolId: String, val schoolName: String) : Destination {
        override val route get() = Routes.schoolChat(schoolId, schoolName)
    }
    /** El editor de topos va a pantalla completa (fuera del overlay). */
    data class TopoEditor(val blockId: String) : Destination {
        override val route get() = Routes.topoEditor(blockId)
    }
    data object MySubmissions : Destination { override val route = Routes.MY_SUBMISSIONS }
    data object SavedSchools : Destination { override val route = Routes.SAVED_SCHOOLS }

    // ── Perfil y diario ──
    data object EditProfile : Destination { override val route = Routes.EDIT_PROFILE }
    data object WeekendAlert : Destination { override val route = Routes.WEEKEND_ALERT }
    data class JournalEntries(val filter: String? = null, val uid: String? = null) : Destination {
        override val route get() = Routes.journalEntries(filter, uid)
    }
    data class JournalSchools(val uid: String? = null) : Destination {
        override val route get() = Routes.journalSchools(uid)
    }
    data class JournalSectors(val schoolName: String, val uid: String? = null) : Destination {
        override val route get() = Routes.journalSectors(schoolName, uid)
    }
    data class Projects(val uid: String? = null) : Destination {
        override val route get() = Routes.projects(uid)
    }
    data class JournalStats(val uid: String? = null, val tab: String? = null) : Destination {
        override val route get() = Routes.journalStats(uid, tab)
    }
    data class MyPosts(val uid: String? = null) : Destination {
        override val route get() = Routes.myPosts(uid)
    }

    // ── Personas ──
    data class PublicProfile(val uid: String) : Destination {
        override val route get() = Routes.publicProfile(uid)
    }
    data class FollowList(val uid: String, val mode: String) : Destination {
        override val route get() = Routes.followList(uid, mode)
    }
    data object FollowRequests : Destination { override val route = Routes.FOLLOW_REQUESTS }

    // ── Chats, feed y quedadas ──
    data class Chat(val uid: String) : Destination {
        override val route get() = Routes.chat(uid)
    }
    data class GroupChat(val convId: String) : Destination {
        override val route get() = Routes.groupChat(convId)
    }
    data object NewGroup : Destination { override val route = Routes.NEW_GROUP }
    data class FeedPost(val postId: String) : Destination {
        override val route get() = Routes.feedPost(postId)
    }
    data class MeetupDetail(val meetupId: String) : Destination {
        override val route get() = Routes.meetupDetail(meetupId)
    }

    // ── Rocódromos ──
    data class GymDetail(val gymId: Long) : Destination {
        override val route get() = GymNav.detail(gymId)
    }
    data object GymTraining : Destination { override val route = GymNav.TRAINING }
    data class GymAscents(val uid: String? = null) : Destination {
        override val route get() = GymNav.ascents(uid)
    }
    data class GymBoards(val uid: String? = null) : Destination {
        override val route get() = GymNav.boards(uid)
    }
    data class GymVisit(val gymId: Long, val uid: String? = null) : Destination {
        override val route get() = GymNav.visit(gymId, uid)
    }
    data class GymChat(val gymId: Long, val gymName: String) : Destination {
        override val route get() = GymNav.chat(gymId, gymName)
    }
    data class GymMeetup(val gymId: Long, val gymName: String) : Destination {
        override val route get() = GymNav.meetup(gymId, gymName)
    }
}
