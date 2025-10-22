/**
 * MessageAI – Paging RemoteMediator for messages.
 *
 * Bridges Room and Firestore to load newer pages on demand. Uses a single
 * `RemoteKeys` row per chat to track the next timestamp key (newest-first
 * pagination). Refresh clears caches for the chat before inserting.
 */
package com.messageai.tactical.data.remote

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import androidx.room.withTransaction
import com.messageai.tactical.data.db.AppDatabase
import com.messageai.tactical.data.db.MessageEntity
import com.messageai.tactical.data.db.RemoteKeys
import com.messageai.tactical.data.remote.model.MessageDoc
import javax.inject.Inject

@OptIn(ExperimentalPagingApi::class)
class MessageRemoteMediator @Inject constructor(
    private val chatId: String,
    private val db: AppDatabase,
    private val messageService: MessageService
) : RemoteMediator<Int, MessageEntity>() {

    override suspend fun load(loadType: LoadType, state: PagingState<Int, MessageEntity>): MediatorResult {
        return try {
            val pageSize = state.config.pageSize
            val remoteKeys = db.remoteKeysDao().remoteKeys(chatId)
            val startAfterTs = when (loadType) {
                LoadType.REFRESH -> null
                LoadType.PREPEND -> return MediatorResult.Success(endOfPaginationReached = true)
                LoadType.APPEND -> remoteKeys?.nextKeyTs
            }

            val docs: List<MessageDoc> = messageService.pageMessages(chatId, pageSize, startAfterTs)
            val entities = docs.map { Mapper.messageDocToEntity(it) }
            val lastTs = entities.lastOrNull()?.timestamp

            db.withTransaction {
                if (loadType == LoadType.REFRESH) {
                    // Clear existing data for this chat on refresh
                    db.remoteKeysDao().clear(chatId)
                }
                db.messageDao().upsertAll(entities)
                db.remoteKeysDao().insertOrReplace(RemoteKeys(chatId = chatId, nextKeyTs = lastTs))
            }

            MediatorResult.Success(endOfPaginationReached = entities.isEmpty())
        } catch (e: Exception) {
            MediatorResult.Error(e)
        }
    }
}
