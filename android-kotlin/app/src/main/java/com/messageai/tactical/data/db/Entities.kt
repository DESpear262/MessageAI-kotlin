package com.messageai.tactical.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "messages", indices = [Index(value = ["chatId", "timestamp"])])
data class MessageEntity(
    @PrimaryKey val id: String,
    val chatId: String,
    val senderId: String,
    val text: String?,
    val imageUrl: String?,
    val timestamp: Long,
    val status: String,
    val readBy: String,
    val deliveredBy: String = "[]",
    val synced: Boolean = false,
    val createdAt: Long
)

@Entity(tableName = "chats", indices = [Index(value = ["updatedAt"])])
data class ChatEntity(
    @PrimaryKey val id: String,
    val type: String,
    val name: String?,
    val participants: String,
    val lastMessage: String?,
    val lastMessageTime: Long?,
    val unreadCount: Int = 0,
    val updatedAt: Long
)

@Entity(tableName = "send_queue")
data class SendQueueEntity(
    @PrimaryKey val id: String,
    val messageId: String,
    val chatId: String,
    val retryCount: Int = 0,
    val createdAt: Long
)

@Entity(tableName = "remote_keys")
data class RemoteKeys(
    @PrimaryKey val chatId: String,
    val nextKeyTs: Long?
)
