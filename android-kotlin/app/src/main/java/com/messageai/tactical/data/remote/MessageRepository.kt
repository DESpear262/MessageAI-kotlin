/**
 * MessageAI – Paging repository for chat messages.
 *
 * Exposes a Paging3 `Flow<PagingData<MessageEntity>>` sourced from Room with a
 * `RemoteMediator` that backfills from Firestore in 50-item pages by default.
 */
package com.messageai.tactical.data.remote

import androidx.paging.ExperimentalPagingApi
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import com.messageai.tactical.data.db.AppDatabase
import com.messageai.tactical.data.db.MessageEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MessageRepository @Inject constructor(
    private val db: AppDatabase,
    private val service: MessageService
) {
    @OptIn(ExperimentalPagingApi::class)
    /** Returns a paged stream of messages for a chat, newest-first. */
    fun messages(chatId: String, pageSize: Int = 50): Flow<PagingData<MessageEntity>> {
        return Pager(
            config = PagingConfig(pageSize = pageSize, enablePlaceholders = false),
            remoteMediator = MessageRemoteMediator(chatId, db, service),
            pagingSourceFactory = { db.messageDao().pagingSource(chatId) }
        ).flow.map { paging ->
            paging.map { it }
        }
    }
}
