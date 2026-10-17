package com.example.messengerapp.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Qualifier
import javax.inject.Singleton

/** ダウンロードした APK の置き場。素の File と区別するための修飾子 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApkCacheDir

@Module
@InstallIn(SingletonComponent::class)
object UpdateModule {

    @Provides
    @Singleton
    @ApkCacheDir
    fun provideApkCacheDir(@ApplicationContext context: Context): File =
        File(context.cacheDir, APK_CACHE_DIR_NAME)

    private const val APK_CACHE_DIR_NAME = "update"
}
