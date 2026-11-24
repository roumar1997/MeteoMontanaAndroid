package com.meteomontana.android.ui.screens.detail

import androidx.compose.ui.unit.Dp
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.meteomontana.android.navigation.Routes
import com.meteomontana.android.ui.navigation.Destination
import com.meteomontana.android.ui.navigation.Navigator

/** Detalle de escuela (en el overlay, con la barra de pestañas visible) y su chat. */
fun NavGraphBuilder.schoolRoutes(nav: Navigator, bottomInset: Dp) {
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
            onBack = nav::back,
            // El editor de topos SÍ es pantalla completa (sin barra): el Navigator
            // lo abre en el NavHost principal sin cerrar el overlay.
            onOpenBlock = { blockId -> nav.navigate(Destination.TopoEditor(blockId)) },
            onMyProposals = { nav.navigate(Destination.MySubmissions) },
            onDayClick = { idx -> nav.navigate(Destination.DayDetail(schoolId, idx)) },
            onOpenChat = { uid, _ -> nav.navigate(Destination.Chat(uid)) },
            onOpenSchoolChat = { name -> nav.navigate(Destination.SchoolChat(schoolId, name)) }
        )
    }
    composable(
        route = Routes.SCHOOL_CHAT,
        arguments = listOf(
            navArgument("schoolId") { type = NavType.StringType },
            navArgument("name") { type = NavType.StringType; defaultValue = "" }
        )
    ) { entry ->
        SchoolChatScreen(
            schoolId = entry.arguments?.getString("schoolId") ?: "",
            schoolName = entry.arguments?.getString("name") ?: "",
            myUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: "",
            onBack = nav::back,
            onOpenChat = { uid, _ -> nav.navigate(Destination.Chat(uid)) },
            onOpenProfile = { uid -> nav.navigate(Destination.PublicProfile(uid)) },
            bottomInset = bottomInset
        )
    }
}
