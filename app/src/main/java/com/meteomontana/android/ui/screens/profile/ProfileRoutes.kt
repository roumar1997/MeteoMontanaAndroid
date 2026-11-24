package com.meteomontana.android.ui.screens.profile

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.meteomontana.android.navigation.Routes
import com.meteomontana.android.ui.navigation.Destination
import com.meteomontana.android.ui.navigation.Navigator

/** Pantallas de perfil y diario dentro del overlay. Navegan solo con [Navigator]. */
fun NavGraphBuilder.profileRoutes(nav: Navigator) {
    composable(Routes.PROFILE) {
        val myUid = { com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid }
        ProfileScreen(
            onBack = nav::back,
            onEdit = { nav.navigate(Destination.EditProfile) },
            onSubmissions = { nav.navigate(Destination.MySubmissions) },
            onSavedSchools = { nav.navigate(Destination.SavedSchools) },
            onWeekendAlert = { nav.navigate(Destination.WeekendAlert) },
            onOpenFollowers = { myUid()?.let { nav.navigate(Destination.FollowList(it, "followers")) } },
            onOpenFollowing = { myUid()?.let { nav.navigate(Destination.FollowList(it, "following")) } },
            onOpenFollowRequests = { nav.navigate(Destination.FollowRequests) },
            onOpenSchoolEntries = { schoolName -> nav.navigate(Destination.JournalSectors(schoolName)) },
            onOpenBoulders = { nav.navigate(Destination.JournalEntries("discipline:BOULDER")) },
            onOpenRoutes = { nav.navigate(Destination.JournalEntries("discipline:ROUTE")) },
            onOpenAllSchools = { nav.navigate(Destination.JournalSchools(null)) },
            onOpenMaxGrade = { nav.navigate(Destination.JournalEntries("grade-max")) },
            onOpenProjects = { nav.navigate(Destination.Projects(null)) },
            onOpenMyPosts = { nav.navigate(Destination.MyPosts()) },
            onOpenStats = { nav.navigate(Destination.JournalStats()) },
            onOpenStatsTab = { tab -> nav.navigate(Destination.JournalStats(tab = tab)) },
            onOpenGymTraining = { nav.navigate(Destination.GymTraining) },
            onOpenGymAscents = { nav.navigate(Destination.GymAscents()) },
            onOpenBoards = { nav.navigate(Destination.GymBoards()) },
            onOpenGym = { nav.navigate(Destination.GymDetail(it)) }
        )
    }
    // C4: MIS ESTADISTICAS (piramide, racha, progresion).
    composable(
        Routes.JOURNAL_STATS,
        arguments = listOf(
            navArgument("uid") { type = NavType.StringType; nullable = true; defaultValue = null },
            navArgument("tab") { type = NavType.StringType; nullable = true; defaultValue = null }
        )
    ) { entry ->
        val statsUid = entry.arguments?.getString("uid")?.takeIf { it.isNotBlank() }
        StatsHubScreen(
            uid = statsUid,
            initialTab = entry.arguments?.getString("tab")?.takeIf { it.isNotBlank() },
            onOpenAscents = { nav.navigate(Destination.GymAscents(statsUid)) },
            onBack = nav::back,
            onOpenSchool = { school -> nav.navigate(Destination.JournalSectors(school)) },
            onOpenBlock = { schoolId, lineName, lineId ->
                nav.navigate(Destination.SchoolDetail(schoolId, via = lineName, viaId = lineId))
            },
            onOpenAllSchools = { nav.navigate(Destination.JournalSchools(null)) },
            onOpenProjects = { nav.navigate(Destination.Projects(null)) },
            onOpenGym = { nav.navigate(Destination.GymVisit(it, statsUid)) }
        )
    }
    composable(Routes.EDIT_PROFILE) { EditProfileScreen(onBack = nav::back) }
    composable(Routes.WEEKEND_ALERT) { WeekendAlertScreen(onBack = nav::back) }
    composable(
        route = Routes.JOURNAL_ENTRIES,
        arguments = listOf(
            navArgument("filter") { type = NavType.StringType; nullable = true; defaultValue = null },
            navArgument("uid") { type = NavType.StringType; nullable = true; defaultValue = null }
        )
    ) {
        JournalEntriesScreen(
            onBack = nav::back,
            onOpenSchool = { id, via, viaId -> nav.navigate(Destination.SchoolDetail(id, via, viaId)) }
        )
    }
    composable(
        route = Routes.JOURNAL_SCHOOLS,
        arguments = listOf(navArgument("uid") { type = NavType.StringType; nullable = true; defaultValue = null })
    ) { entry ->
        val uid = entry.arguments?.getString("uid")?.takeIf { it.isNotBlank() }
        JournalSchoolsScreen(
            onBack = nav::back,
            // Pasa por "sectores" (JOURNAL_SECTORS) para no mezclar todos los
            // bloques/vías de la escuela de golpe.
            onSchoolClick = { schoolName -> nav.navigate(Destination.JournalSectors(schoolName, uid)) }
        )
    }
    composable(
        route = Routes.JOURNAL_SECTORS,
        arguments = listOf(
            navArgument("school") { type = NavType.StringType; defaultValue = "" },
            navArgument("uid") { type = NavType.StringType; nullable = true; defaultValue = null }
        )
    ) { entry ->
        val uid = entry.arguments?.getString("uid")?.takeIf { it.isNotBlank() }
        JournalSectorsScreen(
            onBack = nav::back,
            onSectorClick = { schoolName, sectorName ->
                nav.navigate(Destination.JournalEntries(filter = "school:$schoolName|sector:$sectorName", uid = uid))
            },
            onOpenSchool = { id, via, viaId -> nav.navigate(Destination.SchoolDetail(id, via, viaId)) }
        )
    }
    composable(
        route = Routes.PROJECTS,
        arguments = listOf(navArgument("uid") { type = NavType.StringType; nullable = true; defaultValue = null })
    ) {
        ProjectsScreen(
            onBack = nav::back,
            onOpenSchool = { id, via, viaId -> nav.navigate(Destination.SchoolDetail(id, via, viaId)) }
        )
    }
}
