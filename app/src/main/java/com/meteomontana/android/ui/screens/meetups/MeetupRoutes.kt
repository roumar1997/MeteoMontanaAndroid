package com.meteomontana.android.ui.screens.meetups

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.meteomontana.android.navigation.Routes
import com.meteomontana.android.ui.navigation.Destination
import com.meteomontana.android.ui.navigation.Navigator

/** Ficha de quedada, crear quedada y alerta de quedadas dentro del overlay. */
fun NavGraphBuilder.meetupRoutes(nav: Navigator) {
    composable(
        route = Routes.MEETUP_DETAIL,
        arguments = listOf(navArgument("meetupId") { type = NavType.StringType })
    ) { entry ->
        MeetupDetailScreen(
            meetupId = entry.arguments?.getString("meetupId") ?: "",
            onBack = nav::back,
            onOpenChat = { convId -> nav.navigate(Destination.GroupChat(convId)) },
            onOpenSchool = { id -> nav.navigate(Destination.SchoolDetail(id)) },
            onOpenProfile = { uid -> nav.navigate(Destination.PublicProfile(uid)) },
            onEditProfile = { nav.navigate(Destination.EditProfile) }
        )
    }
    composable(
        route = Routes.CREATE_MEETUP_PATTERN,
        arguments = listOf("schoolId", "schoolName", "day", "name", "desc").map { key ->
            navArgument(key) { type = NavType.StringType; nullable = true; defaultValue = null }
        }
    ) { entry ->
        val a = entry.arguments
        CreateMeetupScreen(
            prefill = a?.getString("schoolId")?.let { id ->
                MeetupPrefill(
                    id, a.getString("schoolName").orEmpty(), a.getString("day"),
                    a.getString("name").orEmpty(), a.getString("desc"))
            },
            onBack = nav::back,
            onCreated = { id -> nav.replace(Destination.MeetupDetail(id)) },
            onEditProfile = { nav.navigate(Destination.EditProfile) }
        )
    }
    composable(Routes.MEETUP_ALERT) { MeetupAlertScreen(onBack = nav::back) }
}
