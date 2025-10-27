# MessageAI Product Requirements Document
## Kotlin (Android Native) Implementation

**Project Type**: Tactical Messaging Application with AI Capabilities  
**Target Platform**: Android (Primary)  
**Development Framework**: Kotlin with Jetpack Compose  
**Timeline**: MVP in 24 hours, Full deployment in 7 days  
**Deployment**: Android Emulator (MVP), APK (Final)

---

## Executive Summary

MessageAI is a tactical communication system designed for reliable messaging in challenging environments. The MVP focuses on core messaging infrastructure with real-time sync, offline persistence, and group coordination. Post-MVP development will add tactical features including precise geolocation, end-to-end encryption, NATO-standard communication templates, and mesh networking for denied environments.

---

## User Stories

### Core Messaging
- **As a user**, I want to send text messages to another user and see them appear instantly, so I can communicate in real-time
- **As a user**, I want my messages to persist locally, so I can access chat history even when offline
- **As a user**, I want to see when my message is sent, delivered, and read, so I know the status of my communications
- **As a user**, I want to see when the other person is online/offline, so I know if they're available
- **As a user**, I want to see message timestamps, so I can track when communications occurred
- **As a user**, I want my sent messages to appear immediately in the UI, so the app feels responsive even on poor connections

### Authentication
- **As a user**, I want to create an account with email and password, so I can access the app securely
- **As a user**, I want to reset my password if I forget it, so I can regain access to my account
- **As a user**, I want to see my profile with display name and picture, so others can identify me

### Group Communication
- **As a user**, I want to create a group chat with 3+ people, so I can coordinate with my team
- **As a user**, I want to name group chats, so I can organize different conversations
- **As a user**, I want to see who sent each message in a group, so I can track communication sources
- **As a user**, I want to see read receipts for each member, so I know who has seen critical information

### Media
- **As a user**, I want to send images from my gallery, so I can share visual information
- **As a user**, I want to preview images before sending, so I can confirm I'm sharing the right content

### Notifications
- **As a user**, I want to receive notifications when I get new messages, so I don't miss important communications

---

## MVP Feature Requirements

### 1. Authentication System
**Priority**: P0 (Blocker)

**Requirements**:
- Email/password registration with basic validation (email format, password min 8 characters)
- Login flow with error handling
- Forgot password flow using Firebase Auth
- Profile creation (display name, optional profile picture)
- Persistent authentication state

**Technical Implementation**:
- Firebase Authentication
- Android Keystore for token persistence
- Form validation with email regex, password strength check

**Acceptance Criteria**:
- [ ] User can register with valid email/password
- [ ] User can log in and stay logged in across app restarts
- [ ] User can reset password via email link
- [ ] Invalid credentials show appropriate error messages
- [ ] User profile displays name and picture (or default avatar)

---

### 2. One-on-One Chat
**Priority**: P0 (Blocker)

**Requirements**:
- Text message sending/receiving between two users
- Real-time message delivery (messages appear within 1 second for online users)
- Message persistence using local Room database
- Optimistic UI updates (messages appear instantly before server confirmation)
- Message states: sending → sent → delivered → read
- Per-message timestamps (displayed in chat)
- Online/offline presence indicators
- Typing indicators
- Message history loads from local database on app start

**Technical Implementation**:
- Firestore for real-time message sync
- Room Database for local persistence
- Optimistic updates using local state + Firestore listeners
- Presence tracking via Firestore with onDisconnect handlers

**Acceptance Criteria**:
- [ ] Messages appear instantly for sender (optimistic update)
- [ ] Messages appear within 1 second for online recipient
- [ ] Messages persist across app restarts
- [ ] Message delivery states update correctly (sent/delivered/read)
- [ ] User can see when other person is online/offline
- [ ] User can see when other person is typing
- [ ] Timestamps are accurate and timezone-aware

---

### 3. Group Chat
**Priority**: P0 (Blocker)

**Requirements**:
- Create group chat with 3+ users at creation time
- User-defined group name
- Message attribution (sender name/avatar shown for each message)
- Per-user read receipts (show who has read each message)
- All messages persist locally
- Real-time delivery to all group members

**Technical Implementation**:
- Firestore collection for groups with member array
- Messages subcollection with sender ID
- Read receipts tracked per message per user
- Group list view showing latest message preview

**Acceptance Criteria**:
- [ ] User can create a group with 3+ members
- [ ] User can set/edit group name
- [ ] Each message shows sender's name and avatar
- [ ] Read receipts show which members have read each message
- [ ] Group messages sync in real-time for all online members
- [ ] Group chat history persists locally

**🏗️ Architecture Note**: Design group member storage to support dynamic add/remove operations post-MVP. Use array of user IDs with metadata structure that can accommodate role information later.

---

### 4. Media Sharing
**Priority**: P0 (Blocker)

**Requirements**:
- Send images from device gallery
- Image preview before sending
- Images display inline in chat
- Images persist locally with messages

**Technical Implementation**:
- Android MediaStore for gallery access
- Coil or Glide for image loading/caching
- Firebase Storage for image hosting
- Thumbnail generation for chat list

**Acceptance Criteria**:
- [ ] User can select image from gallery
- [ ] User sees preview before sending
- [ ] Image uploads and appears in chat for both users
- [ ] Images are cached locally for offline viewing
- [ ] Image loading shows progress indicator

**🏗️ Architecture Note**: Design media storage with metadata structure that can support file types beyond images (documents, voice, video) and geolocation pins post-MVP.

---

### 5. Push Notifications
**Priority**: P0 (Blocker)

**Requirements**:
- Receive notifications for new messages (foreground minimum)
- Notifications show sender name and message preview
- Tapping notification opens relevant chat

**Technical Implementation**:
- Firebase Cloud Messaging (FCM)
- Android Notification channels (NotificationManager)
- Cloud Functions to trigger notifications on new messages
- WorkManager for background message polling (if needed)

**Acceptance Criteria**:
- [ ] Foreground notifications work reliably
- [ ] Notification shows sender and message preview
- [ ] Tapping notification navigates to correct chat
- [ ] Notifications don't duplicate

---

### 6. Offline Support & Sync
**Priority**: P0 (Blocker)

**Requirements**:
- App functions fully offline (can read message history)
- Messages sent while offline queue and send when connection returns
- Incoming messages received when app comes back online
- No message loss on app crash or force-quit
- Graceful handling of poor network conditions

**Technical Implementation**:
- Room Database for local message storage
- WorkManager for background sync and retry logic
- Firestore offline persistence
- ConnectivityManager for network state detection

**Acceptance Criteria**:
- [ ] User can view chat history with no internet
- [ ] Messages sent offline queue and send on reconnect
- [ ] App receives queued messages when coming online
- [ ] Force-quit during send doesn't lose message
- [ ] App handles airplane mode → reconnect gracefully

**🏗️ Architecture Note**: Implement message queue with conflict resolution strategy (timestamp-based last-write-wins for MVP). Structure allows upgrade to operational transforms or CRDTs for mesh networking post-MVP. WorkManager provides robust background task scheduling that will be essential for mesh networking.

---

## Technical Stack

### Frontend
- **Language**: Kotlin
- **UI Framework**: Jetpack Compose
- **Navigation**: Compose Navigation
- **Dependency Injection**: Hilt (Dagger)
- **Local Storage**: Room Database + DataStore
- **Image Loading**: Coil
- **Network**: Retrofit (if needed for custom APIs)
- **Concurrency**: Coroutines + Flow

### Backend
- **Database**: Firebase Firestore
- **Authentication**: Firebase Auth
- **Storage**: Firebase Storage
- **Push Notifications**: Firebase Cloud Messaging (FCM)
- **Functions**: Firebase Cloud Functions (for AI features post-MVP)

### Development & Deployment
- **Build System**: Gradle with Kotlin DSL
- **Testing**: JUnit, Espresso, Compose UI Testing
- **Deployment**: APK via Android Studio, future Play Store internal testing

---

## Data Schema

### User Document
```kotlin
data class User(
    val uid: String,
    val email: String,
    val displayName: String,
    val photoURL: String? = null,
    val createdAt: Timestamp,
    val lastSeen: Timestamp,
    val isOnline: Boolean = false,
    val fcmToken: String? = null
)
```

### Chat Document (One-on-One)
```kotlin
data class Chat(
    val id: String,
    val participants: List<String>, // User IDs
    val participantDetails: Map<String, ParticipantInfo>,
    val lastMessage: LastMessage? = null,
    val createdAt: Timestamp,
    val updatedAt: Timestamp
)

data class ParticipantInfo(
    val name: String,
    val photoUrl: String?
)

data class LastMessage(
    val text: String,
    val senderId: String,
    val timestamp: Timestamp
)
```

### Message Document
```kotlin
data class Message(
    val id: String,
    val chatId: String,
    val senderId: String,
    val text: String,
    val imageUrl: String? = null,
    val timestamp: Timestamp,
    val status: MessageStatus,
    val readBy: List<String> = emptyList(), // For group chats
    val localOnly: Boolean = false // For optimistic updates
)

enum class MessageStatus {
    SENDING, SENT, DELIVERED, READ
}
```

### Group Document
```kotlin
data class Group(
    val id: String,
    val name: String,
    val members: List<String>, // User IDs
    val memberDetails: Map<String, ParticipantInfo>,
    val createdBy: String,
    val createdAt: Timestamp,
    val lastMessage: LastMessage? = null
)
```

**🏗️ Architecture Note**: Include `metadata: Map<String, Any>` field in all documents for extensibility (roles, permissions, encryption keys, geolocation data, etc.).

---

## Local Database Schema (Room)

### Message Entity
```kotlin
@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey val id: String,
    val chatId: String,
    val senderId: String,
    val text: String?,
    val imageUrl: String?,
    val timestamp: Long,
    val status: String,
    val readBy: String, // JSON array
    val synced: Boolean = false,
    val createdAt: Long
)
```

### Chat Entity
```kotlin
@Entity(tableName = "chats")
data class ChatEntity(
    @PrimaryKey val id: String,
    val type: String, // 'direct' or 'group'
    val name: String?,
    val participants: String, // JSON array
    val lastMessage: String?,
    val lastMessageTime: Long?,
    val unreadCount: Int = 0,
    val updatedAt: Long
)
```

### SendQueue Entity
```kotlin
@Entity(tableName = "send_queue")
data class SendQueueEntity(
    @PrimaryKey val id: String,
    val messageId: String,
    val chatId: String,
    val retryCount: Int = 0,
    val createdAt: Long
)
```

**🏗️ Architecture Note**: Design schema to support message expiration timestamps (for self-destruct), geolocation metadata, and mesh routing information post-MVP. Room's migration system will make schema evolution straightforward.

---

## Key Architectural Decisions

### 1. Modular AI Integration
**Decision**: Create `ai` module with clean interface for swapping providers

**Structure**:
```
/modules
  /ai
    /providers
      OpenAIProvider.kt      // OpenAI implementation
      LocalProvider.kt       // Placeholder for local LLM
    AIService.kt             // Interface
    AITypes.kt
```

**Rationale**: Allows clean swap from OpenAI API → local/HQ-hosted LLM post-prototype

---

### 2. Modular Encryption
**Decision**: Abstract encryption into swappable module with consistent interface

**Structure**:
```
/modules
  /encryption
    /providers
      AES32Provider.kt       // 32-bit AES (prototype)
      SignalProvider.kt      // Signal Protocol (production)
    EncryptionService.kt
    EncryptionTypes.kt
```

**Implementation**:
- Use Android Keystore for secure key storage from day 1
- Interface design supports key exchange protocols

**Rationale**: Prototype with basic encryption, swap to Signal Protocol or military-grade later. Android Keystore provides hardware-backed security.

---

### 3. Message Queue Architecture
**Decision**: Implement persistent queue with WorkManager for retry logic

**Implementation**:
- Messages save to Room database immediately
- WorkManager schedules sync tasks with exponential backoff
- Queue processes on app foreground/network change
- OneTimeWorkRequest for individual message sends
- PeriodicWorkRequest for batch sync

**Rationale**: Ensures no message loss and prepares for mesh networking where queuing is critical. WorkManager is superior to manual background services for reliability and battery optimization.

---

### 4. Media Storage Strategy
**Decision**: Store media metadata alongside messages, with local caching via Coil

**Implementation**:
- Upload to Firebase Storage
- Store URL in message document
- Coil handles local caching automatically
- Metadata supports future geolocation pins

**Rationale**: Extensible to other media types (voice, video, geolocation pins). Coil's caching system is production-ready and battery-efficient.

---

### 5. Dependency Injection with Hilt
**Decision**: Use Hilt for dependency injection from MVP

**Rationale**: 
- Clean architecture with testable components
- Essential for swapping modules (AI providers, encryption providers)
- Reduces boilerplate compared to manual DI
- Industry standard for Android development

---

### 6. Bluetooth/WiFi-Direct Preparation
**Decision**: Abstract network transport layer to support multiple transports

**Structure**:
```
/network
  /transports
    FirestoreTransport.kt    // MVP implementation
    BluetoothTransport.kt    // Future
    WiFiDirectTransport.kt   // Future
  NetworkService.kt          // Interface
```

**Rationale**: Android's native Bluetooth and WiFi-Direct APIs are first-class. Designing transport abstraction now makes mesh networking integration straightforward post-MVP.

---

## Testing Requirements

### MVP Test Scenarios
1. **Real-time messaging**: Two emulators (or emulator + physical device) send messages back and forth
2. **Offline scenario**: Device A goes offline, Device B sends messages, Device A comes online and receives them
3. **Background/Foreground**: Send message while app backgrounded, verify notification and sync
4. **Force-quit**: Force-quit app mid-send, reopen, verify message sends via WorkManager
5. **Poor network**: Throttle connection, send 20+ rapid messages, verify all deliver
6. **Group chat**: 3 participants send messages, verify all receive and read receipts work
7. **Image sending**: Send image from gallery, verify appears for recipient
8. **Persistence**: Send messages, force-quit app, reopen, verify history loads from Room

---

## Development Stages

### Stage 1: Foundation (Hours 0-6)
**Goal**: Basic app shell with authentication

**Tasks**:
- [ ] Create Android Studio project with Kotlin + Compose
- [ ] Set up Firebase project (Firestore, Auth, Storage, FCM)
- [ ] Configure Hilt for dependency injection
- [ ] Implement authentication screens (register, login, forgot password)
- [ ] Set up Compose Navigation
- [ ] Create user profile screen
- [ ] Set up Room database with DAOs

**Deliverable**: User can register, login, and see their profile

---

### Stage 2: Core Messaging (Hours 6-14)
**Goal**: Working one-on-one chat

**Tasks**:
- [ ] Create chat list screen with Compose LazyColumn
- [ ] Implement chat screen UI with message bubbles
- [ ] Build message sending logic (optimistic updates)
- [ ] Set up Firestore listeners with Flow adapters
- [ ] Implement local message persistence (Room)
- [ ] Add message states (sending/sent/delivered/read)
- [ ] Add online/offline presence
- [ ] Add typing indicators
- [ ] Implement WorkManager for message queue

**Deliverable**: Two users can chat in real-time with persistence

---

### Stage 3: Group Chat (Hours 14-18)
**Goal**: Working group chat with 3+ users

**Tasks**:
- [ ] Create group creation flow
- [ ] Implement group chat screen (with sender attribution)
- [ ] Add per-user read receipts
- [ ] Update message schema for groups
- [ ] Test with 3 participants on emulators

**Deliverable**: Three users can chat in a named group with read receipts

---

### Stage 4: Media & Polish (Hours 18-22)
**Goal**: Image sharing and notifications working

**Tasks**:
- [ ] Implement image picker using ActivityResultContracts
- [ ] Create image preview composable
- [ ] Upload images to Firebase Storage
- [ ] Display images inline using Coil
- [ ] Set up FCM for push notifications
- [ ] Create notification channels
- [ ] Test foreground notifications
- [ ] Add timestamps to messages
- [ ] Polish UI (loading states, error handling, Material3 theming)

**Deliverable**: Users can send images and receive notifications

---

### Stage 5: Testing & Deployment (Hours 22-24)
**Goal**: All MVP test scenarios pass

**Tasks**:
- [ ] Run through all 8 test scenarios
- [ ] Fix critical bugs
- [ ] Test on physical device
- [ ] Generate signed APK for testing
- [ ] Document any known issues

**Deliverable**: Functional MVP deployed on Android emulator, APK-ready

---

## Post-MVP Development Path

### Week 2: Tactical Features (P1)
- Precise geolocation sharing (lat/long with FusedLocationProviderClient)
- Pin-dropping in messages with Google Maps SDK integration
- Self-destruct messages (time-from-read with AlarmManager)
- NATO communication templates (pre-filled forms with validation)

### Week 3: Advanced Security (P1)
- End-to-end encryption (Signal Protocol via libsignal-android)
- Secure key exchange using Android Keystore
- Message encryption at rest
- Hardware-backed security features
- Security audit

### Week 4: AI Integration (P2)
- AI chat interface for conversation analysis
- NATO template generation with AI assistance
- Message summarization
- Action item extraction
- Cloud Functions for AI processing

### Future: Mesh Networking (P3)
- Bluetooth LE peer discovery (BluetoothLeScanner)
- WiFi-Direct group formation (WifiP2pManager)
- Mesh routing protocol
- Offline multi-hop message relay
- Background service for continuous mesh operation

**Note**: All advanced features require modular architecture established in MVP. Do not implement these in first 24 hours. Android's native support for Bluetooth and WiFi-Direct makes this significantly more achievable than cross-platform solutions.

---

## Success Metrics

### MVP Success Criteria
- [ ] All 8 test scenarios pass
- [ ] Messages sync reliably (0% message loss in testing)
- [ ] App doesn't crash during normal use
- [ ] Offline → online transition works seamlessly
- [ ] Group chat supports 3+ users
- [ ] Push notifications work in foreground
- [ ] WorkManager successfully retries failed sends

### Performance Targets
- Message send latency: <500ms (online)
- Message delivery: <1s (online recipients)
- App launch time: <2s (cold start)
- Message history load: <1s for 100 messages
- Chat list scroll: 60fps on mid-range devices
- Memory usage: <100MB for typical use

---

## Known Limitations (MVP)

- Background notifications require app to be running (can be enhanced with WorkManager polling)
- Image compression not optimized (large files slow to upload)
- No message editing or deletion
- No message search
- No voice/video calls
- Group member management not included
- E2E encryption not included
- Geolocation features not included
- Bluetooth/WiFi-Direct not included

---

## Android-Specific Advantages

### Native Platform Benefits
1. **Bluetooth & WiFi-Direct**: First-class Android SDK support makes mesh networking viable
2. **Background Tasks**: WorkManager provides reliable background processing
3. **Security**: Android Keystore with hardware-backed security
4. **Location Services**: FusedLocationProviderClient for battery-efficient precise location
5. **Notifications**: Rich notification support with channels and priority
6. **Permissions**: Granular runtime permissions for tactical features
7. **Performance**: No bridge overhead, direct hardware access

### Tactical Use Case Fit
- Many military/tactical deployments use Android devices (Samsung Tactical Edition)
- Potential ATAK (Android Team Awareness Kit) integration
- Hardware security module access
- Better battery life for extended field operations
- Offline-first capabilities with robust background sync

---

## Build Configuration

### build.gradle.kts (app level)
```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.gms.google-services")
    id("com.google.dagger.hilt.android")
    kotlin("kapt")
}

android {
    namespace = "com.messageai.tactical"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.messageai.tactical"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0-mvp"
    }
    
    buildFeatures {
        compose = true
    }
    
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.3"
    }
}

dependencies {
    // Compose
    implementation(platform("androidx.compose:compose-bom:2023.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    
    // Firebase
    implementation(platform("com.google.firebase:firebase-bom:32.5.0"))
    implementation("com.google.firebase:firebase-auth-ktx")
    implementation("com.google.firebase:firebase-firestore-ktx")
    implementation("com.google.firebase:firebase-storage-ktx")
    implementation("com.google.firebase:firebase-messaging-ktx")
    
    // Room
    implementation("androidx.room:room-runtime:2.6.0")
    implementation("androidx.room:room-ktx:2.6.0")
    kapt("androidx.room:room-compiler:2.6.0")
    
    // Hilt
    implementation("com.google.dagger:hilt-android:2.48")
    kapt("com.google.dagger:hilt-compiler:2.48")
    implementation("androidx.hilt:hilt-navigation-compose:1.1.0")
    
    // WorkManager
    implementation("androidx.work:work-runtime-ktx:2.9.0")
    implementation("androidx.hilt:hilt-work:1.1.0")
    
    // Coil for images
    implementation("io.coil-kt:coil-compose:2.5.0")
    
    // DataStore
    implementation("androidx.datastore:datastore-preferences:1.0.0")
    
    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3")
}
```

---

## Questions for Development

- [ ] Should we use Material3 or Material2 for theming?
- [ ] Do we need Retrofit for any custom API calls, or is Firebase SDK sufficient?
- [ ] Should we implement image compression using Android's built-in APIs before upload?
- [ ] What's the max group size we should support in data schema?
- [ ] Should we use ViewModel SavedStateHandle for process death recovery?

---

## Resources

- [Android Developers Guide](https://developer.android.com/)
- [Jetpack Compose Documentation](https://developer.android.com/jetpack/compose)
- [Firebase Android SDK](https://firebase.google.com/docs/android/setup)
- [Room Database Guide](https://developer.android.com/training/data-storage/room)
- [WorkManager Guide](https://developer.android.com/topic/libraries/architecture/workmanager)
- [Android Bluetooth Guide](https://developer.android.com/guide/topics/connectivity/bluetooth)
- [WiFi-Direct Guide](https://developer.android.com/guide/topics/connectivity/wifip2p)

---

**Document Version**: 1.0  
**Last Updated**: October 20, 2025  
**Owner**: Development Team