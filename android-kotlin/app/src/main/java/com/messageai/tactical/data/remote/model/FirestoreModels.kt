package com.messageai.tactical.data.remote.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.ServerTimestamp

data class ParticipantInfo(
    val name: String = "",
    val photoUrl: String? = null
)

data class LastMessage(
    val text: String? = null,
    val imageUrl: String? = null,
    val senderId: String = "",
    @ServerTimestamp val timestamp: Timestamp? = null
)

data class UserDoc(
    val uid: String = "",
    val email: String = "",
    val displayName: String = "",
    val photoURL: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val lastSeen: Timestamp? = null,
    val isOnline: Boolean = false,
    val fcmToken: String? = null,
    val metadata: Map<String, Any?>? = null
)

data class ChatDoc(
    val id: String = "",
    val participants: List<String> = emptyList(),
    val participantDetails: Map<String, ParticipantInfo>? = null,
    val lastMessage: LastMessage? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
    val metadata: Map<String, Any?>? = null
)

data class GroupDoc(
    val id: String = "",
    val name: String = "",
    val members: List<String> = emptyList(),
    val memberDetails: Map<String, ParticipantInfo>? = null,
    val createdBy: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    val lastMessage: LastMessage? = null,
    val metadata: Map<String, Any?>? = null
)

enum class MessageStatus { SENDING, SENT, DELIVERED, READ }

data class MessageDoc(
    val id: String = "",
    val chatId: String = "",
    val senderId: String = "",
    val text: String? = null,
    val imageUrl: String? = null,
    @ServerTimestamp val timestamp: Timestamp? = null,
    val clientTimestamp: Long? = null,
    val status: String = MessageStatus.SENDING.name,
    val readBy: List<String> = emptyList(),
    val deliveredBy: List<String> = emptyList(),
    val localOnly: Boolean = false,
    val metadata: Map<String, Any?>? = null
)
