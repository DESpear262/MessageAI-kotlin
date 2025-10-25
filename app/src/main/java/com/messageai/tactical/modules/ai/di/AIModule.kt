package com.messageai.tactical.modules.ai.di

import com.messageai.tactical.modules.ai.AIService
import com.messageai.tactical.modules.ai.IAIProvider
import com.messageai.tactical.modules.ai.RagContextBuilder
import com.messageai.tactical.modules.ai.api.LangChainApi
import com.messageai.tactical.modules.ai.provider.LangChainAdapter
import com.messageai.tactical.modules.reporting.ReportService
import com.messageai.tactical.modules.ai.provider.LocalProvider
import com.messageai.tactical.modules.ai.provider.LangChainProvider
import com.messageai.tactical.data.db.AppDatabase
import com.messageai.tactical.BuildConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import com.google.firebase.auth.FirebaseAuth
import com.google.android.gms.tasks.Tasks
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AIModule {

    @Provides
    @Singleton
    fun provideOkHttp(auth: FirebaseAuth): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY else HttpLoggingInterceptor.Level.BASIC
        }
        val authInterceptor = Interceptor { chain ->
            val original = chain.request()
            val token = try {
                val task = auth.currentUser?.getIdToken(false)
                if (task != null) Tasks.await(task).token else null
            } catch (_: Exception) { null }
            val builder = original.newBuilder()
            if (!token.isNullOrEmpty()) {
                builder.addHeader("Authorization", "Bearer $token")
            }
            chain.proceed(builder.build())
        }
        return OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(logging)
            .build()
    }

    @Provides
    @Singleton
    fun provideLangChainApi(client: OkHttpClient): LangChainApi {
        val moshi = Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()
        return Retrofit.Builder()
            .baseUrl(BuildConfig.CF_BASE_URL)
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(LangChainApi::class.java)
    }

    @Provides
    @Singleton
    fun provideLangChainAdapter(api: LangChainApi): LangChainAdapter = LangChainAdapter(api)

    @Provides
    @Singleton
    fun provideIAIProvider(adapter: LangChainAdapter): IAIProvider {
        return if (BuildConfig.AI_ENABLED) {
            LangChainProvider(adapter)
        } else {
            LocalProvider()
        }
    }

    @Provides
    @Singleton
    fun provideRagContextBuilder(db: AppDatabase): RagContextBuilder = RagContextBuilder(db.messageDao())

    @Provides
    @Singleton
    fun provideAIService(db: AppDatabase, provider: IAIProvider, adapter: LangChainAdapter, builder: RagContextBuilder): AIService {
        return AIService(dao = db.messageDao(), provider = provider, adapter = adapter, contextBuilder = builder)
    }

    @Provides
    @Singleton
    fun provideReportService(adapter: LangChainAdapter): ReportService = ReportService(adapter)
}


