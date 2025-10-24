package com.messageai.tactical.modules.missions

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MissionService @Inject constructor(
    private val db: FirebaseFirestore
) {
    private fun missionsCol() = db.collection(COL_MISSIONS)
    private fun tasksCol(missionId: String) = missionsCol().document(missionId).collection(COL_TASKS)

    suspend fun createMission(m: Mission): String {
        val ref = missionsCol().document()
        val now = System.currentTimeMillis()
        val data = hashMapOf(
            "chatId" to m.chatId,
            "title" to m.title,
            "description" to m.description,
            "status" to m.status,
            "priority" to m.priority,
            "assignees" to m.assignees,
            "createdAt" to (m.createdAt.takeIf { it > 0 } ?: now),
            "updatedAt" to now,
            "dueAt" to m.dueAt,
            "tags" to m.tags,
            "archived" to false,
            "sourceMsgId" to m.sourceMsgId
        ).filterValues { it != null }
        ref.set(data).await()
        return ref.id
    }

    suspend fun addTask(missionId: String, t: MissionTask): String {
        val ref = tasksCol(missionId).document()
        val now = System.currentTimeMillis()
        val data = hashMapOf(
            "title" to t.title,
            "description" to t.description,
            "status" to t.status,
            "priority" to t.priority,
            "assignees" to t.assignees,
            "createdAt" to (t.createdAt.takeIf { it > 0 } ?: now),
            "updatedAt" to now,
            "dueAt" to t.dueAt,
            "sourceMsgId" to t.sourceMsgId
        ).filterValues { it != null }
        ref.set(data).await()
        return ref.id
    }

    suspend fun updateMission(missionId: String, fields: Map<String, Any?>) {
        missionsCol().document(missionId).update(fields.filterValues { it != null }).await()
    }

    suspend fun updateTask(missionId: String, taskId: String, fields: Map<String, Any?>) {
        tasksCol(missionId).document(taskId).update(fields.filterValues { it != null }).await()
    }

    fun observeMissions(chatId: String, includeArchived: Boolean = false): Flow<List<Pair<String, Mission>>> = callbackFlow {
        var query: Query = missionsCol().whereEqualTo("chatId", chatId).orderBy("updatedAt", Query.Direction.DESCENDING).limit(100)
        if (!includeArchived) query = query.whereEqualTo("archived", false)
        val reg = query.addSnapshotListener { snap, _ ->
            val list = snap?.documents?.map { d ->
                val m = Mission(
                    id = d.id,
                    chatId = d.getString("chatId") ?: "",
                    title = d.getString("title") ?: "",
                    description = d.getString("description"),
                    status = d.getString("status") ?: "open",
                    priority = (d.get("priority") as? Number)?.toInt() ?: 3,
                    assignees = (d.get("assignees") as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                    createdAt = (d.get("createdAt") as? Number)?.toLong() ?: 0L,
                    updatedAt = (d.get("updatedAt") as? Number)?.toLong() ?: 0L,
                    dueAt = (d.get("dueAt") as? Number)?.toLong(),
                    tags = (d.get("tags") as? List<*>)?.filterIsInstance<String>(),
                    archived = d.getBoolean("archived") ?: false,
                    sourceMsgId = d.getString("sourceMsgId")
                )
                d.id to m
            } ?: emptyList()
            trySend(list)
        }
        awaitClose { reg.remove() }
    }

    fun observeTasks(missionId: String): Flow<List<Pair<String, MissionTask>>> = callbackFlow {
        val reg = tasksCol(missionId).orderBy("updatedAt", Query.Direction.DESCENDING).limit(200).addSnapshotListener { snap, _ ->
            val list = snap?.documents?.map { d ->
                val t = MissionTask(
                    id = d.id,
                    missionId = missionId,
                    title = d.getString("title") ?: "",
                    description = d.getString("description"),
                    status = d.getString("status") ?: "open",
                    priority = (d.get("priority") as? Number)?.toInt() ?: 3,
                    assignees = (d.get("assignees") as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                    createdAt = (d.get("createdAt") as? Number)?.toLong() ?: 0L,
                    updatedAt = (d.get("updatedAt") as? Number)?.toLong() ?: 0L,
                    dueAt = (d.get("dueAt") as? Number)?.toLong(),
                    sourceMsgId = d.getString("sourceMsgId")
                )
                d.id to t
            } ?: emptyList()
            trySend(list)
        }
        awaitClose { reg.remove() }
    }

    suspend fun archiveIfCompleted(missionId: String) {
        val tasks = tasksCol(missionId).get().await()
        val allDone = tasks.documents.all { (it.getString("status") ?: "open") == "done" }
        if (allDone) {
            missionsCol().document(missionId).update(mapOf("archived" to true, "updatedAt" to System.currentTimeMillis())).await()
        }
    }

    companion object {
        private const val COL_MISSIONS = "missions"
        private const val COL_TASKS = "tasks"
    }
}


