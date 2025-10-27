package com.messageai.tactical.modules.missions

data class Mission(
    val id: String = "",
    val chatId: String,
    val title: String,
    val description: String? = null,
    val status: String = "open", // open | in_progress | done
    val priority: Int = 3,
    val assignees: List<String> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val dueAt: Long? = null,
    val tags: List<String>? = null,
    val archived: Boolean = false,
    val sourceMsgId: String? = null,
    val casevacCasualties: Int = 0
)

data class MissionTask(
    val id: String = "",
    val missionId: String,
    val title: String,
    val description: String? = null,
    val status: String = "open",
    val priority: Int = 3,
    val assignees: List<String> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val dueAt: Long? = null,
    val sourceMsgId: String? = null
)


