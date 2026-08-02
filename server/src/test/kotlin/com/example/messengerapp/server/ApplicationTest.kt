package com.example.messengerapp.server

import com.example.messengerapp.server.model.AuthResponse
import com.example.messengerapp.server.model.ChatMessageDto
import com.example.messengerapp.server.model.ChatRoomDto
import com.example.messengerapp.server.model.ChatRoomReadStateDto
import com.example.messengerapp.server.model.CreateGroupRequest
import com.example.messengerapp.server.model.FriendDto
import com.example.messengerapp.server.model.LoginRequest
import com.example.messengerapp.server.model.SendMessageRequest
import com.example.messengerapp.server.storage.SeedData
import com.example.messengerapp.server.storage.ServerStore
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

class ApplicationTest {

    /** テストごとに使い捨ての DB ファイルを渡す（実行環境の server/data を汚さない） */
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `login issues a token and registers an unknown user`() = withServer {
        val response = client.post("/api/v1/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest(userId = "me"))
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.body<AuthResponse>()
        assertEquals("me", body.userId)
        assertEquals("自分", body.userName)
        assertTrue(body.token.isNotBlank())
    }

    @Test
    fun `protected endpoints reject a missing or unknown token`() = withServer {
        assertEquals(HttpStatusCode.Unauthorized, client.get("/api/v1/chats").status)

        val withUnknownToken = client.get("/api/v1/chats") {
            header(HttpHeaders.Authorization, "Bearer no_such_token")
        }
        assertEquals(HttpStatusCode.Unauthorized, withUnknownToken.status)
    }

    @Test
    fun `chat rooms and friends come from the seed data`() = withServer {
        val token = login()

        val rooms = client.authorizedGet("/api/v1/chats", token).body<List<ChatRoomDto>>()
        assertEquals(15, rooms.size)
        // 一覧は最新順で返る
        assertEquals(rooms.sortedByDescending { it.lastMessageTime }, rooms)

        val friends = client.authorizedGet("/api/v1/friends", token).body<List<FriendDto>>()
        assertEquals(10, friends.size)
    }

    @Test
    fun `messages are paged by beforeMessageId`() = withServer {
        val token = login()

        val latest = client
            .authorizedGet("/api/v1/chats/room_0/messages?limit=5", token)
            .body<List<ChatMessageDto>>()
        assertEquals(5, latest.size)
        // 返却は昇順
        assertEquals(latest.sortedBy { it.sentAt }, latest)

        val older = client
            .authorizedGet("/api/v1/chats/room_0/messages?limit=5&beforeMessageId=${latest.first().messageId}", token)
            .body<List<ChatMessageDto>>()
        assertEquals(5, older.size)
        // 取得済みの範囲と重複しない
        assertTrue(older.none { old -> latest.any { it.messageId == old.messageId } })
        assertTrue(older.last().sentAt <= latest.first().sentAt)
    }

    @Test
    fun `paging returns empty at the oldest message`() = withServer {
        val token = login()

        val all = client
            .authorizedGet("/api/v1/chats/room_0/messages?limit=100", token)
            .body<List<ChatMessageDto>>()

        val older = client
            .authorizedGet("/api/v1/chats/room_0/messages?limit=5&beforeMessageId=${all.first().messageId}", token)
            .body<List<ChatMessageDto>>()

        assertTrue(older.isEmpty())
    }

    @Test
    fun `a sent message can be fetched back`() = withServer {
        val token = login()

        val sent = client.post("/api/v1/chats/room_0/messages") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(SendMessageRequest(body = "日本語のテスト"))
        }
        assertEquals(HttpStatusCode.Created, sent.status)
        val message = sent.body<ChatMessageDto>()
        assertEquals("日本語のテスト", message.body)

        val messages = client
            .authorizedGet("/api/v1/chats/room_0/messages?limit=5", token)
            .body<List<ChatMessageDto>>()
        assertEquals(message.messageId, messages.last().messageId)

        // 一覧の最終メッセージにも反映される
        val room = client.authorizedGet("/api/v1/chats", token)
            .body<List<ChatRoomDto>>()
            .first { it.roomId == "room_0" }
        assertEquals("日本語のテスト", room.lastMessage)
    }

    @Test
    fun `an image message keeps its type and payload`() = withServer {
        val token = login()

        val sent = client.post("/api/v1/chats/room_0/messages") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(
                SendMessageRequest(
                    body = "",
                    messageType = "IMAGE",
                    imageBase64 = "base64data"
                )
            )
        }.body<ChatMessageDto>()

        assertEquals("IMAGE", sent.messageType)
        assertEquals("base64data", sent.imageBase64)

        val room = client.authorizedGet("/api/v1/chats", token)
            .body<List<ChatRoomDto>>()
            .first { it.roomId == "room_0" }
        assertEquals("📷 写真", room.lastMessage)
    }

    @Test
    fun `an unknown roomId returns 404`() = withServer {
        val token = login()

        assertEquals(
            HttpStatusCode.NotFound,
            client.authorizedGet("/api/v1/chats/no_such_room/messages?limit=5", token).status
        )
    }

    @Test
    fun `read state is created on the first access and stays stable`() = withServer {
        val token = login()

        val first = client
            .authorizedGet("/api/v1/chats/room_0/read-state", token)
            .body<ChatRoomReadStateDto>()
        val second = client
            .authorizedGet("/api/v1/chats/room_0/read-state", token)
            .body<ChatRoomReadStateDto>()

        assertEquals("room_0", first.roomId)
        assertEquals(first.lastReadAt, second.lastReadAt)
    }

    @Test
    fun `room members always include the caller`() = withServer {
        val token = login(userId = "user_0")

        val members = client
            .authorizedGet("/api/v1/chats/room_0/members", token)
            .body<List<FriendDto>>()

        assertTrue(members.any { it.userId == "user_0" })
        assertTrue(members.any { it.userId == SeedData.SELF_USER_ID })
    }

    @Test
    fun `createGroup adds a room that shows up in the list`() = withServer {
        val token = login()

        val created = client.post("/api/v1/chats") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(CreateGroupRequest(name = "新グループ 123", memberIds = listOf("user_0")))
        }
        assertEquals(HttpStatusCode.Created, created.status)
        val room = created.body<ChatRoomDto>()
        assertEquals("新グループ 123", room.roomName)

        val rooms = client.authorizedGet("/api/v1/chats", token).body<List<ChatRoomDto>>()
        assertTrue(rooms.any { it.roomId == room.roomId })

        // 作成直後はメッセージが空
        val messages = client
            .authorizedGet("/api/v1/chats/${room.roomId}/messages?limit=10", token)
            .body<List<ChatMessageDto>>()
        assertTrue(messages.isEmpty())
    }

    @Test
    fun `an empty message body is rejected`() = withServer {
        val token = login()

        val response = client.post("/api/v1/chats/room_0/messages") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(SendMessageRequest(body = "   "))
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `state survives a restart against the same file`() {
        val dbFile = File(tempDir.toFile(), "messenger-db.json")

        var messageId = ""
        testApplication {
            application { module(ServerStore(dbFile)) }
            val client = jsonClient()
            val token = client.login()
            messageId = client.post("/api/v1/chats/room_0/messages") {
                header(HttpHeaders.Authorization, "Bearer $token")
                contentType(ContentType.Application.Json)
                setBody(SendMessageRequest(body = "再起動しても残る"))
            }.body<ChatMessageDto>().messageId
        }

        // 同じファイルで作り直す＝サーバ再起動に相当する
        testApplication {
            application { module(ServerStore(dbFile)) }
            val client = jsonClient()
            val token = client.login()
            val messages = client
                .authorizedGet("/api/v1/chats/room_0/messages?limit=100", token)
                .body<List<ChatMessageDto>>()
            assertFalse(messages.none { it.messageId == messageId })
        }
    }

    // --- テスト用ヘルパー ---

    /**
     * 使い捨ての DB ファイルでサーバを起動し、JSON クライアントを用意する。
     * ブロック内では [ApplicationTestBuilder] の `client` が JSON を扱える状態になっている。
     */
    private fun withServer(block: suspend TestContext.() -> Unit) = testApplication {
        application { module(ServerStore(File(tempDir.toFile(), "messenger-db.json"))) }
        TestContext(jsonClient()).block()
    }

    private class TestContext(val client: HttpClient) {
        suspend fun login(userId: String = SeedData.SELF_USER_ID): String = client.login(userId)
    }

    private companion object {

        fun ApplicationTestBuilder.jsonClient(): HttpClient = createClient {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }

        suspend fun HttpClient.login(userId: String = SeedData.SELF_USER_ID): String =
            post("/api/v1/auth/login") {
                contentType(ContentType.Application.Json)
                setBody(LoginRequest(userId = userId))
            }.body<AuthResponse>().token

        suspend fun HttpClient.authorizedGet(path: String, token: String): HttpResponse =
            get(path) { header(HttpHeaders.Authorization, "Bearer $token") }
    }
}
