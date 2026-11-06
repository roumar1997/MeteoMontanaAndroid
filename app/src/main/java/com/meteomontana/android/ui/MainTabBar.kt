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

/** Cápsula de pestañas de abajo (estilo iOS): los cinco rótulos siempre, repartidos a partes iguales. */
@Composable
internal fun MainTabBar(selectedTab: String, onTabClick: (tab: com.meteomontana.android.navigation.Tab, alreadySelected: Boolean) -> Unit) {
        // Cápsula flotante (estilo iOS) en vez de la barra plana a todo el ancho.
        Box(
            modifier = Modifier.fillMaxWidth()
                .navigationBarsPadding()
                // Márgenes laterales: la cápsula ocupa el ancho MENOS
                // esto, igual que en iOS. Antes se encogía a lo que
                // ocupaban los iconos.
                .padding(horizontal = 12.dp)
                .padding(bottom = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = Modifier
                    // A todo el ancho disponible: es lo que permite
                    // repartir las cinco pestañas por igual con weight,
                    // sea cual sea el móvil.
                    .fillMaxWidth()
                    // Fondo + borde del armazón en UNA sola llamada: según
                    // el tratamiento activo será color liso, esmerilado o
                    // esmerilado con canto de luz. La barra no sabe cuál.
                    .cumbreChromeSurface(CumbreCapsuleShape)
                    .padding(horizontal = 6.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                mainTabs.forEach { tab ->
                    val selected = selectedTab == tab.route
                    val tint = if (selected) MaterialTheme.colorScheme.primary
                               else MaterialTheme.colorScheme.onSurfaceVariant
                    // LOS CINCO RÓTULOS SIEMPRE, como en iOS: icono arriba
                    // y nombre debajo. Antes solo se etiquetaba el activo
                    // porque en horizontal no caben cinco nombres; en
                    // vertical sí, y así sabes a dónde vas antes de pulsar.
                    //
                    // ADAPTABLE: `weight(1f)` reparte el ancho a partes
                    // iguales sea cual sea la pantalla, y el rótulo se
                    // recorta con puntos suspensivos si el móvil es muy
                    // estrecho. Ni un ancho fijo, que es lo que descuadra
                    // al cambiar de dispositivo.
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(24.dp))
                            .background(
                                if (selected) MaterialTheme.colorScheme.primaryContainer
                                else androidx.compose.ui.graphics.Color.Transparent
                            )
                            .clickable {
                                // Con el overlay abierto, pulsar una
                                // pestaña lo cierra y cambia de tab.
                                onTabClick(tab, selected)
                            }
                            .padding(horizontal = 2.dp, vertical = 7.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Icon(tab.icon, contentDescription = stringResource(tab.labelRes),
                            tint = tint, modifier = Modifier.size(20.dp))
                        Text(
                            stringResource(tab.labelRes),
                            style = MaterialTheme.typography.labelMedium,
                            color = tint,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
}
