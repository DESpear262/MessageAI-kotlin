package com.messageai.tactical.data.remote

import com.google.truth.Truth.assertThat
import org.junit.Test

/**
 * Unit tests for [FirestorePaths].
 *
 * Verifies deterministic chat ID generation and path constants.
 */
class FirestorePathsTest {

    @Test
    fun `directChatId is deterministic for same pair`() {
        val id1 = FirestorePaths.directChatId("user1", "user2")
        val id2 = FirestorePaths.directChatId("user1", "user2")
        assertThat(id1).isEqualTo(id2)
    }

    @Test
    fun `directChatId is order-independent`() {
        val id1 = FirestorePaths.directChatId("user1", "user2")
        val id2 = FirestorePaths.directChatId("user2", "user1")
        assertThat(id1).isEqualTo(id2)
    }

    @Test
    fun `directChatId orders lexicographically`() {
        val id = FirestorePaths.directChatId("userB", "userA")
        assertThat(id).isEqualTo("userA_userB")
    }

    @Test
    fun `directChatId handles identical UIDs`() {
        val id = FirestorePaths.directChatId("user1", "user1")
        assertThat(id).isEqualTo("user1_user1")
    }

    @Test
    fun `directChatId trims whitespace`() {
        val id = FirestorePaths.directChatId(" user1 ", " user2 ")
        assertThat(id).isEqualTo("user1_user2")
    }

    @Test
    fun `directChatId handles numeric IDs`() {
        val id = FirestorePaths.directChatId("123", "456")
        assertThat(id).isEqualTo("123_456")
    }

    @Test
    fun `directChatId handles UUIDs`() {
        val uuid1 = "550e8400-e29b-41d4-a716-446655440000"
        val uuid2 = "6ba7b810-9dad-11d1-80b4-00c04fd430c8"
        val id1 = FirestorePaths.directChatId(uuid1, uuid2)
        val id2 = FirestorePaths.directChatId(uuid2, uuid1)
        assertThat(id1).isEqualTo(id2)
        assertThat(id1).contains(uuid1)
        assertThat(id1).contains(uuid2)
    }

    @Test
    fun `collection constants are correct`() {
        assertThat(FirestorePaths.USERS).isEqualTo("users")
        assertThat(FirestorePaths.CHATS).isEqualTo("chats")
        assertThat(FirestorePaths.GROUPS).isEqualTo("groups")
        assertThat(FirestorePaths.MESSAGES).isEqualTo("messages")
    }
}


