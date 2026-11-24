package com.meteomontana.android.ui.screens.gyms

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.meteomontana.android.ui.navigation.Destination
import com.meteomontana.android.ui.navigation.GymNav
import com.meteomontana.android.ui.navigation.Navigator
import com.meteomontana.android.ui.screens.meetups.CreateMeetupScreen
import com.meteomontana.android.ui.screens.meetups.MeetupPrefill
import com.meteomontana.android.ui.screens.profile.BoardsScreen
import com.meteomontana.android.ui.screens.profile.GymAscentsScreen
import com.meteomontana.android.ui.screens.profile.GymVisitScreen

/**
 * Pantallas de rocódromos dentro del overlay (`SheetNavHost`): la ficha, el entrenamiento, las ascensiones,
 * los boards, la visita, el chat y «crear quedada aquí». Las rutas viven en [GymNav] (contrato de navegación).
 */
fun NavGraphBuilder.gymRoutes(nav: Navigator) {
    composable(
        route = GymNav.DETAIL,
        arguments = listOf(navArgument("gymId") { type = NavType.StringType })
    ) { entry ->
        val gymId = entry.arguments?.getString("gymId")?.toLongOrNull() ?: -1L
        GymDetailScreen(
            onBack = nav::back,
            onCreateMeetup = { gym -> nav.navigate(Destination.GymMeetup(gymId, gym.name)) },
            onOpenChat = { gym -> nav.navigate(Destination.GymChat(gymId, gym.name)) }
        )
    }
    composable(GymNav.TRAINING) { GymTrainingScreen(onBack = nav::back) }
    composable(
        route = GymNav.ASCENTS,
        arguments = listOf(navArgument("uid") { type = NavType.StringType; nullable = true; defaultValue = null })
    ) { entry ->
        val uid = entry.arguments?.getString("uid")?.takeIf { it.isNotBlank() }
        GymAscentsScreen(
            uid = uid,
            onBack = nav::back,
            onOpenBoards = { nav.navigate(Destination.GymBoards(uid)) },
            onOpenGym = { nav.navigate(Destination.GymVisit(it, uid)) }
        )
    }
    composable(
        route = GymNav.BOARDS,
        arguments = listOf(navArgument("uid") { type = NavType.StringType; nullable = true; defaultValue = null })
    ) { entry ->
        val uid = entry.arguments?.getString("uid")?.takeIf { it.isNotBlank() }
        BoardsScreen(
            uid = uid,
            onBack = nav::back,
            onOpenGym = { nav.navigate(Destination.GymVisit(it, uid)) }
        )
    }
    composable(
        route = GymNav.VISIT,
        arguments = listOf(
            navArgument("gymId") { type = NavType.StringType },
            navArgument("uid") { type = NavType.StringType; nullable = true; defaultValue = null }
        )
    ) { entry ->
        GymVisitScreen(
            uid = entry.arguments?.getString("uid")?.takeIf { it.isNotBlank() },
            gymId = entry.arguments?.getString("gymId")?.toLongOrNull() ?: -1L,
            onBack = nav::back,
            onOpenGym = { nav.navigate(Destination.GymDetail(it)) }
        )
    }
    composable(
        route = GymNav.CHAT,
        arguments = listOf(
            navArgument("gymId") { type = NavType.StringType },
            navArgument("name") { type = NavType.StringType; defaultValue = "" }
        )
    ) { entry ->
        GymChatScreen(
            gymId = entry.arguments?.getString("gymId")?.toLongOrNull() ?: -1L,
            gymName = entry.arguments?.getString("name").orEmpty(),
            myUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: "",
            onBack = nav::back,
            onOpenChat = { uid -> nav.navigate(Destination.Chat(uid)) },
            onOpenProfile = { uid -> nav.navigate(Destination.PublicProfile(uid)) },
            bottomInset = com.meteomontana.android.ui.components.LocalTabBarInset.current
        )
    }
    composable(
        route = GymNav.MEETUP,
        arguments = listOf(
            navArgument("gymId") { type = NavType.StringType },
            navArgument("name") { type = NavType.StringType; nullable = true; defaultValue = null }
        )
    ) { entry ->
        val a = entry.arguments
        CreateMeetupScreen(
            prefill = MeetupPrefill(
                schoolId = "", schoolName = a?.getString("name").orEmpty(), day = null,
                name = com.meteomontana.android.util.AppText.get(
                    com.meteomontana.android.R.string.gym_meetup_default_name, a?.getString("name").orEmpty()
                ),
                description = null, gymId = a?.getString("gymId")?.toLongOrNull()
            ),
            onBack = nav::back,
            onCreated = { id -> nav.replace(Destination.MeetupDetail(id)) },
            onEditProfile = { nav.navigate(Destination.EditProfile) }
        )
    }
}
