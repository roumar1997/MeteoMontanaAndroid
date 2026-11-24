package com.meteomontana.android.ui.screens.community

import androidx.compose.ui.unit.Dp
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.meteomontana.android.navigation.Routes
import com.meteomontana.android.ui.navigation.Destination
import com.meteomontana.android.ui.navigation.Navigator

/** «Mis publicaciones» y el detalle de un post del feed (push/campanita) dentro del overlay. */
fun NavGraphBuilder.communityRoutes(nav: Navigator, bottomInset: Dp) {
    composable(
        Routes.MY_POSTS,
        arguments = listOf(navArgument("uid") { type = NavType.StringType; nullable = true; defaultValue = null })
    ) { entry ->
        MyPostsScreen(
            onBack = nav::back,
            ownProfile = entry.arguments?.getString("uid").isNullOrBlank(),
            onOpenUser = { uid -> nav.navigate(Destination.PublicProfile(uid)) },
            onOpenSchool = { schoolId, lineId, lineName, blockId ->
                nav.navigate(Destination.SchoolDetail(schoolId, via = lineName, viaId = lineId, blockId = blockId))
            }
        )
    }
    composable(
        route = Routes.FEED_POST,
        arguments = listOf(navArgument("postId") { type = NavType.StringType })
    ) {
        FeedPostDetailScreen(
            onBack = nav::back,
            onOpenUser = { uid -> nav.navigate(Destination.PublicProfile(uid)) },
            onOpenSchool = { schoolId, lineId, lineName, blockId ->
                nav.navigate(Destination.SchoolDetail(schoolId, via = lineName, viaId = lineId, blockId = blockId))
            },
            bottomInset = bottomInset
        )
    }
}
