package com.messageai.tactical.ui.auth

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.google.android.gms.tasks.OnFailureListener
import com.google.android.gms.tasks.OnSuccessListener
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.AuthResult
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.*
import com.google.common.truth.Truth.assertThat
import io.mockk.*
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Unit tests for [AuthViewModel].
 *
 * Verifies login, registration, password reset validation and error handling.
 */
class AuthViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private lateinit var viewModel: AuthViewModel

    @Before
    fun setup() {
        auth = mockk(relaxed = true)
        firestore = mockk(relaxed = true)
        viewModel = AuthViewModel(auth, firestore)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `login with blank email shows error`() {
        viewModel.login("", "password123") {}

        assertThat(viewModel.error.value).isEqualTo("Invalid email or password")
    }

    @Test
    fun `login with short password shows error`() {
        viewModel.login("test@example.com", "12345") {}

        assertThat(viewModel.error.value).isEqualTo("Invalid email or password")
    }

    @Test
    fun `login with valid credentials calls Firebase`() {
        val mockTask: Task<AuthResult> = mockk(relaxed = true)
        every { auth.signInWithEmailAndPassword(any(), any()) } returns mockTask
        every { mockTask.addOnSuccessListener(any()) } answers {
            val listener = firstArg<OnSuccessListener<AuthResult>>()
            listener.onSuccess(mockk())
            mockTask
        }
        every { mockTask.addOnFailureListener(any()) } returns mockTask

        var successCalled = false
        viewModel.login("test@example.com", "password123") { successCalled = true }

        verify { auth.signInWithEmailAndPassword("test@example.com", "password123") }
        assertThat(successCalled).isTrue()
    }

    @Test
    fun `login failure updates error state`() {
        val mockTask: Task<AuthResult> = mockk(relaxed = true)
        val exception = Exception("Invalid credentials")
        every { auth.signInWithEmailAndPassword(any(), any()) } returns mockTask
        every { mockTask.addOnSuccessListener(any()) } returns mockTask
        every { mockTask.addOnFailureListener(any()) } answers {
            val listener = firstArg<OnFailureListener>()
            listener.onFailure(exception)
            mockTask
        }

        viewModel.login("test@example.com", "password123") {}

        assertThat(viewModel.error.value).isEqualTo("Invalid credentials")
    }

    @Test
    fun `register with blank email shows error`() {
        viewModel.register("", "password123", "John Doe") {}

        assertThat(viewModel.error.value).isEqualTo("Enter name, valid email, and 6+ char password")
    }

    @Test
    fun `register with short password shows error`() {
        viewModel.register("test@example.com", "12345", "John Doe") {}

        assertThat(viewModel.error.value).isEqualTo("Enter name, valid email, and 6+ char password")
    }

    @Test
    fun `register with blank display name shows error`() {
        viewModel.register("test@example.com", "password123", "") {}

        assertThat(viewModel.error.value).isEqualTo("Enter name, valid email, and 6+ char password")
    }

    @Test
    fun `register with valid data creates user and Firestore doc`() {
        // Mock user creation
        val mockAuthTask: Task<AuthResult> = mockk(relaxed = true)
        val mockAuthResult: AuthResult = mockk()
        val mockUser: FirebaseUser = mockk(relaxed = true)
        
        every { auth.createUserWithEmailAndPassword(any(), any()) } returns mockAuthTask
        every { mockAuthTask.addOnSuccessListener(any()) } answers {
            val listener = firstArg<OnSuccessListener<AuthResult>>()
            listener.onSuccess(mockAuthResult)
            mockAuthTask
        }
        every { mockAuthTask.addOnFailureListener(any()) } returns mockAuthTask
        every { mockAuthResult.user } returns mockUser
        every { mockUser.uid } returns "user123"
        every { mockUser.email } returns "test@example.com"

        // Mock profile update
        val mockProfileTask: Task<Void> = mockk(relaxed = true)
        every { mockUser.updateProfile(any()) } returns mockProfileTask
        every { mockProfileTask.addOnCompleteListener(any()) } answers {
            val listener = firstArg<com.google.android.gms.tasks.OnCompleteListener<Void>>()
            listener.onComplete(mockProfileTask)
            mockProfileTask
        }

        // Mock Firestore
        val mockCollection: CollectionReference = mockk()
        val mockDocument: DocumentReference = mockk(relaxed = true)
        val mockDocTask: Task<Void> = mockk(relaxed = true)
        
        every { firestore.collection("users") } returns mockCollection
        every { mockCollection.document("user123") } returns mockDocument
        every { mockDocument.set(any()) } returns mockDocTask
        every { mockDocTask.addOnSuccessListener(any()) } answers {
            val listener = firstArg<OnSuccessListener<Void>>()
            listener.onSuccess(null)
            mockDocTask
        }
        every { mockDocTask.addOnFailureListener(any()) } returns mockDocTask

        var successCalled = false
        viewModel.register("test@example.com", "password123", "John Doe") { 
            successCalled = true 
        }

        verify { auth.createUserWithEmailAndPassword("test@example.com", "password123") }
        verify { mockUser.updateProfile(any()) }
        verify { mockDocument.set(any()) }
        assertThat(successCalled).isTrue()
    }

    @Test
    fun `checkUserAndSendReset with blank email shows error`() {
        viewModel.checkUserAndSendReset("")

        assertThat(viewModel.error.value).isEqualTo("Enter email")
    }

    @Test
    fun `checkUserAndSendReset with non-existent user shows error`() {
        val mockCollection: CollectionReference = mockk()
        val mockQuery: Query = mockk(relaxed = true)
        val mockTask: Task<QuerySnapshot> = mockk(relaxed = true)
        val mockSnapshot: QuerySnapshot = mockk()

        every { firestore.collection("users") } returns mockCollection
        every { mockCollection.whereEqualTo("email", any()) } returns mockQuery
        every { mockQuery.limit(1) } returns mockQuery
        every { mockQuery.get() } returns mockTask
        every { mockTask.addOnSuccessListener(any()) } answers {
            val listener = firstArg<OnSuccessListener<QuerySnapshot>>()
            listener.onSuccess(mockSnapshot)
            mockTask
        }
        every { mockTask.addOnFailureListener(any()) } returns mockTask
        every { mockSnapshot.isEmpty } returns true

        viewModel.checkUserAndSendReset("test@example.com")

        assertThat(viewModel.error.value).isEqualTo("That account doesn't exist")
    }

    @Test
    fun `checkUserAndSendReset sends reset link for existing user`() {
        val mockCollection: CollectionReference = mockk()
        val mockQuery: Query = mockk(relaxed = true)
        val mockQueryTask: Task<QuerySnapshot> = mockk(relaxed = true)
        val mockSnapshot: QuerySnapshot = mockk()
        val mockResetTask: Task<Void> = mockk(relaxed = true)

        every { firestore.collection("users") } returns mockCollection
        every { mockCollection.whereEqualTo("email", any()) } returns mockQuery
        every { mockQuery.limit(1) } returns mockQuery
        every { mockQuery.get() } returns mockQueryTask
        every { mockQueryTask.addOnSuccessListener(any()) } answers {
            val listener = firstArg<OnSuccessListener<QuerySnapshot>>()
            listener.onSuccess(mockSnapshot)
            mockQueryTask
        }
        every { mockQueryTask.addOnFailureListener(any()) } returns mockQueryTask
        every { mockSnapshot.isEmpty } returns false

        every { auth.sendPasswordResetEmail(any()) } returns mockResetTask
        every { mockResetTask.addOnSuccessListener(any()) } answers {
            val listener = firstArg<OnSuccessListener<Void>>()
            listener.onSuccess(null)
            mockResetTask
        }
        every { mockResetTask.addOnFailureListener(any()) } returns mockResetTask

        viewModel.checkUserAndSendReset("test@example.com")

        verify { auth.sendPasswordResetEmail("test@example.com") }
        assertThat(viewModel.error.value).isEqualTo("Reset link sent")
    }
}


