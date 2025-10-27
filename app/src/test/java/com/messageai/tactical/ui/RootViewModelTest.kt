package com.messageai.tactical.ui

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.common.truth.Truth.assertThat
import com.messageai.tactical.data.remote.RtdbPresenceService
import com.messageai.tactical.data.db.AppDatabase
import com.messageai.tactical.data.remote.ChatService
import com.messageai.tactical.data.remote.MessageListener
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Unit tests for [RootViewModel].
 *
 * Verifies authentication state management, logout flow, and state refresh.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RootViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var auth: FirebaseAuth
    private lateinit var presenceService: RtdbPresenceService
    private lateinit var viewModel: RootViewModel
    private lateinit var db: AppDatabase
    private lateinit var chatService: ChatService
    private lateinit var messageListener: MessageListener

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        auth = mockk(relaxed = true)
        presenceService = mockk(relaxed = true)
        db = mockk(relaxed = true)
        chatService = mockk(relaxed = true)
        messageListener = mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test
    fun `initial state is authenticated when user exists`() {
        val mockUser: FirebaseUser = mockk()
        every { auth.currentUser } returns mockUser

        viewModel = RootViewModel(auth, presenceService, db, chatService, messageListener)

        assertThat(viewModel.isAuthenticated.value).isTrue()
        verify { presenceService.goOnline() }
    }

    @Test
    fun `initial state is not authenticated when user is null`() {
        every { auth.currentUser } returns null

        viewModel = RootViewModel(auth, presenceService, db, chatService, messageListener)

        assertThat(viewModel.isAuthenticated.value).isFalse()
        verify(exactly = 0) { presenceService.goOnline() }
    }

    @Test
    fun `refreshAuthState updates authentication status to true`() {
        every { auth.currentUser } returns null
        viewModel = RootViewModel(auth, presenceService, db, chatService, messageListener)
        assertThat(viewModel.isAuthenticated.value).isFalse()

        val mockUser: FirebaseUser = mockk()
        every { auth.currentUser } returns mockUser

        viewModel.refreshAuthState()

        assertThat(viewModel.isAuthenticated.value).isTrue()
        verify(atLeast = 1) { presenceService.goOnline() }
    }

    @Test
    fun `refreshAuthState updates authentication status to false`() {
        val mockUser: FirebaseUser = mockk()
        every { auth.currentUser } returns mockUser
        viewModel = RootViewModel(auth, presenceService, db, chatService, messageListener)
        assertThat(viewModel.isAuthenticated.value).isTrue()

        every { auth.currentUser } returns null

        viewModel.refreshAuthState()

        assertThat(viewModel.isAuthenticated.value).isFalse()
    }

    @Test
    fun `logout calls signOut and updates state`() = runTest {
        val mockUser: FirebaseUser = mockk()
        every { auth.currentUser } returns mockUser
        every { auth.signOut() } just Runs

        viewModel = RootViewModel(auth, presenceService, db, chatService, messageListener)
        assertThat(viewModel.isAuthenticated.value).isTrue()

        viewModel.logout()
        testDispatcher.scheduler.advanceUntilIdle()

        verify { auth.signOut() }
        verify { presenceService.goOffline() }
        assertThat(viewModel.isAuthenticated.value).isFalse()
    }

    @Test
    fun `multiple refreshAuthState calls work correctly`() {
        every { auth.currentUser } returns null
        viewModel = RootViewModel(auth, presenceService, db, chatService, messageListener)

        val mockUser: FirebaseUser = mockk()

        // First refresh: authenticated
        every { auth.currentUser } returns mockUser
        viewModel.refreshAuthState()
        assertThat(viewModel.isAuthenticated.value).isTrue()

        // Second refresh: not authenticated
        every { auth.currentUser } returns null
        viewModel.refreshAuthState()
        assertThat(viewModel.isAuthenticated.value).isFalse()

        // Third refresh: authenticated again
        every { auth.currentUser } returns mockUser
        viewModel.refreshAuthState()
        assertThat(viewModel.isAuthenticated.value).isTrue()
    }
}


