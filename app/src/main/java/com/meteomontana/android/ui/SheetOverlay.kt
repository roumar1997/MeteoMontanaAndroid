@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.meteomontana.android.ui

import com.meteomontana.android.ui.components.blockTouchesBelow
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
import com.meteomontana.android.ui.theme.CumbreMotion
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
import com.meteomontana.android.ui.navigation.Destination
import com.meteomontana.android.ui.navigation.LocalNavigator
import com.meteomontana.android.ui.navigation.SheetNavigator
import com.meteomontana.android.ui.screens.chat.chatRoutes
import com.meteomontana.android.ui.screens.community.communityRoutes
import com.meteomontana.android.ui.screens.compare.compareRoutes
import com.meteomontana.android.ui.screens.day.dayRoutes
import com.meteomontana.android.ui.screens.detail.schoolRoutes
import com.meteomontana.android.ui.screens.gyms.LocalOpenGym
import com.meteomontana.android.ui.screens.gyms.gymRoutes
import com.meteomontana.android.ui.screens.meetups.meetupRoutes
import com.meteomontana.android.ui.screens.notifications.notificationRoutes
import com.meteomontana.android.ui.screens.profile.profileRoutes
import com.meteomontana.android.ui.screens.saved.savedRoutes
import com.meteomontana.android.ui.screens.submissions.submissionRoutes
import com.meteomontana.android.ui.screens.users.userRoutes


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
    popSheetOrDismiss: () -> Unit,
    bottomInset: androidx.compose.ui.unit.Dp = 0.dp
) {
    AnimatedVisibility(
        visible = sheetVisible,
        enter = slideInHorizontally(CumbreMotion.desplazamiento) { it } + fadeIn(CumbreMotion.opacidad),
        exit = slideOutHorizontally(CumbreMotion.desplazamiento) { it } + fadeOut(CumbreMotion.opacidad),
        modifier = Modifier.fillMaxSize().zIndex(2f)
    ) {
        Box(
            Modifier.fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                // Las pantallas que se abren encima (chat, avisos, perfiles, ajustes…) no dejan pasar toques a Escuelas.
                .blockTouchesBelow()
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
    popSheetOrDismiss: () -> Unit,
    bottomInset: androidx.compose.ui.unit.Dp = 0.dp
) {
    // Contrato de navegación: cada función registra sus pantallas con su propio
    // grafo (xxxRoutes) y navega pidiendo un Destination al Navigator.
    val nav = remember(sheetNav, navController, popSheetOrDismiss) {
        SheetNavigator(sheetNav, navController, popSheetOrDismiss)
    }
    androidx.compose.runtime.CompositionLocalProvider(
        LocalNavigator provides nav,
        LocalOpenGym provides { id -> nav.navigate(Destination.GymDetail(id)) }
    ) {
        NavHost(
            navController = sheetNav,
            startDestination = SHEET_ROOT,
            modifier = Modifier.fillMaxSize(),
            // Deslizado lateral (push) entre pantallas del sheet, como iOS.
            enterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, CumbreMotion.desplazamiento) },
            exitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Start, CumbreMotion.desplazamiento) },
            popEnterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.End, CumbreMotion.desplazamiento) },
            popExitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End, CumbreMotion.desplazamiento) }
        ) {
            composable(
                SHEET_ROOT,
                enterTransition = { EnterTransition.None },
                exitTransition = { ExitTransition.None },
                popEnterTransition = { EnterTransition.None },
                popExitTransition = { ExitTransition.None }
            ) { Box(Modifier.fillMaxSize()) }
            schoolRoutes(nav, bottomInset)
            dayRoutes(nav)
            savedRoutes(nav)
            compareRoutes(nav)
            submissionRoutes(nav)
            profileRoutes(nav)
            userRoutes(nav)
            chatRoutes(nav, bottomInset)
            communityRoutes(nav, bottomInset)
            notificationRoutes(nav)
            meetupRoutes(nav)
            gymRoutes(nav)
        }
    }
}
