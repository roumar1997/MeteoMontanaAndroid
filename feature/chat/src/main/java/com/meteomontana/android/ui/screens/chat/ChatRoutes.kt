package com.meteomontana.android.ui.screens.chat

import androidx.compose.ui.unit.Dp
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.meteomontana.android.navigation.Routes
import com.meteomontana.android.ui.navigation.Destination
import com.meteomontana.android.ui.navigation.Navigator

/** Lista de chats, chat 1:1, grupos y «nuevo grupo» dentro del overlay. */
fun NavGraphBuilder.chatRoutes(nav: Navigator, bottomInset: Dp) {
    composable(Routes.CHAT_LIST) {
        ChatListScreen(
            onBack = nav::back,
            onOpenChat = { uid -> nav.navigate(Destination.Chat(uid)) },
            onOpenGroup = { convId -> nav.navigate(Destination.GroupChat(convId)) },
            onNewGroup = { nav.navigate(Destination.NewGroup) }
        )
    }
    composable(
        route = Routes.CHAT,
        arguments = listOf(navArgument("uid") { type = NavType.StringType })
    ) {
        ChatScreen(
            onBack = nav::back,
            onOpenProfile = { uid -> nav.navigate(Destination.PublicProfile(uid)) },
            bottomInset = bottomInset
        )
    }
    composable(Routes.NEW_GROUP) {
        NewGroupScreen(
            onBack = nav::back,
            onCreated = { convId -> nav.replace(Destination.GroupChat(convId)) }
        )
    }
    composable(
        route = Routes.GROUP_CHAT,
        arguments = listOf(navArgument("convId") { type = NavType.StringType })
    ) {
        GroupChatScreen(
            onBack = nav::back,
            onOpenMeetup = { meetupId -> nav.navigate(Destination.MeetupDetail(meetupId)) },
            onOpenProfile = { uid -> nav.navigate(Destination.PublicProfile(uid)) },
            bottomInset = bottomInset
        )
    }
}
