## Load a conversation into Firestore (dev)

This CLI loads a conversation JSON into the dev Firestore instance by:
- Resolving users by exact `email` in `users` collection
- Ensuring a deterministic 1:1 chat exists (same ID as the app: `uidA_uidB` sorted)
- Appending messages to `chats/{chatId}/messages` mirroring `SendWorker` fields

### JSON format
```json
{
  "users": ["alice@example.com", "bob@example.com"],
  "chat": [
    { "from": "alice@example.com", "contents": "Hi Bob" },
    { "from": "bob@example.com",   "contents": "Hey Alice" }
  ]
}
```

### Prereqs
- Python 3.10+
- Service account credentials for the dev project
  - Set environment variable `GOOGLE_APPLICATION_CREDENTIALS` to the JSON file

### Install deps
```bash
pip install google-cloud-firestore
```

### Run
From the repo root:
```bash
python scripts/load_chat.py path/to/chat.json
```

### Notes
- If any user email does not exist, the script exits with an error.
- If the chat already exists, messages are appended; otherwise it is created.
- Messages are written in batches (<500 writes per batch). `lastMessage` and
  `updatedAt` are updated after write to reflect the final appended message.



