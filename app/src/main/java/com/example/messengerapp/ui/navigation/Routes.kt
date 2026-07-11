package com.example.messengerapp.ui.navigation

/**
 * アプリ全体のルート定数。
 *
 * - [SPLASH] は起動時の判定専用画面で、どの subgraph にも属さない root 直下の destination。
 * - [Auth] はサインイン／サインアップ等、認証前フローの subgraph。
 * - [Main] はサインイン済みユーザー向けの subgraph。ChatList をホームとしてタブ画面と詳細画面を含む。
 *
 * graph 単位の `popUpTo(Routes.Auth.GRAPH) { inclusive = true }` のような指定が可能になるため、
 * ログイン成功時／ログアウト時の backstack 操作を宣言的に書ける。
 */
object Routes {
    const val SPLASH: String = "splash"

    object Auth {
        const val GRAPH: String = "auth_graph"
        const val SIGN_IN: String = "sign_in"
    }

    object Main {
        const val GRAPH: String = "main_graph"
        const val CHAT_LIST: String = "chat_list"
        const val FRIENDS_LIST: String = "friends_list"
        const val SETTINGS: String = "settings"
        const val CHAT_ROOM: String = "chat_room/{roomId}"

        const val CHAT_ROOM_ARG: String = "roomId"

        fun chatRoom(roomId: String): String = "chat_room/$roomId"
    }
}
