/**
 * MessageAI – Time utilities for Firestore/RTDB interoperability.
 *
 * Provides helpers to convert Firestore `Timestamp` values to epoch millis and
 * a last-write-wins (LWW) resolver that prefers authoritative server time while
 * falling back to client timestamps when necessary.
 */
package com.messageai.tactical.data.remote

import com.google.firebase.Timestamp

object TimeUtils {
    /** Converts a Firestore [Timestamp] to epoch millis, or null if absent. */
    fun toEpochMillis(ts: Timestamp?): Long? = ts?.seconds?.times(1000)?.plus(ts.nanoseconds / 1_000_000)

    /**
     * Last-write-wins: prefer server timestamp when available, else client timestamp.
     *
     * If both are missing, use [fallback] (defaults to current time).
     */
    fun lwwMillis(serverTs: Timestamp?, clientMillis: Long?, fallback: Long = System.currentTimeMillis()): Long {
        return toEpochMillis(serverTs) ?: clientMillis ?: fallback
    }
}
