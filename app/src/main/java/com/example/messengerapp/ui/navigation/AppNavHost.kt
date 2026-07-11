package com.example.messengerapp.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.messengerapp.notification.NotificationTarget
import com.example.messengerapp.ui.MainViewModel
import com.example.messengerapp.ui.components.BottomNavBar
import com.example.messengerapp.ui.screen.splash.SplashScreen

/**
 * アプリ全体の root NavHost。
 *
 * 直接扱う destination は [Routes.SPLASH] のみで、認証前後のフローはそれぞれ
 * [authGraph] / [mainGraph] という独立した nested graph に分離している。
 * これにより graph 境界を跨ぐ backstack 操作（ログイン成功・ログアウト）が宣言的に書ける。
 *
 * 通知タップ経由の画面遷移要求は、`destination.hierarchy` に [Routes.Main.GRAPH] が
 * 含まれるようになるまで保留される。未認証の状態で通知から起動された場合でも、
 * Splash→（自動ログイン or サインイン）→ Main graph 到達後に正しく適用される。
 */
@Composable
fun AppNavHost(
    navController: NavHostController = rememberNavController(),
    mainViewModel: MainViewModel = hiltViewModel()
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val currentRoute = currentDestination?.route

    val showBottomNav = currentRoute == Routes.Main.CHAT_LIST ||
            currentRoute == Routes.Main.FRIENDS_LIST

    val pendingTarget by mainViewModel.pendingTarget.collectAsState()
    val isInMainGraph = currentDestination?.hierarchy
        ?.any { it.route == Routes.Main.GRAPH } == true

    LaunchedEffect(pendingTarget, isInMainGraph) {
        val target = pendingTarget ?: return@LaunchedEffect
        if (!isInMainGraph) return@LaunchedEffect
        navigateToTarget(navController, target)
        mainViewModel.onTargetConsumed()
    }

    Scaffold(
        bottomBar = {
            if (showBottomNav) {
                BottomNavBar(
                    currentRoute = currentRoute,
                    onNavigateToChatList = {
                        navController.navigate(Routes.Main.CHAT_LIST) {
                            popUpTo(Routes.Main.CHAT_LIST) { inclusive = true }
                        }
                    },
                    onNavigateToFriends = {
                        navController.navigate(Routes.Main.FRIENDS_LIST) {
                            popUpTo(Routes.Main.CHAT_LIST)
                        }
                    },
                    onNavigateToSettings = {
                        navController.navigate(Routes.Main.SETTINGS)
                    }
                )
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.SPLASH,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Routes.SPLASH) {
                SplashScreen(
                    onNavigateToSignIn = {
                        navController.navigate(Routes.Auth.GRAPH) {
                            popUpTo(Routes.SPLASH) { inclusive = true }
                        }
                    },
                    onNavigateToChatList = {
                        navController.navigate(Routes.Main.GRAPH) {
                            popUpTo(Routes.SPLASH) { inclusive = true }
                        }
                    }
                )
            }

            authGraph(
                onSignedIn = {
                    navController.navigate(Routes.Main.GRAPH) {
                        popUpTo(Routes.Auth.GRAPH) { inclusive = true }
                    }
                }
            )

            mainGraph(
                navController = navController,
                onLogout = {
                    navController.navigate(Routes.Auth.GRAPH) {
                        popUpTo(Routes.Main.GRAPH) { inclusive = true }
                    }
                }
            )
        }
    }
}

private fun navigateToTarget(
    navController: NavHostController,
    target: NotificationTarget
) {
    when (target) {
        NotificationTarget.ChatList -> navController.navigate(Routes.Main.CHAT_LIST) {
            popUpTo(Routes.Main.CHAT_LIST) { inclusive = true }
        }
        NotificationTarget.Friends -> navController.navigate(Routes.Main.FRIENDS_LIST) {
            popUpTo(Routes.Main.CHAT_LIST)
        }
        NotificationTarget.Settings -> navController.navigate(Routes.Main.SETTINGS)
        is NotificationTarget.ChatRoom -> {
            // ChatList をベースに積むことで戻る操作で自然に一覧へ戻れるようにする
            navController.navigate(Routes.Main.chatRoom(target.roomId)) {
                popUpTo(Routes.Main.CHAT_LIST)
            }
        }
    }
}
