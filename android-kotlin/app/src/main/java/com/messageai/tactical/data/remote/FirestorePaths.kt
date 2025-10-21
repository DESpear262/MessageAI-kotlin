package com.messageai.tactical.data.remote

object FirestorePaths {
    const val USERS = "users"
    const val CHATS = "chats"
    const val GROUPS = "groups"
    const val MESSAGES = "messages"

    fun directChatId(uidA: String, uidB: String): String {
        val a = uidA.trim()
        val b = uidB.trim()
        return if (a <= b) "${'$'}a_${'$'}b" else "${'$'}b_${'$'}a"
    }
}
