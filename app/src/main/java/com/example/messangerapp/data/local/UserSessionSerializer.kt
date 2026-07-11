package com.example.messangerapp.data.local

import androidx.datastore.core.Serializer
import com.example.messangerapp.domain.model.UserSession
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream

class UserSessionSerializer(
    private val cryptoManager: CryptoManager
) : Serializer<UserSession> {
    
    override val defaultValue: UserSession
        get() = UserSession()

    override suspend fun readFrom(input: InputStream): UserSession {
        val decryptedBytes = cryptoManager.decrypt(input)
        if (decryptedBytes.isEmpty()) {
            return defaultValue
        }
        return try {
            val jsonString = String(decryptedBytes, Charsets.UTF_8)
            Gson().fromJson(jsonString, UserSession::class.java) ?: defaultValue
        } catch (e: Exception) {
            e.printStackTrace()
            defaultValue
        }
    }

    override suspend fun writeTo(t: UserSession, output: OutputStream) {
        val jsonString = Gson().toJson(t)
        cryptoManager.encrypt(jsonString.toByteArray(Charsets.UTF_8), output)
    }
}
