package com.messageai.tactical.data.remote

import com.google.firebase.Timestamp

object TimeUtils {
    fun toEpochMillis(ts: Timestamp?): Long? = ts?.seconds?.times(1000)?.plus(ts.nanoseconds / 1_000_000)

    // Last-write-wins: prefer server timestamp when available, else client timestamp
    fun lwwMillis(serverTs: Timestamp?, clientMillis: Long?, fallback: Long = System.currentTimeMillis()): Long {
        return toEpochMillis(serverTs) ?: clientMillis ?: fallback
    }
}
