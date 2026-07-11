package com.example.messangerapp.ui.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import com.example.messangerapp.ui.screen.signin.SignInScreen

/**
 * 認証前フローの subgraph。
 *
 * サインイン成功時の遷移は呼び出し側（[AppNavHost]）が決めるため [onSignedIn] で受け取る。
 * これにより本関数は「サインインが完了した」というイベントを通知するだけの責務になり、
 * 画面遷移ポリシー（main graph へ飛ぶか、別の graph へ飛ぶか等）は AppNavHost に集約される。
 */
fun NavGraphBuilder.authGraph(
    onSignedIn: () -> Unit
) {
    navigation(
        startDestination = Routes.Auth.SIGN_IN,
        route = Routes.Auth.GRAPH
    ) {
        composable(Routes.Auth.SIGN_IN) {
            SignInScreen(onSignInSuccess = onSignedIn)
        }
    }
}
