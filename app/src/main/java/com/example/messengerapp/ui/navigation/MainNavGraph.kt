package com.example.messengerapp.ui.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.navArgument
import com.example.messengerapp.ui.screen.chatlist.ChatListScreen
import com.example.messengerapp.ui.screen.chatroom.ChatRoomScreen
import com.example.messengerapp.ui.screen.friends.FriendsScreen
import com.example.messengerapp.ui.screen.settings.SettingsScreen

/**
 * サインイン済みユーザー向け subgraph。
 *
 * subgraph 内部遷移（ChatList → ChatRoom 等）は [navController] で閉じ、
 * 外側（[Routes.Auth.GRAPH]）への遷移となるログアウトだけは [onLogout] で呼び出し側に委譲する。
 * この分離により本関数は「main フロー内の遷移グラフ」としての責務に集中できる。
 */
fun NavGraphBuilder.mainGraph(
    navController: NavHostController,
    onLogout: () -> Unit
) {
    navigation(
        startDestination = Routes.Main.CHAT_LIST,
        route = Routes.Main.GRAPH
    ) {
        composable(Routes.Main.CHAT_LIST) {
            ChatListScreen(
                onNavigateToChatRoom = { roomId ->
                    navController.navigate(Routes.Main.chatRoom(roomId))
                }
            )
        }
        composable(Routes.Main.FRIENDS_LIST) {
            FriendsScreen()
        }
        composable(Routes.Main.SETTINGS) {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() },
                onLogout = onLogout
            )
        }
        composable(
            route = Routes.Main.CHAT_ROOM,
            arguments = listOf(
                navArgument(Routes.Main.CHAT_ROOM_ARG) { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val roomId = backStackEntry.arguments
                ?.getString(Routes.Main.CHAT_ROOM_ARG)
                .orEmpty()
            ChatRoomScreen(
                roomId = roomId,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
