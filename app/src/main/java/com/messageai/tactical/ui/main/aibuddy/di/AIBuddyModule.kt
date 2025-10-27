package com.messageai.tactical.ui.main.aibuddy.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AIBuddyModule {
    @Provides
    @Singleton
    fun provideAppContext(app: android.app.Application): Context = app.applicationContext
}


