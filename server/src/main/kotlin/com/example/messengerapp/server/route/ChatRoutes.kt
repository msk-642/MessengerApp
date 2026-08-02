package com.example.messengerapp.server.route

import com.example.messengerapp.server.auth.requireUserId
import com.example.messengerapp.server.model.ChatMessageDto
import com.example.messengerapp.server.model.ChatRoomDto
import com.example.messengerapp.server.model.ChatRoomReadStateDto
import com.example.messengerapp.server.model.CreateGroupRequest
import com.example.messengerapp.server.model.FriendDto
import com.example.messengerapp.server.model.SendMessageRequest
import com.example.messengerapp.server.model.ServerState
import com.example.messengerapp.server.storage.ServerStore
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.NotFoundException
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import java.util.UUID

private const val DEFAULT_LIMIT = 100
private const val MESSAGE_TYPE_IMAGE = "IMAGE"
private const val IMAGE_LAST_MESSAGE_LABEL = "📷 写真"

/** 初回アクセス時に入れる既読日時のオフセット（最新の数件が未読として見えるようにする） */
private const val INITIAL_UNREAD_OFFSET_MILLIS = 5 * 60_000L

/** チャット系のルーティング。:app の ChatApi の定義と 1 対 1 で対応する */
fun Route.chatRoutes(store: ServerStore) {

    get("/api/v1/friends") {
        call.requireUserId(store)
        call.respond(store.read { it.friends })
    }

    route("/api/v1/chats") {

        get {
            call.requireUserId(store)
            call.respond(store.read { state -> state.rooms.sortedByDescending { it.lastMessageTime } })
        }

        post {
            val userId = call.requireUserId(store)
            val request = call.receive<CreateGroupRequest>()
            if (request.name.isBlank()) {
                throw BadRequestException("グループ名は必須です")
            }

            val room = store.mutate { state ->
                val createdAt = System.currentTimeMillis()
                val newRoom = ChatRoomDto(
                    roomId = "room_${UUID.randomUUID()}",
                    roomName = request.name,
                    lastMessage = "グループを作成しました",
                    lastMessageTime = createdAt,
                    unreadCount = 0
                )
                // 作成者は必ずメンバーに含める
                val members = (listOf(userId) + request.memberIds).distinct()
                val next = state.copy(
                    rooms = state.rooms + newRoom,
                    messages = state.messages + (newRoom.roomId to emptyList()),
                    roomMembers = state.roomMembers + (newRoom.roomId to members),
                    readStates = state.readStates +
                        (ServerState.readStateKey(newRoom.roomId, userId) to createdAt)
                )
                next to newRoom
            }
            call.respond(HttpStatusCode.Created, room)
        }

        route("/{roomId}") {

            get("/messages") {
                call.requireUserId(store)
                val roomId = call.roomId()
                val beforeMessageId = call.request.queryParameters["beforeMessageId"]
                val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: DEFAULT_LIMIT
                if (limit <= 0) {
                    throw BadRequestException("limit は 1 以上で指定してください")
                }

                val messages = store.read { state ->
                    val all = state.messagesOf(roomId)
                    if (beforeMessageId == null) {
                        // 最新から limit 件（返却は昇順のまま）
                        all.takeLast(limit)
                    } else {
                        val index = all.indexOfFirst { it.messageId == beforeMessageId }
                        // 未知の ID や先頭要素は「これ以上古いものは無い」とみなす
                        if (index <= 0) emptyList() else all.subList(maxOf(0, index - limit), index)
                    }
                }
                call.respond(messages)
            }

            post("/messages") {
                val userId = call.requireUserId(store)
                val roomId = call.roomId()
                val request = call.receive<SendMessageRequest>()
                val isImage = request.messageType == MESSAGE_TYPE_IMAGE
                if (isImage && request.imageBase64.isNullOrBlank()) {
                    throw BadRequestException("画像データがありません")
                }
                if (!isImage && request.body.isBlank()) {
                    throw BadRequestException("本文が空です")
                }

                val message = store.mutate { state ->
                    state.requireRoom(roomId)
                    val sentAt = System.currentTimeMillis()
                    val newMessage = ChatMessageDto(
                        messageId = "msg_${UUID.randomUUID()}",
                        roomId = roomId,
                        senderId = userId,
                        senderName = state.users[userId] ?: userId,
                        body = request.body,
                        sentAt = sentAt,
                        messageType = request.messageType,
                        imageBase64 = request.imageBase64
                    )
                    val lastMessage = if (isImage) IMAGE_LAST_MESSAGE_LABEL else request.body
                    val next = state.copy(
                        rooms = state.rooms.map { room ->
                            if (room.roomId == roomId) {
                                room.copy(lastMessage = lastMessage, lastMessageTime = sentAt)
                            } else {
                                room
                            }
                        },
                        messages = state.messages +
                            (roomId to (state.messages[roomId].orEmpty() + newMessage))
                    )
                    next to newMessage
                }
                call.respond(HttpStatusCode.Created, message)
            }

            get("/read-state") {
                val userId = call.requireUserId(store)
                val roomId = call.roomId()

                // プロトタイプ用: 初回アクセスのユーザーには「最新の数件が未読」の状態を作る。
                // 既読更新の API は ChatApi に定義が無いため、以降この値は変化しない。
                val lastReadAt = store.mutate { state ->
                    state.requireRoom(roomId)
                    val key = ServerState.readStateKey(roomId, userId)
                    val existing = state.readStates[key]
                    if (existing != null) {
                        state to existing
                    } else {
                        val initial = System.currentTimeMillis() - INITIAL_UNREAD_OFFSET_MILLIS
                        state.copy(readStates = state.readStates + (key to initial)) to initial
                    }
                }
                call.respond(ChatRoomReadStateDto(roomId = roomId, lastReadAt = lastReadAt))
            }

            get("/members") {
                val userId = call.requireUserId(store)
                val roomId = call.roomId()

                val members = store.read { state ->
                    state.requireRoom(roomId)
                    // 呼び出し元を必ず含める（自分宛メンションの判定に自分の名前が要るため）
                    val memberIds = (state.roomMembers[roomId].orEmpty() + userId).distinct()
                    memberIds.map { id ->
                        state.friends.firstOrNull { it.userId == id }
                            ?: FriendDto(userId = id, userName = state.users[id] ?: id)
                    }
                }
                call.respond(members)
            }
        }
    }
}

private fun ApplicationCall.roomId(): String =
    parameters["roomId"]?.takeIf { it.isNotBlank() }
        ?: throw BadRequestException("roomId が指定されていません")

private fun ServerState.requireRoom(roomId: String): ChatRoomDto =
    rooms.firstOrNull { it.roomId == roomId }
        ?: throw NotFoundException("ルームが見つかりません: $roomId")

private fun ServerState.messagesOf(roomId: String): List<ChatMessageDto> {
    requireRoom(roomId)
    return messages[roomId].orEmpty()
}
