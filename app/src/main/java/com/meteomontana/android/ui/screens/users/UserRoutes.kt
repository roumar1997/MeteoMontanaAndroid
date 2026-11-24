package com.meteomontana.android.ui.screens.users

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.meteomontana.android.navigation.Routes
import com.meteomontana.android.ui.navigation.Destination
import com.meteomontana.android.ui.navigation.Navigator

/** Perfil público, seguidores, solicitudes y búsqueda de personas dentro del overlay. */
fun NavGraphBuilder.userRoutes(nav: Navigator) {
    composable(
        route = Routes.PUBLIC_PROFILE,
        arguments = listOf(navArgument("uid") { type = NavType.StringType })
    ) {
        PublicProfileScreen(
            onBack = nav::back,
            onFollowersClick = { uid -> nav.navigate(Destination.FollowList(uid, "followers")) },
            onFollowingClick = { uid -> nav.navigate(Destination.FollowList(uid, "following")) },
            onOpenChat = { uid -> nav.navigate(Destination.Chat(uid)) },
            onOpenBoulders = { uid -> nav.navigate(Destination.JournalEntries(filter = "discipline:BOULDER", uid = uid)) },
            onOpenRoutes = { uid -> nav.navigate(Destination.JournalEntries(filter = "discipline:ROUTE", uid = uid)) },
            onOpenMaxGrade = { uid -> nav.navigate(Destination.JournalEntries(filter = "grade-max", uid = uid)) },
            onOpenSchools = { uid -> nav.navigate(Destination.JournalSchools(uid)) },
            onOpenSchoolEntries = { uid, schoolName -> nav.navigate(Destination.JournalSectors(schoolName, uid)) },
            onOpenProjects = { uid -> nav.navigate(Destination.Projects(uid)) },
            onOpenStats = { uid -> nav.navigate(Destination.JournalStats(uid)) },
            onOpenStatsTab = { uid, tab -> nav.navigate(Destination.JournalStats(uid, tab)) },
            onOpenGymAscents = { uid -> nav.navigate(Destination.GymAscents(uid)) },
            onOpenBoards = { uid -> nav.navigate(Destination.GymBoards(uid)) },
            onOpenGym = { nav.navigate(Destination.GymDetail(it)) },
            onOpenPosts = { uid -> nav.navigate(Destination.MyPosts(uid)) },
            // Sección Publicaciones (feed) del perfil:
            onOpenUserProfile = { uid -> nav.navigate(Destination.PublicProfile(uid)) },
            onOpenFeedSchool = { schoolId, lineId, lineName, blockId ->
                nav.navigate(Destination.SchoolDetail(schoolId, via = lineName, viaId = lineId, blockId = blockId))
            }
        )
    }
    composable(
        route = Routes.FOLLOW_LIST,
        arguments = listOf(
            navArgument("uid") { type = NavType.StringType },
            navArgument("mode") { type = NavType.StringType }
        )
    ) {
        FollowListScreen(
            onBack = nav::back,
            onUserClick = { uid -> nav.navigate(Destination.PublicProfile(uid)) }
        )
    }
    composable(Routes.FOLLOW_REQUESTS) {
        FollowRequestsScreen(
            onBack = nav::back,
            onUserClick = { uid -> nav.navigate(Destination.PublicProfile(uid)) }
        )
    }
    composable(Routes.SEARCH_USERS) {
        SearchUsersScreen(
            onBack = nav::back,
            onUserClick = { uid -> nav.navigate(Destination.PublicProfile(uid)) }
        )
    }
}
