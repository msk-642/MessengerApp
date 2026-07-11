package com.example.messengerapp.di

import com.example.messengerapp.data.repository.AuthRepositoryImpl
import com.example.messengerapp.data.repository.ChatRepositoryImpl
import com.example.messengerapp.domain.repository.AuthRepository
import com.example.messengerapp.domain.repository.ChatRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindAuthRepository(
        authRepositoryImpl: AuthRepositoryImpl
    ): AuthRepository

    @Binds
    @Singleton
    abstract fun bindChatRepository(
        chatRepositoryImpl: ChatRepositoryImpl
    ): ChatRepository

}
