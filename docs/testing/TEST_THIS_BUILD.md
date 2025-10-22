# Test This Build - Critical Fixes

## ✅ What Was Fixed

### 1. Camera Crash After Permission
**Fixed:** Camera now launches properly after granting permission
- Used `LaunchedEffect` with state flag to defer camera launch
- No longer crashes when permission is granted

### 2. Unread Message Counter
**Fixed:** Now counts ALL unread messages in each chat, not just recent ones
- Added `getAllMessagesForChat()` DAO method
- Queries entire Room database for accurate count
- Badge shows total unread messages since last read

### 3. Cloud Functions (Push Notifications)
**Deployed:** All three functions are now live on Firebase
- ✅ `sendPushNotification` - Direct messages
- ✅ `sendGroupPushNotification` - Group messages
- ✅ `updatePresenceOnDisconnect` - Cleanup on user deletion

---

## 🧪 Testing Instructions

### Test 1: Camera (Should Work Now!)

**Steps:**
1. Clean build: `./gradlew clean && ./gradlew installDevDebug`
2. Open a chat
3. Tap camera icon (📷)
4. **Grant permission** when prompted
5. Camera should launch (not crash!)
6. Take a photo
7. Should see "Sending image..." spinner
8. After a few seconds, image should appear

**If it still crashes:**
- Check logcat for the exact error
- Look for `ChatScreen` or `FileProvider` errors

---

### Test 2: Unread Message Badges

**Setup:** You need two devices/emulators with different users

**Steps:**
1. **Device 1:** Login as User A
2. **Device 2:** Login as User B
3. **Device 1:** Send 5 messages to User B
4. **Device 2:** Should see **red badge with "5"** on the chat
5. **Device 2:** Open the chat
6. Badge should **immediately disappear**
7. **Device 1:** Send 3 more messages
8. **Device 2:** Go back to chat list
9. Should see badge with **"3"** (not 8!)

**Check logs for:**
```
MessageListener: Unread count for chat <ID>: 3 (8 total messages in chat)
```

**If badge doesn't appear:**
- Check if `readBy` arrays are being updated in Firestore
- Look for JSON parsing errors in logs

---

### Test 3: Push Notifications (NEW!)

**Requirements:**
- ✅ Cloud Functions deployed (done!)
- ✅ FCM configured (should be from google-services.json)
- Device/emulator with Google Play Services

**Steps:**
1. **Device 1:** Login and ensure app is open
2. **Device 1:** Press home button (app goes to background)
3. **Device 2:** Send a message to Device 1
4. **Device 1:** Should see notification appear! 🎉

**Check logs on Device 1 before closing app:**
```
MessagingService: FCM token updated for user <UID>
```

**If no notification:**
- Go to Firebase Console → Functions → Logs
- Look for `sendPushNotification` execution
- Check if FCM token is saved in Firestore `users/{uid}/fcmToken`
- Verify notification permission is granted (Android 13+)

---

### Test 4: Presence Indicators (Requires RTDB Rules)

**You mentioned updating RTDB rules - check if this works now!**

**Steps:**
1. **Device 1:** Login → should see logs:
   ```
   RtdbPresence: Setting user <UID> to online
   RtdbPresence: Successfully set <UID> to online
   ```
2. **Device 2:** Open chat with User from Device 1
3. **Presence dot should be GREEN** ✅

**If still gray:**
- Check logs for errors from `RtdbPresence`
- Verify RTDB rules allow write:
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
- Check Firebase Console → Realtime Database → Data
- Look for `status/{uid}` with `{"state": "online", ...}`

---

## 🔍 How to View Logs

### Option 1: Android Studio Logcat
1. View → Tool Windows → Logcat
2. Filter: `package:mine`
3. Or filter by tags: `RtdbPresence|PresenceService|MessageListener|MessagingService|ChatScreen`

### Option 2: Command Line (if ADB is in PATH)
```bash
# Find adb (usually here on Windows):
# C:\Users\YourName\AppData\Local\Android\Sdk\platform-tools\adb.exe

# View filtered logs:
adb logcat | findstr /i "RtdbPresence PresenceService MessageListener MessagingService ImageService SendWorker ImageUploadWorker ChatScreen"
```

---

## 🐛 Known Issues That Might Still Exist

### Infinite Spinners (Images Not Uploading)
If you still see spinning images that never load, check for:

1. **WorkManager not running:**
   ```
   Look for: SendWorker: Failed to send message
   Or: ImageUploadWorker: <no logs at all>
   ```

2. **Firestore permission denied:**
   ```
   Look for: PERMISSION_DENIED: Missing or insufficient permissions
   ```

3. **Storage permission denied:**
   ```
   Look for: Failed to upload image
   ```

**Quick test:** Check Room database
```bash
adb shell run-as com.messageai.tactical.dev sqlite3 /data/user/0/com.messageai.tactical.dev/databases/messageai.db "SELECT id, status, imageUrl FROM messages WHERE status='SENDING' LIMIT 5;"
```

If messages are stuck in `SENDING` status, WorkManager isn't completing the upload.

---

## 📊 Success Criteria

All tests pass if:
- ✅ Camera launches after permission grant (no crash)
- ✅ Images upload and display (no infinite spinner)
- ✅ Unread badges show correct count
- ✅ Unread badges clear when opening chat
- ✅ Push notifications appear when app is closed (if FCM configured)
- ✅ Presence dots turn green for online users (if RTDB rules fixed)

---

## 🆘 If Something Still Doesn't Work

Please provide:
1. **Which test failed** (Camera, Unread, Push, or Presence)
2. **Exact steps to reproduce**
3. **Full logcat output** for that test (filter by the relevant tags)
4. **Screenshot** (if UI-related)
5. **Firebase configuration status:**
   - RTDB rules set? (Yes/No)
   - Cloud Functions deployed? (Yes - just did this!)
   - FCM enabled? (Should be from google-services.json)

---

## 🚀 Next Steps

1. **Clean rebuild:**
   ```bash
   ./gradlew clean
   ./gradlew installDevDebug
   ```

2. **Run through all 4 tests above**

3. **Share results:**
   - Which tests passed? ✅
   - Which tests failed? ❌
   - Log output for any failures

4. **If all pass:** You're ready for MVP Block K testing! 🎉

---

## 📝 Technical Details

### Camera Fix
- **Problem:** Creating file/URI inside permission callback caused scope issues
- **Solution:** Use state flag + LaunchedEffect to defer camera launch

### Unread Counter Fix
- **Problem:** Only counted messages in Firestore snapshot
- **Solution:** Query ALL messages from Room database
- **Query:** `SELECT * FROM messages WHERE chatId = :chatId`
- **Logic:** Filter messages where `senderId != myUid AND myUid NOT IN readBy`

### Cloud Functions
- **Location:** `firebase-functions/functions/src/index.ts`
- **Region:** us-central1
- **Triggers:**
  - `chats/{chatId}/messages/{messageId}` → onCreate
  - `groups/{groupId}/messages/{messageId}` → onCreate
  - Auth user deletion → onDelete

All functions query Firestore for user FCM tokens and send via `admin.messaging().sendToDevice()`.

---

**Good luck with testing!** 🍀

