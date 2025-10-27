# Firestore Rules – Generated Documents

This app stores generated documents under `users/{uid}/documents/{docId}` with fields:

- `type`: OPORD | WARNORD | FRAGO | SITREP | MEDEVAC
- `title`: string
- `content`: markdown
- `format`: "markdown"
- `chatId`: string | null
- `ownerUid`: string (set to auth.uid)
- `createdAt`/`updatedAt`: millis
- `metadata`: map

Recommended security rules (Firestore Rules v2):

```rules
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    function isSignedIn() {
      return request.auth != null && request.auth.uid != null;
    }

    // User-owned documents
    match /users/{userId} {
      allow read, write: if false; // default deny

      // Documents collection
      match /documents/{docId} {
        // Read/write only by owner
        allow read, list: if isSignedIn() && request.auth.uid == userId;
        allow create: if isSignedIn()
          && request.auth.uid == userId
          && request.resource.data.ownerUid == request.auth.uid
          && request.resource.data.type in ['OPORD','WARNORD','FRAGO','SITREP','MEDEVAC']
          && request.resource.data.format == 'markdown';
        allow update, delete: if isSignedIn() && request.auth.uid == userId;
      }
    }
  }
}
```

Notes:

- The app writes `ownerUid` on create from the signed-in user; the rule enforces it.
- If you later add shared docs by chat, create `chats/{chatId}/documents/{docId}` with a membership check.
- Firestore offline caching is enabled in `FirebaseModule` so lists render quickly.
