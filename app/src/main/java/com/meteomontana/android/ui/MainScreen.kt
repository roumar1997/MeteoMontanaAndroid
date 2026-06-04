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
import com.meteomontana.android.ui.screens.detail.SchoolDetailScreen
import com.meteomontana.android.ui.screens.notifications.NotificationsScreen
import com.meteomontana.android.ui.screens.profile.EditProfileScreen
import com.meteomontana.android.ui.screens.profile.JournalEntriesScreen
import com.meteomontana.android.ui.screens.profile.ProfileScreen
import com.meteomontana.android.ui.screens.radar.RadarScreen
import com.meteomontana.android.ui.screens.schools.SchoolListScreen
import com.meteomontana.android.ui.screens.submissions.MySubmissionsScreen
import com.meteomontana.android.ui.screens.submissions.SubmitSchoolScreen
import com.meteomontana.android.ui.screens.topo.TopoEditorScreen
import com.meteomontana.android.ui.screens.users.FollowListScreen
import com.meteomontana.android.ui.screens.users.PublicProfileScreen
import com.meteomontana.android.ui.screens.users.SearchUsersScreen
import com.meteomontana.android.ui.screens.weather.WeatherScreen

@Composable
fun MainScreen() {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route

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
        NavHost(
            navController = navController,
            startDestination = Tab.Schools.route,
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
            composable(Tab.Weather.route) { WeatherScreen() }
            composable(Tab.Schools.route) {
                SchoolListScreen(
                    onSchoolClick = { id -> navController.navigate(Routes.schoolDetail(id)) },
                    onProfileClick = { navController.navigate(Routes.PROFILE) },
                    onSubmitSchool = { navController.navigate(Routes.SUBMIT_SCHOOL) },
                    onSearchUsers = { navController.navigate(Routes.SEARCH_USERS) },
                    onNotifications = { navController.navigate(Routes.NOTIFICATIONS) },
                    onChats = { navController.navigate(Routes.CHAT_LIST) }
                )
            }
            composable(Tab.Radar.route) { RadarScreen() }

            composable(
                route = Routes.SCHOOL_DETAIL,
                arguments = listOf(navArgument("schoolId") { type = NavType.StringType })
            ) {
                SchoolDetailScreen(
                    onBack = { navController.popBackStack() },
                    onOpenBlock = { blockId -> navController.navigate(Routes.topoEditor(blockId)) }
                )
            }

            composable(Routes.PROFILE) {
                ProfileScreen(
                    onBack = { navController.popBackStack() },
                    onEdit = { navController.navigate(Routes.EDIT_PROFILE) },
                    onSubmissions = { navController.navigate(Routes.MY_SUBMISSIONS) },
                    onAdmin = { navController.navigate(Routes.ADMIN) },
                    onOpenSchoolEntries = { schoolName ->
                        navController.navigate(Routes.journalEntries("school:$schoolName"))
                    },
                    onOpenAllBlocks = { navController.navigate(Routes.journalEntries(null)) },
                    onOpenMaxGrade = { navController.navigate(Routes.journalEntries("grade-max")) }
                )
            }
            composable(
                route = Routes.JOURNAL_ENTRIES,
                arguments = listOf(navArgument("filter") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                })
            ) {
                JournalEntriesScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.ADMIN) {
                AdminScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.EDIT_PROFILE) {
                EditProfileScreen(onBack = { navController.popBackStack() })
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
                    onOpenUser = { uid -> navController.navigate(Routes.publicProfile(uid)) }
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
                    onOpenChat = { uid -> navController.navigate(Routes.chat(uid)) }
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
                ChatScreen(onBack = { navController.popBackStack() })
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
