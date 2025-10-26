package com.messageai.tactical.modules.missions

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Mission planner service for CRUD operations and realtime observation.
 *
 * Observability:
 * - Emits JSON-structured logs for key operations with a stable `event` name and parameters.
 * - Use these to correlate UI issues (e.g., missing updates) with Firestore state transitions.
 */
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
            "sourceMsgId" to m.sourceMsgId,
            "casevacCasualties" to m.casevacCasualties
        ).filterValues { it != null }
        ref.set(data).await()
        Log.i(TAG, json(
            "event" to "mission_create",
            "missionId" to ref.id,
            "chatId" to m.chatId,
            "title" to m.title,
            "status" to m.status,
            "priority" to m.priority
        ))
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
        Log.i(TAG, json(
            "event" to "mission_task_add",
            "missionId" to missionId,
            "taskId" to ref.id,
            "title" to t.title,
            "status" to t.status,
            "priority" to t.priority
        ))
        return ref.id
    }

    suspend fun updateMission(missionId: String, fields: Map<String, Any?>) {
        missionsCol().document(missionId).update(fields.filterValues { it != null }).await()
        Log.d(TAG, json(
            "event" to "mission_update",
            "missionId" to missionId,
            "fields" to fields.keys.joinToString(",")
        ))
    }

    suspend fun incrementCasevacCasualties(chatId: String, delta: Int = 1) {
        // Find latest open CASEVAC mission for this chat
        val snap = missionsCol()
            .whereEqualTo("chatId", chatId)
            .whereEqualTo("archived", false)
            .whereEqualTo("title", "CASEVAC")
            .orderBy("updatedAt", Query.Direction.DESCENDING)
            .limit(1)
            .get().await()
        val doc = snap.documents.firstOrNull() ?: run {
            Log.w(TAG, json(
                "event" to "mission_casevac_increment_skipped",
                "chatId" to chatId,
                "reason" to "no_open_casevac"
            ))
            return
        }
        val current = (doc.get("casevacCasualties") as? Number)?.toInt() ?: 0
        missionsCol().document(doc.id).update(
            mapOf(
                "casevacCasualties" to (current + delta),
                "updatedAt" to System.currentTimeMillis()
            )
        ).await()
        Log.i(TAG, json(
            "event" to "mission_casevac_increment",
            "missionId" to doc.id,
            "chatId" to chatId,
            "delta" to delta,
            "newCount" to (current + delta)
        ))
    }

    suspend fun updateTask(missionId: String, taskId: String, fields: Map<String, Any?>) {
        tasksCol(missionId).document(taskId).update(fields.filterValues { it != null }).await()
    }

    fun observeMissions(chatId: String, includeArchived: Boolean = false): Flow<List<Pair<String, Mission>>> = callbackFlow {
        Log.d(TAG, json("event" to "missions_observe_start", "mode" to "chat", "chatId" to chatId, "includeArchived" to includeArchived))
        val query: Query = missionsCol().whereEqualTo("chatId", chatId).orderBy("updatedAt", Query.Direction.DESCENDING).limit(100)
        val reg = query.addSnapshotListener { snap, err ->
            if (err != null) {
                Log.e(TAG, json(
                    "event" to "missions_observe_error",
                    "chatId" to chatId,
                    "message" to (err.message ?: "unknown")
                ))
                return@addSnapshotListener
            }
            val all = snap?.documents?.map { d ->
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
            val list = if (includeArchived) all else all.filter { !it.second.archived }
            trySend(list)
            Log.d(TAG, json(
                "event" to "missions_observe_emit",
                "chatId" to chatId,
                "count" to (list?.size ?: 0)
            ))
        }
        awaitClose { reg.remove() }
    }

    /**
     * Observe all missions regardless of chat for MVP visibility. In a future
     * iteration, this can be restricted by assignees/roles.
     */
    fun observeMissionsGlobal(includeArchived: Boolean = false): Flow<List<Pair<String, Mission>>> = callbackFlow {
        Log.d(TAG, json("event" to "missions_observe_start", "mode" to "global", "includeArchived" to includeArchived))
        val query: Query = missionsCol().orderBy("updatedAt", Query.Direction.DESCENDING).limit(200)
        val reg = query.addSnapshotListener { snap, err ->
            if (err != null) {
                Log.e(TAG, json(
                    "event" to "missions_observe_global_error",
                    "message" to (err.message ?: "unknown")
                ))
                return@addSnapshotListener
            }
            val all = snap?.documents?.map { d ->
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
            val list = if (includeArchived) all else all.filter { !it.second.archived }
            trySend(list)
            Log.d(TAG, json(
                "event" to "missions_observe_global_emit",
                "count" to (list?.size ?: 0)
            ))
        }
        awaitClose { reg.remove() }
    }

    fun observeTasks(missionId: String): Flow<List<Pair<String, MissionTask>>> = callbackFlow {
        val reg = tasksCol(missionId).orderBy("updatedAt", Query.Direction.DESCENDING).limit(200).addSnapshotListener { snap, err ->
            if (err != null) {
                Log.e(TAG, json(
                    "event" to "tasks_observe_error",
                    "missionId" to missionId,
                    "message" to (err.message ?: "unknown")
                ))
                return@addSnapshotListener
            }
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
            Log.d(TAG, json(
                "event" to "tasks_observe_emit",
                "missionId" to missionId,
                "count" to (list?.size ?: 0)
            ))
        }
        awaitClose { reg.remove() }
    }

    suspend fun archiveIfCompleted(missionId: String) {
        val tasks = tasksCol(missionId).get().await()
        val allDone = tasks.documents.all { (it.getString("status") ?: "open") == "done" }
        if (allDone) {
            missionsCol().document(missionId).update(mapOf("archived" to true, "updatedAt" to System.currentTimeMillis())).await()
            Log.i(TAG, json(
                "event" to "mission_archive",
                "missionId" to missionId
            ))
        }
    }

    companion object {
        private const val TAG = "MissionService"
        private const val COL_MISSIONS = "missions"
        private const val COL_TASKS = "tasks"
    }

    private fun json(vararg pairs: Pair<String, Any?>): String {
        return buildString {
            append('{')
            pairs.forEachIndexed { index, (k, v) ->
                if (index > 0) append(',')
                append('"').append(k).append('"').append(':')
                when (v) {
                    null -> append("null")
                    is Number, is Boolean -> append(v.toString())
                    else -> {
                        val s = v.toString().replace("\\", "\\\\").replace("\"", "\\\"")
                        append('"').append(s).append('"')
                    }
                }
            }
            append('}')
        }
    }
}


