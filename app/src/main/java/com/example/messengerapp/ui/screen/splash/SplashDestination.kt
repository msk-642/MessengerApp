package com.example.messengerapp.ui.screen.splash

sealed class SplashDestination {
    data object Undecided : SplashDestination()
    data object SignIn : SplashDestination()
    data object ChatList : SplashDestination()
}
