package com.example.messangerapp.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.example.messangerapp.ui.navigation.Routes

@Composable
fun BottomNavBar(
    currentRoute: String?,
    onNavigateToChatList: () -> Unit,
    onNavigateToFriends: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    Surface(
        tonalElevation = 8.dp,
        shadowElevation = 16.dp,
        border = androidx.compose.foundation.BorderStroke(
            0.5.dp,
            MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)
        )
    ) {
        NavigationBar(
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp
        ) {
            NavigationBarItem(
                selected = currentRoute == Routes.Main.CHAT_LIST,
                onClick = onNavigateToChatList,
                icon = { Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = "Chat List") },
                label = { Text("トーク") },
                colors = NavigationBarItemDefaults.colors(
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
            NavigationBarItem(
                selected = currentRoute == Routes.Main.FRIENDS_LIST,
                onClick = onNavigateToFriends,
                icon = { Icon(Icons.Default.People, contentDescription = "Friends") },
                label = { Text("友達") },
                colors = NavigationBarItemDefaults.colors(
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
            NavigationBarItem(
                selected = currentRoute == Routes.Main.SETTINGS,
                onClick = onNavigateToSettings,
                icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                label = { Text("設定") },
                colors = NavigationBarItemDefaults.colors(
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    }
}
