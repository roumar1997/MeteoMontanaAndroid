@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.meteomontana.android.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.meteomontana.android.ui.screens.chat.GroupChatScreen
import com.meteomontana.android.ui.screens.chat.NewGroupScreen
import com.meteomontana.android.ui.screens.day.DayDetailScreen
import com.meteomontana.android.ui.screens.detail.SchoolDetailScreen
import com.meteomontana.android.ui.screens.saved.SavedSchoolsScreen
import com.meteomontana.android.ui.screens.notifications.NotificationsScreen
import com.meteomontana.android.ui.screens.profile.EditProfileScreen
import com.meteomontana.android.ui.screens.profile.JournalEntriesScreen
import com.meteomontana.android.ui.screens.profile.JournalSchoolsScreen
import com.meteomontana.android.ui.screens.profile.JournalSectorsScreen
import com.meteomontana.android.ui.screens.profile.ProjectsScreen
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
import com.meteomontana.android.ui.screens.meetups.CreateMeetupScreen
import com.meteomontana.android.ui.screens.meetups.MeetupAlertScreen
import com.meteomontana.android.ui.screens.meetups.MeetupDetailScreen
import com.meteomontana.android.ui.screens.meetups.MeetupsScreen
import com.meteomontana.android.ui.screens.weather.WeatherScreen


/**
 * Conmutador TIEMPO ⇄ RADAR de la primera pestaña (segmented estilo Cumbre:
 * cápsula con borde Rule, segmento activo Terra).
 */
// Overlay de detalle DENTRO del contenido del Scaffold (la navbar queda
// visible debajo) + su NavHost. Reparto del antiguo MainScreen.kt de 905 lineas.

@Composable
internal fun SheetOverlay(
    sheetVisible: Boolean,
    onHide: () -> Unit,
    pendingSheetRoute: String?,
    onPendingConsumed: () -> Unit,
    sheetNav: androidx.navigation.NavHostController,
    navController: androidx.navigation.NavHostController,
    openSheet: (String) -> Unit,
    openFullScreen: (String) -> Unit,
    popSheetOrDismiss: () -> Unit,
    bottomInset: androidx.compose.ui.unit.Dp = 0.dp
) {
    AnimatedVisibility(
        visible = sheetVisible,
        enter = slideInHorizontally(tween(280)) { it } + fadeIn(tween(280)),
        exit = slideOutHorizontally(tween(280)) { it } + fadeOut(tween(280)),
        modifier = Modifier.fillMaxSize().zIndex(2f)
    ) {
        Box(
            Modifier.fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Navega al destino pendiente una vez el NavHost interno está
            // compuesto (grafo registrado).
            androidx.compose.runtime.LaunchedEffect(pendingSheetRoute) {
                val r = pendingSheetRoute
                if (r != null) {
                    sheetNav.navigate(r) {
                        popUpTo(SHEET_ROOT) { inclusive = false }
                        launchSingleTop = true
                    }
                    onPendingConsumed()
                }
            }
            // Auto-cierre: si el NavHost interno vuelve a SHEET_ROOT (back del
            // sistema u otro pop) y NO hay ruta pendiente, ocultamos el overlay.
            val sheetEntry by sheetNav.currentBackStackEntryAsState()
            androidx.compose.runtime.LaunchedEffect(sheetEntry) {
                if (sheetEntry?.destination?.route == SHEET_ROOT && pendingSheetRoute == null) {
                    onHide()
                }
            }
            SheetNavHost(
                sheetNav = sheetNav,
                navController = navController,
                openSheet = openSheet,
                openFullScreen = openFullScreen,
                popSheetOrDismiss = popSheetOrDismiss,
                bottomInset = bottomInset
            )
        }
    }
}

/**
 * NavHost interno del overlay (antes vivía dentro del ModalBottomSheet). Desliza
 * lateralmente entre pantallas (Perfil, Chats, detalle de escuela, etc.) como iOS.
 * Se extrae a una función aparte para no anidar en exceso dentro de MainScreen.
 */
@Composable
private fun SheetNavHost(
    sheetNav: androidx.navigation.NavHostController,
    navController: androidx.navigation.NavHostController,
    openSheet: (String) -> Unit,
    openFullScreen: (String) -> Unit,
    popSheetOrDismiss: () -> Unit,
    bottomInset: androidx.compose.ui.unit.Dp = 0.dp
) {
                NavHost(
                    navController = sheetNav,
                    startDestination = SHEET_ROOT,
                    modifier = Modifier.fillMaxSize(),
                    // Deslizado lateral (push) entre pantallas del sheet, como iOS.
                    enterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(280)) },
                    exitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(280)) },
                    popEnterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(280)) },
                    popExitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(280)) }
                ) {
                    // Detalle de escuela: ahora vive en el overlay (con la barra
                    // de pestañas visible), no como pantalla completa aparte.
                    composable(
                        route = Routes.SCHOOL_DETAIL,
                        arguments = listOf(
                            navArgument("schoolId") { type = NavType.StringType },
                            navArgument("via") { type = NavType.StringType; nullable = true; defaultValue = null },
                            navArgument("viaId") { type = NavType.StringType; nullable = true; defaultValue = null },
                            navArgument("blockId") { type = NavType.StringType; nullable = true; defaultValue = null }
                        )
                    ) { entry ->
                        val schoolId = entry.arguments?.getString("schoolId") ?: ""
                        SchoolDetailScreen(
                            onBack = popSheetOrDismiss,
                            // El editor de topos SÍ es pantalla completa (sin barra):
                            // navegamos en el navController principal sin cerrar el
                            // overlay, para volver al detalle al terminar.
                            onOpenBlock = { blockId -> navController.navigate(Routes.topoEditor(blockId)) },
                            onMyProposals = { sheetNav.navigate(Routes.MY_SUBMISSIONS) },
                            onDayClick = { idx -> sheetNav.navigate(Routes.dayDetail(schoolId, idx)) }
                        )
                    }
                    composable(
                        SHEET_ROOT,
                        enterTransition = { EnterTransition.None },
                        exitTransition = { ExitTransition.None },
                        popEnterTransition = { EnterTransition.None },
                        popExitTransition = { ExitTransition.None }
                    ) { Box(Modifier.fillMaxSize()) }

                    composable(Routes.PROFILE) {
                        ProfileScreen(
                            onBack = popSheetOrDismiss,
                            onEdit = { sheetNav.navigate(Routes.EDIT_PROFILE) },
                            onSubmissions = { sheetNav.navigate(Routes.MY_SUBMISSIONS) },
                            onAdmin = { openFullScreen(Routes.ADMIN) },
                            onSavedSchools = { sheetNav.navigate(Routes.SAVED_SCHOOLS) },
                            onWeekendAlert = { sheetNav.navigate(Routes.WEEKEND_ALERT) },
                            onOpenFollowers = {
                                com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid?.let { uid ->
                                    sheetNav.navigate(Routes.followList(uid, "followers"))
                                }
                            },
                            onOpenFollowing = {
                                com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid?.let { uid ->
                                    sheetNav.navigate(Routes.followList(uid, "following"))
                                }
                            },
                            onOpenFollowRequests = { sheetNav.navigate(Routes.FOLLOW_REQUESTS) },
                            onOpenSchoolEntries = { schoolName -> sheetNav.navigate(Routes.journalSectors(schoolName)) },
                            onOpenBoulders = { sheetNav.navigate(Routes.journalEntries("discipline:BOULDER")) },
                            onOpenRoutes = { sheetNav.navigate(Routes.journalEntries("discipline:ROUTE")) },
                            onOpenAllSchools = { sheetNav.navigate(Routes.journalSchools(null)) },
                            onOpenMaxGrade = { sheetNav.navigate(Routes.journalEntries("grade-max")) },
                            onOpenProjects = { sheetNav.navigate(Routes.projects(null)) },
                            onOpenMyPosts = { sheetNav.navigate(Routes.MY_POSTS) }
                        )
                    }
                    // "Mis publicaciones": feed propio en pantalla dedicada.
                    composable(Routes.MY_POSTS) {
                        com.meteomontana.android.ui.screens.community.MyPostsScreen(
                            onBack = popSheetOrDismiss,
                            onOpenUser = { uid -> sheetNav.navigate(Routes.publicProfile(uid)) },
                            onOpenSchool = { schoolId, lineId, lineName, blockId ->
                                sheetNav.navigate(Routes.schoolDetail(schoolId, via = lineName, viaId = lineId, blockId = blockId))
                            }
                        )
                    }
                    composable(Routes.EDIT_PROFILE) {
                        EditProfileScreen(onBack = popSheetOrDismiss)
                    }
                    composable(Routes.MY_SUBMISSIONS) {
                        MySubmissionsScreen(onBack = popSheetOrDismiss)
                    }
                    composable(Routes.SUBMIT_SCHOOL) {
                        SubmitSchoolScreen(onBack = popSheetOrDismiss)
                    }
                    composable(Routes.SAVED_SCHOOLS) {
                        SavedSchoolsScreen(
                            onBack = popSheetOrDismiss,
                            onOpen = { id -> sheetNav.navigate(Routes.schoolDetail(id)) }
                        )
                    }
                    composable(Routes.WEEKEND_ALERT) {
                        com.meteomontana.android.ui.screens.profile.WeekendAlertScreen(onBack = popSheetOrDismiss)
                    }
                    composable(Routes.COMPARE) {
                        com.meteomontana.android.ui.screens.compare.CompareScreen(
                            onBack = popSheetOrDismiss,
                            onSchoolDetail = { id -> sheetNav.navigate(Routes.schoolDetail(id)) }
                        )
                    }

                    composable(
                        route = Routes.DAY_DETAIL,
                        arguments = listOf(
                            navArgument("schoolId") { type = NavType.StringType },
                            navArgument("dayIndex") { type = NavType.StringType }
                        )
                    ) { DayDetailScreen(onBack = popSheetOrDismiss) }
                    composable(
                        route = Routes.DAY_DETAIL_BY_LOCATION,
                        arguments = listOf(
                            navArgument("lat") { type = NavType.StringType },
                            navArgument("lon") { type = NavType.StringType },
                            navArgument("dayIndex") { type = NavType.StringType }
                        )
                    ) { DayDetailScreen(onBack = popSheetOrDismiss) }

                    composable(
                        route = Routes.JOURNAL_ENTRIES,
                        arguments = listOf(
                            navArgument("filter") { type = NavType.StringType; nullable = true; defaultValue = null },
                            navArgument("uid") { type = NavType.StringType; nullable = true; defaultValue = null }
                        )
                    ) {
                        JournalEntriesScreen(
                            onBack = popSheetOrDismiss,
                            onOpenSchool = { id, via, viaId -> sheetNav.navigate(Routes.schoolDetail(id, via, viaId)) }
                        )
                    }
                    composable(
                        route = Routes.JOURNAL_SCHOOLS,
                        arguments = listOf(navArgument("uid") { type = NavType.StringType; nullable = true; defaultValue = null })
                    ) { entry ->
                        val uid = entry.arguments?.getString("uid")?.takeIf { it.isNotBlank() }
                        JournalSchoolsScreen(
                            onBack = popSheetOrDismiss,
                            // Antes iba directo al listado plano; ahora pasa por
                            // "sectores" (JOURNAL_SECTORS) para no mezclar todos los
                            // bloques/vías de la escuela de golpe.
                            onSchoolClick = { schoolName ->
                                sheetNav.navigate(Routes.journalSectors(schoolName, uid))
                            }
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
                            onBack = popSheetOrDismiss,
                            onSectorClick = { schoolName, sectorName ->
                                sheetNav.navigate(
                                    Routes.journalEntries(
                                        filter = "school:$schoolName|sector:$sectorName",
                                        uid = uid
                                    )
                                )
                            },
                            onOpenSchool = { id, via, viaId -> sheetNav.navigate(Routes.schoolDetail(id, via, viaId)) }
                        )
                    }

                    composable(
                        route = Routes.PROJECTS,
                        arguments = listOf(navArgument("uid") { type = NavType.StringType; nullable = true; defaultValue = null })
                    ) { entry ->
                        val uid = entry.arguments?.getString("uid")?.takeIf { it.isNotBlank() }
                        ProjectsScreen(
                            onBack = popSheetOrDismiss,
                            onOpenBoulders = { sheetNav.navigate(Routes.journalEntries(filter = "project:BOULDER", uid = uid)) },
                            onOpenRoutes = { sheetNav.navigate(Routes.journalEntries(filter = "project:ROUTE", uid = uid)) }
                        )
                    }

                    composable(
                        route = Routes.PUBLIC_PROFILE,
                        arguments = listOf(navArgument("uid") { type = NavType.StringType })
                    ) {
                        PublicProfileScreen(
                            onBack = popSheetOrDismiss,
                            onFollowersClick = { uid -> sheetNav.navigate(Routes.followList(uid, "followers")) },
                            onFollowingClick = { uid -> sheetNav.navigate(Routes.followList(uid, "following")) },
                            onOpenChat = { uid -> sheetNav.navigate(Routes.chat(uid)) },
                            onOpenBoulders = { uid -> sheetNav.navigate(Routes.journalEntries(filter = "discipline:BOULDER", uid = uid)) },
                            onOpenRoutes = { uid -> sheetNav.navigate(Routes.journalEntries(filter = "discipline:ROUTE", uid = uid)) },
                            onOpenMaxGrade = { uid -> sheetNav.navigate(Routes.journalEntries(filter = "grade-max", uid = uid)) },
                            onOpenSchools = { uid -> sheetNav.navigate(Routes.journalSchools(uid)) },
                            onOpenSchoolEntries = { uid, schoolName -> sheetNav.navigate(Routes.journalSectors(schoolName, uid)) },
                            onOpenProjects = { uid -> sheetNav.navigate(Routes.projects(uid)) },
                            // Sección Publicaciones (feed) del perfil:
                            onOpenUserProfile = { uid -> sheetNav.navigate(Routes.publicProfile(uid)) },
                            onOpenFeedSchool = { schoolId, lineId, lineName, blockId ->
                                sheetNav.navigate(Routes.schoolDetail(schoolId, via = lineName, viaId = lineId, blockId = blockId))
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
                            onBack = popSheetOrDismiss,
                            onUserClick = { uid -> sheetNav.navigate(Routes.publicProfile(uid)) }
                        )
                    }
                    composable(Routes.FOLLOW_REQUESTS) {
                        FollowRequestsScreen(
                            onBack = popSheetOrDismiss,
                            onUserClick = { uid -> sheetNav.navigate(Routes.publicProfile(uid)) }
                        )
                    }

                    composable(Routes.CHAT_LIST) {
                        ChatListScreen(
                            onBack = popSheetOrDismiss,
                            onOpenChat = { uid -> sheetNav.navigate(Routes.chat(uid)) },
                            onOpenGroup = { convId -> sheetNav.navigate(Routes.groupChat(convId)) },
                            onNewGroup = { sheetNav.navigate(Routes.NEW_GROUP) }
                        )
                    }
                    composable(
                        route = Routes.CHAT,
                        arguments = listOf(navArgument("uid") { type = NavType.StringType })
                    ) {
                        ChatScreen(
                            onBack = popSheetOrDismiss,
                            onOpenProfile = { uid -> sheetNav.navigate(Routes.publicProfile(uid)) },
                            bottomInset = bottomInset
                        )
                    }
                    composable(Routes.NEW_GROUP) {
                        NewGroupScreen(
                            onBack = popSheetOrDismiss,
                            onCreated = { convId ->
                                sheetNav.popBackStack()
                                sheetNav.navigate(Routes.groupChat(convId))
                            }
                        )
                    }
                    composable(
                        route = Routes.GROUP_CHAT,
                        arguments = listOf(navArgument("convId") { type = NavType.StringType })
                    ) {
                        GroupChatScreen(
                            onBack = popSheetOrDismiss,
                            onOpenMeetup = { meetupId -> sheetNav.navigate(Routes.meetupDetail(meetupId)) },
                            bottomInset = bottomInset
                        )
                    }

                    composable(Routes.NOTIFICATIONS) {
                        NotificationsScreen(
                            onBack = popSheetOrDismiss,
                            onOpenUser = { uid -> sheetNav.navigate(Routes.publicProfile(uid)) },
                            onOpenSchool = { id -> sheetNav.navigate(Routes.schoolDetail(id)) },
                            onOpenSubmissions = { sheetNav.navigate(Routes.MY_SUBMISSIONS) },
                            onOpenChat = { uid -> sheetNav.navigate(Routes.chat(uid)) },
                            onOpenFollowRequests = { sheetNav.navigate(Routes.FOLLOW_REQUESTS) },
                            onOpenFeedPost = { postId -> sheetNav.navigate(Routes.feedPost(postId)) }
                        )
                    }
                    // Detalle de un post del feed Comunidad (push/campanita).
                    composable(
                        route = Routes.FEED_POST,
                        arguments = listOf(navArgument("postId") { type = NavType.StringType })
                    ) {
                        com.meteomontana.android.ui.screens.community.FeedPostDetailScreen(
                            onBack = popSheetOrDismiss,
                            onOpenUser = { uid -> sheetNav.navigate(Routes.publicProfile(uid)) },
                            onOpenSchool = { schoolId, lineId, lineName, blockId ->
                                sheetNav.navigate(Routes.schoolDetail(schoolId, via = lineName, viaId = lineId, blockId = blockId))
                            },
                            bottomInset = bottomInset
                        )
                    }
                    composable(Routes.SEARCH_USERS) {
                        SearchUsersScreen(
                            onBack = popSheetOrDismiss,
                            onUserClick = { uid -> sheetNav.navigate(Routes.publicProfile(uid)) }
                        )
                    }
                    // ── Quedadas (sheets) ──
                    composable(
                        route = Routes.MEETUP_DETAIL,
                        arguments = listOf(navArgument("meetupId") { type = NavType.StringType })
                    ) { entry ->
                        val meetupId = entry.arguments?.getString("meetupId") ?: ""
                        MeetupDetailScreen(
                            meetupId = meetupId,
                            onBack = popSheetOrDismiss,
                            onOpenChat = { convId -> sheetNav.navigate(Routes.groupChat(convId)) },
                            onOpenSchool = { id -> sheetNav.navigate(Routes.schoolDetail(id)) },
                            onOpenProfile = { uid -> sheetNav.navigate(Routes.publicProfile(uid)) }
                        )
                    }
                    composable(Routes.CREATE_MEETUP) {
                        CreateMeetupScreen(
                            onBack = popSheetOrDismiss,
                            onCreated = { id ->
                                sheetNav.popBackStack()
                                sheetNav.navigate(Routes.meetupDetail(id))
                            }
                        )
                    }
                    composable(Routes.MEETUP_ALERT) {
                        MeetupAlertScreen(onBack = popSheetOrDismiss)
                    }
                }
}

