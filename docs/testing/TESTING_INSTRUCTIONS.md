# Testing Instructions for Bug Fixes

## Overview
This document provides step-by-step instructions to test the recent bug fixes. Please run the app with logging enabled to capture any remaining issues.

---

## Setup: Enable Logging

### Option 1: Android Studio Logcat
1. Open Android Studio
2. Go to View → Tool Windows → Logcat
3. Filter by package: `com.messageai.tactical`

### Option 2: ADB Command Line
```bash
adb logcat | grep -E "(RtdbPresence|PresenceService|MessageListener|ChatViewModel|MessagingService)"
```

Or on Windows PowerShell:
```powershell
adb logcat | Select-String -Pattern "(RtdbPresence|PresenceService|MessageListener|ChatViewModel|MessagingService)"
```

---

## Test 1: Camera Image Sending ✅

**Expected Result:** Camera launches without crash, image uploads and displays

**Steps:**
1. Open a chat
2. Tap the camera icon (📷)
3. **First time:** You should see a permission dialog - tap "Allow"
4. Camera should launch
5. Take a photo
6. Photo should show "Sending image..." with a loading spinner
7. After a few seconds, the actual image should appear

**Logs to Check:**
- Look for `ChatViewModel: Sending image` messages
- Look for `SendWorker` and `ImageUploadWorker` activity
- Look for any errors in image processing

**If it fails:**
- Share the exact error message from logcat
- Note at which step it fails (permission, camera launch, or upload)

---

## Test 2: Gallery Image Sending ✅

**Expected Result:** Gallery picker opens, selected image uploads and displays

**Steps:**
1. Open a chat
2. Tap the gallery icon (🖼️)
3. Select an image from your device
4. Image should show "Sending image..." with loading spinner
5. After processing, the actual image should appear

**Logs to Check:**
- Same as camera test above

**If it fails:**
- Check if the empty message bubble issue persists
- Look for errors in `ImageService` or `ImageUploadWorker`

---

## Test 3: Presence Indicators 🔍

**Expected Result:** Green dot for online users, gray dot for offline

**Critical:** This test requires Firebase Realtime Database to be enabled in your Firebase project!

### Setup Check:
1. Go to Firebase Console → Realtime Database
2. Verify that RTDB is enabled (not just Firestore)
3. Check that Database URL is present in `google-services.json`
4. Verify your RTDB rules allow write access:
   ```json
   {
     "rules": {
       "status": {
         "$uid": {
           ".write": "$uid === auth.uid",
           ".read": true
         }
       }
     }
   }
   ```

### Test Steps:
1. **On Device 1:** Login and open the app
2. **Check logs for:**
   ```
   RtdbPresence: Setting user <UID> to online
   RtdbPresence: Successfully set <UID> to online
   ```
3. **On Device 2:** Login as a different user
4. Open a chat with user from Device 1
5. **The presence dot should be GREEN (online)**

**If it shows gray (offline):**
- Check the logs for error messages from `RtdbPresence`
- Common errors:
  - `FirebaseDatabase: Failed to get FirebaseDatabase instance: Specify DatabaseURL` → RTDB not configured
  - `Permission denied` → Check RTDB security rules
  - No logs at all → Lifecycle observer not triggering (report this)

**Additional Test:**
6. **On Device 1:** Press home button (app to background)
7. **Check logs for:** `RtdbPresence: Setting user <UID> to offline`
8. **On Device 2:** Presence dot should turn GRAY
9. **On Device 1:** Return to app
10. **On Device 2:** Presence dot should turn GREEN again

---

## Test 4: Unread Message Counters 🔍

**Expected Result:** Red badge with count appears for unread messages

### Test Steps:
1. **On Device 2:** Send several messages to Device 1 while Device 1 is on the chat list screen (not in the chat)
2. **On Device 1:** You should see a red badge appear on the chat with the count (1, 2, 3, etc.)
3. **On Device 1:** Tap the chat to open it
4. **The badge should disappear immediately**
5. Exit back to chat list
6. Badge should still be gone

**Logs to Check:**
```
MessageListener: Unread count for chat <ID>: X (Y total messages)
```

**If badge doesn't appear:**
- Check if logs show: `Unread count for chat <ID>: 0`
- Check if the message's `readBy` array actually excludes your UID
- Look for JSON parsing errors in `MessageListener`

**If badge doesn't disappear when opening chat:**
- Check if `markAsRead()` is being called
- Check Room database directly (see below)

---

## Test 5: Push Notifications (Background) 🔍

**Expected Result:** Notification appears when app is in background or killed

### Prerequisites:
1. Cloud Functions must be deployed (`firebase-functions/functions/src/index.ts`)
2. FCM must be configured in Firebase Console
3. Device must have internet connection

### Test Steps:
1. **On Device 1:** Ensure app is open and user is logged in
2. **On Device 1:** Press home button or kill the app entirely
3. **On Device 2:** Send a message to Device 1
4. **On Device 1:** You should see a system notification appear in the notification shade

**Logs to Check (before closing app):**
```
MessagingService: New FCM token: <token>
MessagingService: FCM token updated for user <UID>
```

**When notification arrives:**
```
MessagingService: Message received from: <sender>
MessagingService: Message data: {chatId=..., ...}
MessagingService: Notification shown with ID: <id>
```

**If notifications don't appear:**
- Check if FCM token is being saved: Query Firestore `users/{uid}/fcmToken`
- Check if Cloud Functions are running: Firebase Console → Functions → Logs
- Check if device has notification permissions: Settings → Apps → MessageAI → Notifications
- Look for errors in `MessagingService` logs

---

## Debugging Tools

### Check Room Database Directly
```bash
adb shell run-as com.messageai.tactical.dev sqlite3 /data/user/0/com.messageai.tactical.dev/databases/messageai.db "SELECT id, chatId, unreadCount FROM chats;"
```

### Check Firestore Data
1. Firebase Console → Firestore Database
2. Navigate to `chats` collection
3. Check `lastMessage` field structure
4. Navigate to specific chat → `messages` subcollection
5. Check message documents for `readBy`, `deliveredBy`, `imageUrl` fields

### Check RTDB Data
1. Firebase Console → Realtime Database
2. Look under `status/{uid}` for presence data
3. Should see: `{ "state": "online", "last_changed": <timestamp> }`

### Force Clean Build
If weird caching issues occur:
```bash
./gradlew clean
./gradlew :app:compileDevDebugKotlin
```

---

## Common Issues & Solutions

### Issue: "Permission denied" in RTDB logs
**Solution:** Update RTDB security rules to allow authenticated users to write their own presence:
```json
{
  "rules": {
    "status": {
      "$uid": {
        ".write": "$uid === auth.uid",
        ".read": true
      }
    }
  }
}
```

### Issue: "Failed to get FirebaseDatabase instance"
**Solution:** RTDB is not enabled. Enable it in Firebase Console → Realtime Database → Create Database

### Issue: Images show empty bubble then never update
**Solution:** Check WorkManager is running:
```bash
adb shell dumpsys activity service com.messageai.tactical/.MessageAiApp
```
Look for WorkManager info. If not running, check `AndroidManifest.xml` for WorkManager initialization disabling.

### Issue: Unread count always 0
**Solution:** Check message `readBy` field format. Should be JSON array like `["uid1", "uid2"]`, not a Java toString like `[uid1, uid2]`.

### Issue: Notifications only work in foreground
**Solution:** 
1. Check if app has notification permission (Android 13+)
2. Check Cloud Functions are sending data-only messages (not notification messages)
3. Verify `MessagingService` is registered in manifest with correct intent-filter

---

## Success Criteria

All tests pass if:
- ✅ Camera launches and sends images successfully
- ✅ Gallery picker sends images successfully  
- ✅ Images show loading state, then actual image
- ✅ Presence dots turn green for online users (if RTDB configured)
- ✅ Presence updates when app goes to background/foreground
- ✅ Unread badges appear with correct counts
- ✅ Unread badges disappear when opening chat
- ✅ Push notifications appear when app is closed (if FCM configured)

---

## Reporting Issues

If any test fails, please provide:
1. **Which test failed** (Test 1, Test 2, etc.)
2. **Exact step where it failed**
3. **Full logcat output** around the failure
4. **Screenshot** of the issue (if UI-related)
5. **Firebase project configuration:**
   - Is RTDB enabled? (Yes/No)
   - Are Cloud Functions deployed? (Yes/No)
   - What security rules are active?

---

## Next Steps After Testing

Once all tests pass:
1. Test on physical device (not just emulator)
2. Test with poor network conditions
3. Test with multiple simultaneous users
4. Perform the acceptance tests from `messageai-mvp-task-plan.md` Block K

**Firebase Configuration Required:**
- Realtime Database must be enabled for presence indicators
- Cloud Functions must be deployed for push notifications
- Storage rules must allow authenticated uploads for images
- Firestore rules must secure chat access

Contact the Firebase admin to verify these are properly configured.

