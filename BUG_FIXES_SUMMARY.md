# Bug Fixes Summary - October 22, 2025

## Issues Resolved

### 1. ✅ Presence Indicators Always Show Offline
**Problem:** Online presence dots were always gray (offline) even when users were actually online.

**Root Cause:** The `RtdbPresenceService.goOnline()` and `goOffline()` methods were never being called. The presence infrastructure existed but wasn't wired to the app lifecycle.

**Fix:**
- Updated `RootViewModel` to inject `RtdbPresenceService`
- Added `onAppForeground()` and `onAppBackground()` methods to manage presence
- Wired app lifecycle events in `AppRoot.kt` using `LifecycleEventObserver`
- Presence is now set to online when:
  - User logs in (via `refreshAuthState()`)
  - App comes to foreground (via `ON_RESUME`)
  - Initial auth check (in `init` block)
- Presence is set to offline when:
  - User logs out
  - App goes to background (via `ON_PAUSE`)
  - ViewModel is cleared

**Files Modified:**
- `app/src/main/java/com/messageai/tactical/ui/RootViewModel.kt`
- `app/src/main/java/com/messageai/tactical/ui/AppRoot.kt`

---

### 2. ✅ Push Notifications Don't Work When App is Closed
**Problem:** FCM push notifications were not displaying when the app was in the background or killed.

**Root Cause:** Multiple issues in `MessagingService`:
1. Insufficient logging made debugging impossible
2. No handling for notification-only messages vs data-only messages
3. No `PendingIntent` to open the app when tapping notification
4. Fallback notification handling was inadequate

**Fix:**
- Added comprehensive logging to `onNewToken()` and `onMessageReceived()`
- Updated message parsing to handle both `message.data` and `message.notification` fields
- Created proper `PendingIntent` with `Intent.FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TOP`
- Added chatId to intent extras for deep-linking
- Notifications now use `FLAG_IMMUTABLE` for Android 12+ compatibility
- System notifications are always shown (not just as fallback)

**Files Modified:**
- `app/src/main/java/com/messageai/tactical/notifications/MessagingService.kt`

**Note:** The Cloud Functions in `firebase-functions/functions/src/index.ts` send data-only messages which are now properly handled.

---

### 3. ✅ Missing Unread Message Counter
**Problem:** Chat list had no visual indicator for unread messages.

**Root Cause:** Feature was not implemented at all. The `unreadCount` field existed in `ChatEntity` but was never populated or displayed.

**Fix:**
- Updated `MessageListener` to calculate and update unread count when processing new messages
- Unread count increments for messages where:
  - Sender is not the current user
  - Current user's UID is not in the `readBy` array
- Added `Badge` component to `ChatListScreen` showing unread count in red
- Badge displays "99+" for counts > 99
- Added `markAsRead()` function to clear unread count when user opens chat
- Unread count is cleared in `LaunchedEffect` when `ChatScreen` opens

**Files Modified:**
- `app/src/main/java/com/messageai/tactical/data/remote/MessageListener.kt`
- `app/src/main/java/com/messageai/tactical/ui/main/ChatListScreen.kt`
- `app/src/main/java/com/messageai/tactical/ui/chat/ChatScreen.kt`

**UI Design:** Badge appears in the trailing content of each `ListItem`, using Material 3 error color scheme for high visibility.

---

### 4. ✅ Missing Image Picker Buttons
**Problem:** Gallery and camera buttons to send images were completely absent from the chat UI.

**Root Cause:** The image upload infrastructure (ImageService, ImageUploadWorker) was implemented in Block G, but the UI buttons were never added to `ChatScreen`.

**Fix:**
- Added `rememberLauncherForActivityResult` for both gallery picker and camera capture
- Gallery picker uses `ActivityResultContracts.GetContent` with "image/*" filter
- Camera launcher uses `ActivityResultContracts.TakePicture` with FileProvider URI
- Added two `IconButton`s to the input row:
  - `Icons.Default.Image` for gallery
  - `Icons.Default.CameraAlt` for camera
- Camera creates temporary file in cache with FileProvider authority `${packageName}.fileprovider`
- Implemented `sendImage()` function in `ChatViewModel`:
  - Persists selected URI to cache using `ImageService.persistToCache()`
  - Creates placeholder `MessageEntity` with status "SENDING"
  - Enqueues both `SendWorker` and `ImageUploadWorker`
- Updated message rendering to display images:
  - Uses `AsyncImage` from Coil library
  - Images constrained to max 200dp height
  - Supports messages with both image and text

**Files Modified:**
- `app/src/main/java/com/messageai/tactical/ui/chat/ChatScreen.kt`
- `app/build.gradle.kts` (added `material-icons-extended` and `lifecycle-runtime-compose` dependencies)

---

## Dependencies Added
```gradle
implementation("androidx.compose.material:material-icons-extended")
implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
```

These dependencies provide:
- Material Design icons (Camera, Image, etc.)
- Lifecycle integration for Compose (`LocalLifecycleOwner`)

---

## Testing Checklist

### Presence Indicators
- [ ] Login → presence dot turns green for online users
- [ ] Background app → presence dot turns gray
- [ ] Foreground app → presence dot turns green again
- [ ] Logout → presence set to offline
- [ ] Kill app → RTDB onDisconnect sets offline status

### Push Notifications
- [ ] Receive notification when app is in background
- [ ] Receive notification when app is killed
- [ ] Tap notification opens app to correct chat
- [ ] Notification shows correct sender name and message text
- [ ] Notification channel "Messages" appears in Android settings

### Unread Counters
- [ ] Unread badge appears when receiving new message
- [ ] Badge increments correctly (1, 2, 3...)
- [ ] Badge shows "99+" for 100+ unread messages
- [ ] Badge disappears when opening chat
- [ ] Badge uses red error color for visibility

### Image Sending
- [ ] Gallery button opens photo picker
- [ ] Selecting photo from gallery sends successfully
- [ ] Camera button launches camera
- [ ] Taking photo sends successfully
- [ ] Images display inline in message bubbles
- [ ] Images are resized/compressed before upload
- [ ] Image uploads retry on failure (via WorkManager)
- [ ] EXIF metadata is stripped from images

---

## Architecture Notes

### Presence Management
- **Service:** `RtdbPresenceService` writes to `status/{uid}` in RTDB
- **State:** `{ state: "online"|"offline", last_changed: timestamp }`
- **Lifecycle:** Managed by `RootViewModel` and `AppRoot`
- **onDisconnect:** Firebase RTDB automatically sets offline when client disconnects

### Unread Counter Logic
- **Update:** Calculated in `MessageListener` when processing Firestore changes
- **Storage:** Stored in Room `chats.unreadCount` field
- **Clear:** When user opens chat via `ChatViewModel.markAsRead()`
- **Scope:** Per-chat (not global)

### Image Pipeline
1. User picks/captures image → URI
2. `ImageService.persistToCache()` → File in app cache
3. Create placeholder MessageEntity with status="SENDING"
4. Enqueue `SendWorker` (creates Firestore doc with image=null)
5. Enqueue `ImageUploadWorker`:
   - Decode + resize + compress via `ImageService.processAndUpload()`
   - Upload to Storage at `chat-media/{chatId}/{messageId}.jpg`
   - Patch Firestore message doc with `imageUrl` and status="SENT"
6. `MessageListener` receives update → updates Room → UI shows image

---

## Known Limitations (MVP Acceptable)

1. **Presence latency:** RTDB presence updates may take 1-2 seconds to propagate
2. **Notification grouping:** Multiple messages show as separate notifications (not grouped by chat)
3. **Unread count accuracy:** If user reads messages on another device, count won't sync until next Firestore update
4. **Image preview:** No full-screen preview or zoom functionality (shows inline only)
5. **Upload progress:** No progress bar for image uploads (shows as "SENDING" until complete)

---

## Security Considerations

### Presence Privacy
- Users can see each other's online status in direct chats
- No privacy controls for hiding online status (MVP scope)
- Consider adding "Show online status" toggle in future

### Image Uploads
- EXIF metadata stripped (prevents location leaks)
- Images resized to max 2048px (prevents excessive storage use)
- Storage path includes chatId for security rules enforcement
- **CRITICAL:** Firestore/Storage rules must validate user has access to chat

### Notifications
- FCM tokens stored in Firestore `users/{uid}/fcmToken`
- Tokens updated on app launch and token refresh
- Cloud Functions query tokens to send push notifications
- **CRITICAL:** Cloud Functions must validate sender is in chat participants

---

## Future Enhancements

### Presence
- Add "last seen" timestamp display ("Active 5m ago")
- Group presence aggregation ("3 members online")
- Typing indicators (infrastructure exists, UI not implemented)

### Notifications
- Notification grouping by chat
- Rich notification with inline reply
- Notification badges on app icon (Android 8+)
- Priority channels (mute, mentions-only, all)

### Unread Counters
- Global unread count badge on tab
- Mark all as read button
- Unread sync across devices via Firestore

### Images
- Full-screen viewer with zoom/pan
- Upload progress bar
- Multiple image selection
- Video support
- GIF support
- Image compression settings

---

## Commit Message
```
Fix critical bugs: presence, notifications, unread counts, and image buttons

PRESENCE INDICATORS (Bug #1):
- Wire RtdbPresenceService to app lifecycle via RootViewModel
- Track ON_RESUME/ON_PAUSE events in AppRoot with LifecycleEventObserver  
- Set online on login, foreground, and init; offline on logout, background, clear
- Presence dots now accurately reflect user online status

PUSH NOTIFICATIONS (Bug #2):
- Add comprehensive logging to MessagingService for debugging
- Handle both data-only and notification messages from FCM
- Create PendingIntent with chatId for deep-linking on tap
- Always show system notification (not just fallback)
- Use FLAG_IMMUTABLE for Android 12+ compatibility

UNREAD MESSAGE COUNTERS (Feature #3):
- Calculate unread count in MessageListener for messages not read by user
- Display red Badge in ChatListScreen with count (shows "99+" for >99)
- Clear unread count when opening chat via markAsRead()
- Badge uses Material 3 error color for high visibility

IMAGE PICKER BUTTONS (Bug #4):
- Add gallery and camera IconButtons to ChatScreen input row
- Implement gallery picker with ActivityResultContracts.GetContent
- Implement camera capture with TakePicture and FileProvider
- Add sendImage() to ChatViewModel with ImageService integration
- Render images inline with AsyncImage (max 200dp height)
- Support messages with both image and text

DEPENDENCIES:
- Add material-icons-extended for Camera/Image icons
- Add lifecycle-runtime-compose for LocalLifecycleOwner

All builds compile successfully. Ready for acceptance testing.
```

