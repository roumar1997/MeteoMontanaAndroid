package com.meteomontana.android.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.meteomontana.android.navigation.Routes
import com.meteomontana.android.navigation.Tab
import com.meteomontana.android.navigation.mainTabs
import com.meteomontana.android.ui.screens.admin.AdminScreen
import com.meteomontana.android.ui.screens.chat.ChatListScreen
import com.meteomontana.android.ui.screens.chat.ChatScreen
import com.meteomontana.android.ui.screens.day.DayDetailScreen
import com.meteomontana.android.ui.screens.detail.SchoolDetailScreen
import com.meteomontana.android.ui.screens.saved.SavedSchoolsScreen
import com.meteomontana.android.ui.screens.notifications.NotificationsScreen
import com.meteomontana.android.ui.screens.profile.EditProfileScreen
import com.meteomontana.android.ui.screens.profile.JournalEntriesScreen
import com.meteomontana.android.ui.screens.profile.JournalSchoolsScreen
import com.meteomontana.android.ui.screens.profile.ProfileScreen
import com.meteomontana.android.ui.screens.radar.RadarScreen
import com.meteomontana.android.ui.screens.schools.SchoolListScreen
import com.meteomontana.android.ui.screens.submissions.MySubmissionsScreen
import com.meteomontana.android.ui.screens.submissions.SubmitSchoolScreen
import com.meteomontana.android.ui.screens.topo.TopoEditorScreen
import com.meteomontana.android.ui.screens.users.FollowListScreen
import com.meteomontana.android.ui.screens.users.FollowRequestsScreen
import com.meteomontana.android.ui.screens.users.PublicProfileScreen
import com.meteomontana.android.ui.screens.users.SearchUsersScreen
import com.meteomontana.android.ui.screens.weather.WeatherScreen

@Composable
fun MainScreen(
    deepLink: com.meteomontana.android.DeepLinkTarget? = null,
    onDeepLinkConsumed: () -> Unit = {}
) {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route

    // Consume el deep link entrante del push: navega al destino + marca consumido.
    // launchSingleTop evita apilar dos veces el mismo destino si el push entra
    // duplicado o hay recomposición (antes: dos FOLLOW_REQUESTS en la pila → al
    // pulsar atrás solo se quitaba una copia y parecía que "atrás no funcionaba").
    // El destino raíz (Schools) siempre queda debajo, así que atrás vuelve a él.
    androidx.compose.runtime.LaunchedEffect(deepLink) {
        if (deepLink != null) {
            val opts: androidx.navigation.NavOptionsBuilder.() -> Unit = { launchSingleTop = true }
            when (deepLink.targetType) {
                "school", "school_detail" ->
                    deepLink.targetId?.let { navController.navigate(Routes.schoolDetail(it), opts) }
                "user"        -> deepLink.targetId?.let { navController.navigate(Routes.publicProfile(it), opts) }
                "chat", "message" -> deepLink.targetId?.let { navController.navigate(Routes.chat(it), opts) }
                "submission", "contribution" -> navController.navigate(Routes.MY_SUBMISSIONS, opts)
                "notifications" -> navController.navigate(Routes.NOTIFICATIONS, opts)
                "follow_request" -> navController.navigate(Routes.FOLLOW_REQUESTS, opts)
                // Alerta del finde: targetId = ids CSV de las escuelas comparadas
                "compare" -> deepLink.targetId?.let { navController.navigate("compare/$it", opts) }
            }
            onDeepLinkConsumed()
        }
    }

    val showBottomBar = currentRoute in mainTabs.map { it.route }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.background,
                    tonalElevation = 0.dp
                ) {
                    mainTabs.forEach { tab ->
                        val selected = currentRoute == tab.route
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                if (!selected) {
                                    navController.navigate(tab.route) {
                                        popUpTo(navController.graph.startDestinationId) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label, style = MaterialTheme.typography.labelMedium) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor   = MaterialTheme.colorScheme.primary,
                                selectedTextColor   = MaterialTheme.colorScheme.primary,
                                indicatorColor      = MaterialTheme.colorScheme.primaryContainer,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        androidx.compose.foundation.layout.Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            com.meteomontana.android.ui.components.NetworkBanner()
            NavHost(
                navController = navController,
                startDestination = Tab.Schools.route,
                modifier = Modifier.weight(1f)
            ) {
            composable(Tab.Weather.route) {
                WeatherScreen(
                    onDayClick = { schoolId, lat, lon, idx ->
                        if (schoolId != null) navController.navigate(Routes.dayDetail(schoolId, idx))
                        else navController.navigate(Routes.dayDetailByLocation(lat, lon, idx))
                    }
                )
            }
            composable(Tab.Schools.route) {
                SchoolListScreen(
                    onSchoolClick = { id -> navController.navigate(Routes.schoolDetail(id)) },
                    onProfileClick = { navController.navigate(Routes.PROFILE) },
                    onSubmitSchool = { navController.navigate(Routes.SUBMIT_SCHOOL) },
                    onSearchUsers = { navController.navigate(Routes.SEARCH_USERS) },
                    onNotifications = { navController.navigate(Routes.NOTIFICATIONS) },
                    onChats = { navController.navigate(Routes.CHAT_LIST) },
                    onCompare = { ids -> navController.navigate(Routes.compare(ids)) }
                )
            }
            composable(Tab.Radar.route) { RadarScreen() }

            composable(
                route = Routes.SCHOOL_DETAIL,
                arguments = listOf(
                    navArgument("schoolId") { type = NavType.StringType },
                    navArgument("via") { type = NavType.StringType; nullable = true; defaultValue = null },
                    navArgument("viaId") { type = NavType.StringType; nullable = true; defaultValue = null }
                )
            ) {
                val schoolId = it.arguments?.getString("schoolId") ?: ""
                SchoolDetailScreen(
                    onBack = { navController.popBackStack() },
                    onOpenBlock = { blockId -> navController.navigate(Routes.topoEditor(blockId)) },
                    onMyProposals = { navController.navigate(Routes.MY_SUBMISSIONS) },
                    onDayClick = { idx -> navController.navigate(Routes.dayDetail(schoolId, idx)) }
                )
            }
            composable(
                route = Routes.DAY_DETAIL,
                arguments = listOf(
                    navArgument("schoolId") { type = NavType.StringType },
                    navArgument("dayIndex") { type = NavType.StringType }
                )
            ) {
                DayDetailScreen(onBack = { navController.popBackStack() })
            }
            composable(
                route = Routes.DAY_DETAIL_BY_LOCATION,
                arguments = listOf(
                    navArgument("lat") { type = NavType.StringType },
                    navArgument("lon") { type = NavType.StringType },
                    navArgument("dayIndex") { type = NavType.StringType }
                )
            ) {
                DayDetailScreen(onBack = { navController.popBackStack() })
            }

            composable(Routes.PROFILE) {
                ProfileScreen(
                    onBack = { navController.popBackStack() },
                    onEdit = { navController.navigate(Routes.EDIT_PROFILE) },
                    onSubmissions = { navController.navigate(Routes.MY_SUBMISSIONS) },
                    onAdmin = { navController.navigate(Routes.ADMIN) },
                    onSavedSchools = { navController.navigate(Routes.SAVED_SCHOOLS) },
                    onWeekendAlert = { navController.navigate(Routes.WEEKEND_ALERT) },
                    onOpenFollowers = {
                        com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid?.let { uid ->
                            navController.navigate(Routes.followList(uid, "followers"))
                        }
                    },
                    onOpenFollowing = {
                        com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid?.let { uid ->
                            navController.navigate(Routes.followList(uid, "following"))
                        }
                    },
                    onOpenFollowRequests = { navController.navigate(Routes.FOLLOW_REQUESTS) },
                    onOpenSchoolEntries = { schoolName ->
                        navController.navigate(Routes.journalEntries("school:$schoolName"))
                    },
                    onOpenBoulders = { navController.navigate(Routes.journalEntries("discipline:BOULDER")) },
                    onOpenRoutes = { navController.navigate(Routes.journalEntries("discipline:ROUTE")) },
                    onOpenAllSchools = { navController.navigate(Routes.journalSchools(null)) },
                    onOpenMaxGrade = { navController.navigate(Routes.journalEntries("grade-max")) }
                )
            }
            composable(
                route = Routes.JOURNAL_ENTRIES,
                arguments = listOf(
                    navArgument("filter") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                    navArgument("uid") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    }
                )
            ) {
                JournalEntriesScreen(
                    onBack = { navController.popBackStack() },
                    onOpenSchool = { id, via, viaId -> navController.navigate(Routes.schoolDetail(id, via, viaId)) }
                )
            }
            composable(
                route = Routes.JOURNAL_SCHOOLS,
                arguments = listOf(navArgument("uid") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                })
            ) { backStack ->
                val uid = backStack.arguments?.getString("uid")?.takeIf { it.isNotBlank() }
                JournalSchoolsScreen(
                    onBack = { navController.popBackStack() },
                    onSchoolClick = { schoolName ->
                        navController.navigate(Routes.journalEntries(filter = "school:$schoolName", uid = uid))
                    }
                )
            }
            composable(Routes.ADMIN) {
                AdminScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.EDIT_PROFILE) {
                EditProfileScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.COMPARE) {
                com.meteomontana.android.ui.screens.compare.CompareScreen(
                    onBack = { navController.popBackStack() },
                    onSchoolDetail = { id -> navController.navigate(Routes.schoolDetail(id)) }
                )
            }
            composable(Routes.WEEKEND_ALERT) {
                com.meteomontana.android.ui.screens.profile.WeekendAlertScreen(
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Routes.SAVED_SCHOOLS) {
                SavedSchoolsScreen(
                    onBack = { navController.popBackStack() },
                    onOpen = { id -> navController.navigate(Routes.schoolDetail(id)) }
                )
            }
            composable(Routes.MY_SUBMISSIONS) {
                MySubmissionsScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.SUBMIT_SCHOOL) {
                SubmitSchoolScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.SEARCH_USERS) {
                SearchUsersScreen(
                    onBack = { navController.popBackStack() },
                    onUserClick = { uid -> navController.navigate(Routes.publicProfile(uid)) }
                )
            }
            composable(Routes.NOTIFICATIONS) {
                NotificationsScreen(
                    onBack = { navController.popBackStack() },
                    onOpenUser = { uid -> navController.navigate(Routes.publicProfile(uid)) },
                    onOpenSchool = { id -> navController.navigate(Routes.schoolDetail(id)) },
                    onOpenSubmissions = { navController.navigate(Routes.MY_SUBMISSIONS) },
                    onOpenChat = { uid -> navController.navigate(Routes.chat(uid)) },
                    onOpenFollowRequests = { navController.navigate(Routes.FOLLOW_REQUESTS) }
                )
            }
            composable(
                route = Routes.PUBLIC_PROFILE,
                arguments = listOf(navArgument("uid") { type = NavType.StringType })
            ) {
                PublicProfileScreen(
                    onBack = { navController.popBackStack() },
                    onFollowersClick = { uid ->
                        navController.navigate(Routes.followList(uid, "followers"))
                    },
                    onFollowingClick = { uid ->
                        navController.navigate(Routes.followList(uid, "following"))
                    },
                    onOpenChat = { uid -> navController.navigate(Routes.chat(uid)) },
                    onOpenBoulders = { uid ->
                        navController.navigate(Routes.journalEntries(filter = "discipline:BOULDER", uid = uid))
                    },
                    onOpenRoutes = { uid ->
                        navController.navigate(Routes.journalEntries(filter = "discipline:ROUTE", uid = uid))
                    },
                    onOpenMaxGrade = { uid ->
                        navController.navigate(Routes.journalEntries(filter = "grade-max", uid = uid))
                    },
                    onOpenSchools = { uid ->
                        navController.navigate(Routes.journalSchools(uid))
                    },
                    onOpenSchoolEntries = { uid, schoolName ->
                        navController.navigate(Routes.journalEntries(filter = "school:$schoolName", uid = uid))
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
                    onBack = { navController.popBackStack() },
                    onUserClick = { uid -> navController.navigate(Routes.publicProfile(uid)) }
                )
            }
            composable(Routes.FOLLOW_REQUESTS) {
                FollowRequestsScreen(
                    onBack = { navController.popBackStack() },
                    onUserClick = { uid -> navController.navigate(Routes.publicProfile(uid)) }
                )
            }
            composable(Routes.CHAT_LIST) {
                ChatListScreen(
                    onBack = { navController.popBackStack() },
                    onOpenChat = { uid -> navController.navigate(Routes.chat(uid)) }
                )
            }
            composable(
                route = Routes.CHAT,
                arguments = listOf(navArgument("uid") { type = NavType.StringType })
            ) {
                ChatScreen(
                    onBack = { navController.popBackStack() },
                    onOpenProfile = { uid -> navController.navigate(Routes.publicProfile(uid)) }
                )
            }
            composable(
                route = Routes.TOPO_EDITOR,
                arguments = listOf(navArgument("blockId") { type = NavType.StringType })
            ) {
                TopoEditorScreen(onBack = { navController.popBackStack() })
            }
        }
        }
    }
}
