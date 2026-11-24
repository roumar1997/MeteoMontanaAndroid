package com.meteomontana.android.ui.screens.notifications

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.meteomontana.android.navigation.Routes
import com.meteomontana.android.ui.navigation.Destination
import com.meteomontana.android.ui.navigation.Navigator

/** Campanita de avisos dentro del overlay. */
fun NavGraphBuilder.notificationRoutes(nav: Navigator) {
    composable(Routes.NOTIFICATIONS) {
        NotificationsScreen(
            onBack = nav::back,
            onOpenUser = { uid -> nav.navigate(Destination.PublicProfile(uid)) },
            onOpenSchool = { id -> nav.navigate(Destination.SchoolDetail(id)) },
            onOpenSubmissions = { nav.navigate(Destination.MySubmissions) },
            onOpenChat = { uid -> nav.navigate(Destination.Chat(uid)) },
            onOpenFollowRequests = { nav.navigate(Destination.FollowRequests) },
            onOpenFeedPost = { postId -> nav.navigate(Destination.FeedPost(postId)) }
        )
    }
}
