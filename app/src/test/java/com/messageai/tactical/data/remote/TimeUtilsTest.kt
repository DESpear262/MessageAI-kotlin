package com.messageai.tactical.data.remote

import com.google.firebase.Timestamp
import com.google.truth.Truth.assertThat
import org.junit.Test

/**
 * Unit tests for [TimeUtils].
 *
 * Verifies epoch conversion, LWW timestamp resolution, and edge cases.
 */
class TimeUtilsTest {

    @Test
    fun `toEpochMillis returns null for null timestamp`() {
        val result = TimeUtils.toEpochMillis(null)
        assertThat(result).isNull()
    }

    @Test
    fun `toEpochMillis converts valid timestamp correctly`() {
        val timestamp = Timestamp(1609459200, 500_000_000) // 2021-01-01 00:00:00.500
        val result = TimeUtils.toEpochMillis(timestamp)
        assertThat(result).isEqualTo(1609459200500L)
    }

    @Test
    fun `toEpochMillis handles zero nanoseconds`() {
        val timestamp = Timestamp(1609459200, 0)
        val result = TimeUtils.toEpochMillis(timestamp)
        assertThat(result).isEqualTo(1609459200000L)
    }

    @Test
    fun `toEpochMillis handles maximum nanoseconds`() {
        val timestamp = Timestamp(1609459200, 999_999_999)
        val result = TimeUtils.toEpochMillis(timestamp)
        assertThat(result).isEqualTo(1609459200999L)
    }

    @Test
    fun `lwwMillis prefers server timestamp over client timestamp`() {
        val serverTs = Timestamp(1609459300, 0) // 100 seconds later
        val clientMillis = 1609459200000L
        val result = TimeUtils.lwwMillis(serverTs, clientMillis)
        assertThat(result).isEqualTo(1609459300000L)
    }

    @Test
    fun `lwwMillis falls back to client timestamp when server is null`() {
        val clientMillis = 1609459200000L
        val result = TimeUtils.lwwMillis(null, clientMillis)
        assertThat(result).isEqualTo(clientMillis)
    }

    @Test
    fun `lwwMillis uses fallback when both are null`() {
        val fallback = 1234567890000L
        val result = TimeUtils.lwwMillis(null, null, fallback)
        assertThat(result).isEqualTo(fallback)
    }

    @Test
    fun `lwwMillis uses current time fallback when not specified`() {
        val before = System.currentTimeMillis()
        val result = TimeUtils.lwwMillis(null, null)
        val after = System.currentTimeMillis()
        assertThat(result).isAtLeast(before)
        assertThat(result).isAtMost(after)
    }

    @Test
    fun `lwwMillis handles zero timestamps`() {
        val serverTs = Timestamp(0, 0)
        val result = TimeUtils.lwwMillis(serverTs, null)
        assertThat(result).isEqualTo(0L)
    }
}


