package com.example.messengerapp.server.auth

import com.example.messengerapp.server.storage.ServerStore
import io.ktor.http.HttpHeaders
import io.ktor.server.application.ApplicationCall

/** 認証に失敗したことを表す。StatusPages で 401 に変換される */
class UnauthorizedException(message: String) : RuntimeException(message)

private const val BEARER_PREFIX = "Bearer "

/**
 * `Authorization: Bearer <token>` を検証し、対応する userId を返す。
 * 失敗した場合は [UnauthorizedException] を投げる。
 */
suspend fun ApplicationCall.requireUserId(store: ServerStore): String {
    val header = request.headers[HttpHeaders.Authorization]
        ?: throw UnauthorizedException("Authorization ヘッダがありません")
    if (!header.startsWith(BEARER_PREFIX)) {
        throw UnauthorizedException("Authorization ヘッダは Bearer 形式で指定してください")
    }
    val token = header.removePrefix(BEARER_PREFIX).trim()
    if (token.isEmpty()) {
        throw UnauthorizedException("トークンが空です")
    }
    return store.read { it.tokens[token] }
        ?: throw UnauthorizedException("トークンが無効です")
}
