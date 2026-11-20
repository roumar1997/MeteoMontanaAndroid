package com.meteomontana.android.ui.screens.gyms

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.meteomontana.android.navigation.Routes
import com.meteomontana.android.ui.screens.meetups.CreateMeetupScreen
import com.meteomontana.android.ui.screens.meetups.MeetupPrefill

/**
 * Pantallas de rocódromos dentro del overlay (`SheetNavHost`): la ficha y «crear quedada aquí». Van en su propio
 * fichero para que SheetOverlay no crezca con cada función.
 */
/** Rutas de rocódromos dentro del overlay: la ficha y «crear quedada aquí» (la quedada propone «Quedada en <rocódromo>»). */
object GymNav {
    const val DETAIL = "gyms/{gymId}"
    fun detail(id: Long) = "gyms/$id"
    const val TRAINING = "gym-training"
    /** Ascensiones (por rocódromo, grado y mes) de una persona; sin uid, las mías. */
    const val ASCENTS = "gym-ascents?uid={uid}"
    fun ascents(uid: String? = null) = if (uid.isNullOrBlank()) "gym-ascents" else "gym-ascents?uid=$uid"
    const val CHAT = "gyms/{gymId}/chat?name={name}"
    fun chat(id: Long, name: String) = "gyms/$id/chat?name=${android.net.Uri.encode(name)}"
    const val MEETUP = "gyms/{gymId}/meetup?name={name}"
    fun meetup(id: Long, name: String) = "gyms/$id/meetup?name=${android.net.Uri.encode(name)}"
}

fun NavGraphBuilder.gymRoutes(sheetNav: NavHostController, popSheetOrDismiss: () -> Unit) {
    composable(
        route = GymNav.DETAIL,
        arguments = listOf(navArgument("gymId") { type = NavType.StringType })
    ) { entry ->
        val gymId = entry.arguments?.getString("gymId")?.toLongOrNull() ?: -1L
        GymDetailScreen(
            onBack = popSheetOrDismiss,
            onCreateMeetup = { gym -> sheetNav.navigate(GymNav.meetup(gymId, gym.name)) },
            onOpenChat = { gym -> sheetNav.navigate(GymNav.chat(gymId, gym.name)) }
        )
    }
    composable(GymNav.TRAINING) { GymTrainingScreen(onBack = popSheetOrDismiss) }
    composable(
        route = GymNav.ASCENTS,
        arguments = listOf(navArgument("uid") { type = NavType.StringType; nullable = true; defaultValue = null })
    ) { entry ->
        com.meteomontana.android.ui.screens.profile.GymAscentsScreen(
            uid = entry.arguments?.getString("uid")?.takeIf { it.isNotBlank() },
            onBack = popSheetOrDismiss,
            onOpenGym = { sheetNav.navigate(GymNav.detail(it)) }
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
            onBack = popSheetOrDismiss,
            onOpenChat = { uid -> sheetNav.navigate(Routes.chat(uid)) },
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
            onBack = popSheetOrDismiss,
            onCreated = { id ->
                sheetNav.popBackStack()
                sheetNav.navigate(Routes.meetupDetail(id))
            },
            onEditProfile = { sheetNav.navigate(Routes.EDIT_PROFILE) }
        )
    }
}
