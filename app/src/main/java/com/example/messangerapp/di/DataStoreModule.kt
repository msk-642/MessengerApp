package com.example.messangerapp.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.dataStoreFile
import com.example.messangerapp.data.local.CryptoManager
import com.example.messangerapp.data.local.UserSessionSerializer
import com.example.messangerapp.domain.model.UserSession
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataStoreModule {

    @Provides
    @Singleton
    fun provideCryptoManager(): CryptoManager {
        return CryptoManager()
    }

    @Provides
    @Singleton
    fun provideUserSessionDataStore(
        @ApplicationContext context: Context,
        cryptoManager: CryptoManager
    ): DataStore<UserSession> {
        return DataStoreFactory.create(
            serializer = UserSessionSerializer(cryptoManager),
            produceFile = { context.dataStoreFile("user_session.json") }
        )
    }
}
