package com.messageai.tactical.modules.documents.di

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.messageai.tactical.modules.documents.DocumentService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DocumentsModule {

    @Provides
    @Singleton
    fun provideDocumentService(auth: FirebaseAuth, db: FirebaseFirestore): DocumentService {
        return DocumentService(auth, db)
    }
}


