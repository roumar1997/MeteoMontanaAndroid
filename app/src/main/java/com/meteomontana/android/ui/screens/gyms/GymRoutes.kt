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
            onCreateMeetup = { gym -> sheetNav.navigate(GymNav.meetup(gymId, gym.name)) }
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
