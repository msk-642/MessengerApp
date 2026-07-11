package com.example.messangerapp.ui.screen.splash

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun SplashScreen(
    onNavigateToSignIn: () -> Unit,
    onNavigateToChatList: () -> Unit,
    viewModel: SplashViewModel = hiltViewModel()
) {
    val destination by viewModel.destination.collectAsState()

    LaunchedEffect(destination) {
        when (destination) {
            is SplashDestination.SignIn -> onNavigateToSignIn()
            is SplashDestination.ChatList -> onNavigateToChatList()
            else -> Unit
        }
    }

    // スプラッシュUI（ロゴ表示中などに使用）
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}
