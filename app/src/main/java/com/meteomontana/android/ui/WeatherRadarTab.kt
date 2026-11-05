package com.meteomontana.android.ui

import androidx.compose.ui.res.stringResource
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.zIndex
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import com.meteomontana.android.ui.guide.guideTarget
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
import com.meteomontana.android.ui.components.CumbreCapsuleShape
import com.meteomontana.android.ui.components.LocalChromeTreatment
import com.meteomontana.android.ui.components.cumbreBackdrop
import com.meteomontana.android.ui.components.cumbreChromeSurface
import com.meteomontana.android.ui.theme.ChromeTreatment
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import com.meteomontana.android.R

/** Primera pestaña: la capa TIEMPO y la capa RADAR (mapa a pantalla completa), con su conmutador. */
@Composable
internal fun WeatherRadarTab(
    weatherShowsRadar: Boolean,
    radarVisited: Boolean,
    /** La pestaña está a la vista (seleccionada y sin nada abierto encima). */
    weatherVisible: Boolean,
    onShowRadar: (Boolean) -> Unit,
    openSheet: (String) -> Unit
) {
    Box(Modifier.fillMaxSize()) {
        // ── Capa TIEMPO ──
        Box(
            Modifier.fillMaxSize()
                .zIndex(if (!weatherShowsRadar) 1f else 0f)
                .graphicsLayer { alpha = if (!weatherShowsRadar) 1f else 0f }
        ) {
            com.meteomontana.android.ui.guide.GuideHost("weather") {
            androidx.compose.foundation.layout.Column(Modifier.fillMaxSize()) {
                WeatherRadarToggle(
                    showRadar = false,
                    onSelect = { onShowRadar(it) },
                    modifier = Modifier.padding(top = 8.dp).guideTarget("weather.toggle")
                )
                WeatherScreen(
                    onDayClick = { schoolId, lat, lon, idx ->
                        if (schoolId != null) openSheet(Routes.dayDetail(schoolId, idx))
                        else openSheet(Routes.dayDetailByLocation(lat, lon, idx))
                    },
                    visible = weatherVisible && !weatherShowsRadar
                )
            }
            }
        }
        // ── Capa RADAR (mapa a pantalla completa) ──
        if (radarVisited) {
            Box(
                Modifier.fillMaxSize()
                    .zIndex(if (weatherShowsRadar) 1f else 0f)
                    .graphicsLayer { alpha = if (weatherShowsRadar) 1f else 0f }
            ) {
                RadarScreen(onSchoolDetail = { id ->
                    openSheet(Routes.schoolDetail(id))
                })
                WeatherRadarToggle(
                    showRadar = true,
                    onSelect = { onShowRadar(it) },
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .statusBarsPadding()
                        .padding(top = 8.dp)
                        .zIndex(2f)
                )
            }
        }
    }
}
