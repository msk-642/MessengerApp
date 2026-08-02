package com.example.messengerapp.server.route

import com.example.messengerapp.server.model.AuthResponse
import com.example.messengerapp.server.model.LoginRequest
import com.example.messengerapp.server.storage.ServerStore
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import java.util.UUID

/**
 * 認証系のルーティング。
 *
 * プロトタイプのためパスワードは扱わず、userId のみでサインインする。
 * 未知の userId はその場で登録される。
 */
fun Route.authRoutes(store: ServerStore) {

    post("/api/v1/auth/login") {
        val request = call.receive<LoginRequest>()
        if (request.userId.isBlank()) {
            throw BadRequestException("userId は必須です")
        }

        val response = store.mutate { state ->
            val userName = state.users[request.userId] ?: "ユーザー ${request.userId}"
            val token = UUID.randomUUID().toString()
            val next = state.copy(
                users = state.users + (request.userId to userName),
                tokens = state.tokens + (token to request.userId)
            )
            next to AuthResponse(token = token, userId = request.userId, userName = userName)
        }
        call.respond(response)
    }
}
