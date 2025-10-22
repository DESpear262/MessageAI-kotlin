/**
 * MessageAI – Participant helper utilities.
 *
 * Provides helper functions for parsing and manipulating participant lists
 * stored as JSON strings in Room entities.
 */
package com.messageai.tactical.util

import kotlinx.serialization.json.Json

object ParticipantHelper {
    private val json = Json { ignoreUnknownKeys = true }
    
    /**
     * Parses a JSON-encoded participant list from a Room entity.
     *
     * @param participantsJson JSON string containing array of participant UIDs
     * @return List of participant UIDs, or empty list if parsing fails
     */
    fun parseParticipants(participantsJson: String): List<String> {
        return try {
            json.decodeFromString<List<String>>(participantsJson)
        } catch (_: Exception) {
            emptyList()
        }
    }
    
    /**
     * Gets the "other" participant UID in a 1:1 chat (the one that isn't myUid).
     *
     * @param participantsJson JSON string containing array of participant UIDs
     * @param myUid Current user's UID
     * @return The other participant's UID, or myUid if not found (self-chat)
     */
    fun getOtherParticipant(participantsJson: String, myUid: String): String {
        val list = parseParticipants(participantsJson)
        return list.firstOrNull { it != myUid } ?: myUid
    }
}

