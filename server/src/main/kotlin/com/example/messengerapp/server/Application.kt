package com.example.messengerapp.server

import com.example.messengerapp.server.auth.UnauthorizedException
import com.example.messengerapp.server.model.ErrorResponse
import com.example.messengerapp.server.route.authRoutes
import com.example.messengerapp.server.route.chatRoutes
import com.example.messengerapp.server.storage.ServerStore
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.NotFoundException
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.server.routing.routing
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import org.slf4j.event.Level
import java.io.File

private val logger = LoggerFactory.getLogger("MessengerServer")

const val SERVER_PORT = 8080

/** DB ファイルの既定位置（:server:run は workingDir を server/ に固定している） */
private const val DB_FILE_PATH = "data/messenger-db.json"

fun main() {
    // 0.0.0.0 で待ち受け、エミュレータ(10.0.2.2)からも実機(LAN IP)からも到達できるようにする
    embeddedServer(Netty, port = SERVER_PORT, host = "0.0.0.0") {
        module(ServerStore(File(DB_FILE_PATH)))
    }.start(wait = true)
}

fun Application.module(store: ServerStore) {
    install(ContentNegotiation) {
        json(
            Json {
                ignoreUnknownKeys = true
                // 既定値のフィールドも出力する（クライアントが常に同じ形を受け取れるようにする）
                encodeDefaults = true
            }
        )
    }

    install(CallLogging) {
        level = Level.INFO
    }

    install(StatusPages) {
        exception<UnauthorizedException> { call, cause ->
            call.respond(HttpStatusCode.Unauthorized, ErrorResponse(cause.message ?: "認証に失敗しました"))
        }
        exception<NotFoundException> { call, cause ->
            call.respond(HttpStatusCode.NotFound, ErrorResponse(cause.message ?: "対象が見つかりません"))
        }
        exception<BadRequestException> { call, cause ->
            call.respond(HttpStatusCode.BadRequest, ErrorResponse(cause.message ?: "リクエストが不正です"))
        }
        exception<Throwable> { call, cause ->
            logger.error("未処理の例外", cause)
            call.respond(HttpStatusCode.InternalServerError, ErrorResponse("サーバ内部エラー"))
        }
    }

    routing {
        authRoutes(store)
        chatRoutes(store)
    }
}
