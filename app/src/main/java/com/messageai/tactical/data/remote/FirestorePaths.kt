/**
 * MessageAI – Firestore collection/path constants and deterministic ID helpers.
 *
 * This file centralizes Firestore paths and provides utilities for generating
 * stable identifiers, such as a 1:1 direct chat ID that is deterministic for
 * any pair of user IDs. Keeping these in one place prevents typos and ensures
 * consistent path usage across the app.
 */
package com.messageai.tactical.data.remote

object FirestorePaths {
    const val USERS = "users"
    const val CHATS = "chats"
    const val GROUPS = "groups"
    const val MESSAGES = "messages"

    /**
     * Returns a deterministic direct chat ID for two users.
     *
     * The ID is computed by lexicographically ordering the two user IDs and
     * joining them with an underscore, so the same pair always yields the same
     * chat ID regardless of argument order.
     */
    fun directChatId(uidA: String, uidB: String): String {
        val a = uidA.trim()
        val b = uidB.trim()
        return if (a <= b) "${a}_${b}" else "${b}_${a}"
    }
}
