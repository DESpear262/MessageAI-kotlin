package com.messageai.tactical.modules.facility

import com.google.firebase.firestore.FirebaseFirestore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object FacilityModule {
    @Provides
    @Singleton
    fun provideFacilityService(db: FirebaseFirestore): FacilityService = FacilityService(db)
}


