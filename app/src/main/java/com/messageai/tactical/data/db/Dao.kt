/**
 * MessageAI – Room DAOs for local persistence.
 *
 * Streaming queries back UI flows, while mutation methods are suspendable to
 * encourage off-main-thread usage. Pagination for messages uses a keyed query
 * ordered by timestamp.
 */
package com.messageai.tactical.data.db

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(messages: List<MessageEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(message: MessageEntity)

    @Query("SELECT * FROM messages WHERE chatId = :chatId ORDER BY timestamp DESC")
    fun pagingSource(chatId: String): PagingSource<Int, MessageEntity>

    @Query("UPDATE messages SET status = :status WHERE id = :messageId")
    suspend fun updateStatus(messageId: String, status: String)
}

@Dao
interface ChatDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(chats: List<ChatEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(chat: ChatEntity)

    @Query("SELECT * FROM chats ORDER BY updatedAt DESC")
    fun getChats(): Flow<List<ChatEntity>>

    @Query("SELECT * FROM chats WHERE id = :chatId LIMIT 1")
    fun getChat(chatId: String): Flow<ChatEntity?>

    @Query("UPDATE chats SET unreadCount = :count WHERE id = :chatId")
    suspend fun updateUnread(chatId: String, count: Int)
}

@Dao
interface SendQueueDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun enqueue(item: SendQueueEntity)

    @Update
    suspend fun update(item: SendQueueEntity)

    @Query("DELETE FROM send_queue WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT * FROM send_queue ORDER BY createdAt ASC")
    fun items(): Flow<List<SendQueueEntity>>
}

@Dao
interface RemoteKeysDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplace(keys: RemoteKeys)

    @Query("SELECT * FROM remote_keys WHERE chatId = :chatId")
    suspend fun remoteKeys(chatId: String): RemoteKeys?

    @Query("DELETE FROM remote_keys WHERE chatId = :chatId")
    suspend fun clear(chatId: String)
}
