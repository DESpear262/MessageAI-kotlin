package com.messageai.tactical.modules.missions

import com.google.firebase.firestore.FirebaseFirestore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object MissionModule {
    @Provides
    @Singleton
    fun provideMissionService(db: FirebaseFirestore): MissionService = MissionService(db)
}


