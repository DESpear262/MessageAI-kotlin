# Debug Instructions - Message Display Issue

## Current Status
- ✅ SendWorker works (messages write to Firestore)
- ✅ Chat preview shows (lastMessage updates)
- ❌ Messages don't display in chat screen

## Added Debug Logging

Enhanced MessageListener with detailed logs to diagnose the issue.

---

## Steps to Debug

### 1. Rebuild with New Logging
```bash
./gradlew installDevDebug
```

### 2. Clear Logcat & Start Fresh
```bash
adb logcat -c
adb logcat | findstr /C:"MessageListener" /C:"SendWorker" /C:"ChatScreen"
```

### 3. Test Flow
1. Open app
2. Open a chat (or create new one)
3. Send a message: "Debug test 1"
4. Watch the logs

---

## What to Look For in Logs

### Scenario A: Listener Starting Successfully ✅
```
D/MessageListener: Starting listener for chat {chatId}
D/MessageListener: Received snapshot for chat {chatId} with X total documents, Y changes
D/MessageListener: Processing ADDED message: {messageId}
D/MessageListener: Upserting 1 messages to Room
```
**If you see this:** Listener is working, Room is updating. Issue is UI not refreshing from Room.

### Scenario B: Listener Starts but No Snapshots 🟡
```
D/MessageListener: Starting listener for chat {chatId}
(nothing else)
```
**If you see this:** Listener attached but Firestore isn't sending events. Possible causes:
- Firestore security rules blocking read
- Network issue
- Chat document doesn't exist

### Scenario C: Listener Not Starting ❌
```
(no MessageListener logs at all)
```
**If you see this:** LaunchedEffect not firing or exception during setup.

### Scenario D: Listener Error 🔴
```
E/MessageListener: Firestore listener error for chat {chatId}
```
**If you see this:** Permission denied or other Firestore error.

---

## Expected Full Log Sequence

### When Opening Chat:
```
D/MessageListener: Starting listener for chat abc123
D/MessageListener: Received snapshot for chat abc123 with 5 total documents, 5 changes
D/MessageListener: Processing ADDED message: msg1
D/MessageListener: Processing ADDED message: msg2
...
D/MessageListener: Upserting 5 messages to Room
```

### When Sending Message:
```
I/WM-WorkerWrapper: Worker result SUCCESS for Work [SendWorker]
D/MessageListener: Received snapshot for chat abc123 with 6 total documents, 1 changes
D/MessageListener: Processing ADDED message: msg6
D/MessageListener: Upserting 1 messages to Room
```

---

## Troubleshooting Based on Logs

### If Listener IS Working but UI Not Updating

**Problem:** Paging3 PagingSource not invalidating when Room updates.

**Root Cause:** Room DAO needs to trigger invalidation.

**Check:** Look at line 26 in Dao.kt:
```kotlin
@Query("SELECT * FROM messages WHERE chatId = :chatId ORDER BY timestamp DESC")
fun pagingSource(chatId: String): PagingSource<Int, MessageEntity>
```

This should auto-invalidate on Room writes, but might need manual invalidation.

**Potential Fix:**
```kotlin
// After upsertAll in MessageListener
db.invalidationTracker.refreshVersionsAsync()
```

### If Listener NOT Starting

**Check ChatViewModel injection:**
- Is `messageListener` properly injected?
- Is `@HiltViewModel` annotation present?
- Is ChatScreen using `hiltViewModel()`?

### If Firestore Permission Error

**Check Firestore Rules:**
```javascript
match /chats/{chatId}/messages/{messageId} {
  allow read: if request.auth != null &&
    request.auth.uid in get(/databases/$(database)/documents/chats/$(chatId)).data.participants;
}
```

---

## Quick Diagnostic Commands

### Check if Messages in Firestore:
Open Firebase Console → Firestore → chats → {chatId} → messages

### Check if Messages in Room:
```bash
adb shell run-as com.messageai.tactical.dev
cd databases
sqlite3 messageai.db
SELECT id, text, chatId, synced FROM messages ORDER BY timestamp DESC LIMIT 10;
.exit
```

### Check Current Chat ID:
Add this log in ChatScreen:
```kotlin
LaunchedEffect(chatId) {
    android.util.Log.d("ChatScreen", "Opened chat: $chatId")
    vm.startListener(chatId, scope)
}
```

---

## Next Steps After You Test

**Report back with:**
1. Full MessageListener logs (copy/paste)
2. What happens when you send a message
3. Results of Room database query

This will tell us exactly where the flow is breaking!

