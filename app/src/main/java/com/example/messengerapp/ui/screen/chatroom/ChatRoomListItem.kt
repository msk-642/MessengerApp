package com.example.messengerapp.ui.screen.chatroom

import com.example.messengerapp.domain.model.ChatMessage

/**
 * チャットルームのメッセージ表示領域に並べる1行分の表示アイテム。
 * 日付区切り・未読境界はサーバやリポジトリに保存せず、表示直前に導出する。
 */
sealed interface ChatRoomListItem {

    /** LazyColumn の key に使う一意キー */
    val key: String

    /** 日付区切りメッセージ（日付単位 yyyyMMdd で1つ） */
    data class DateSeparator(
        /** 表示ラベル (yyyy/MM/dd) */
        val label: String,
        override val key: String
    ) : ChatRoomListItem

    /** 未読境界メッセージ（既読日時より未来の最初のメッセージの直前に1つ） */
    data object UnreadBoundary : ChatRoomListItem {
        override val key: String = "unread_boundary"
    }

    /** 通常メッセージ */
    data class Message(val message: ChatMessage) : ChatRoomListItem {
        override val key: String get() = message.messageId
    }
}
