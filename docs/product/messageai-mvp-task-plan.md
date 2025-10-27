# MessageAI – MVP Parallel Task Plan

**Total focus:** core auth → 1:1 chat → groups → media → notifications → offline+queue  
**Parallelization:** 3–4 blocks at once; watch prerequisites

---

## 🔴 BLOCK A: Foundation & Project Skeleton
**Dependencies:** None  
**Time:** ~3–4h  
**Critical path:** yes

**Tasks**
- [ ] Initialize app project, package IDs, environments, and secrets
  - [ ] Android Studio project; Compose Navigation; Gradle Kotlin DSL.
- [ ] Create Firebase project: Auth, Firestore, Storage, FCM; download configs
- [ ] App shell & navigation (Auth stack → Main tabs/stacks)
- [ ] Basic theme + icon set; error/loading components
- [ ] Local DB bootstrap (schema + migrations stub)
  - Messages, Chats, SendQueue (mirrors in both PRDs)
- [ ] Env switching (dev/prod), feature flags for post-MVP modules

**Deliverable**
- App launches to auth gate; main shell reachable after mock login

**Validation**
- [ ] Builds/run on device or emulator  
- [ ] Lint/format + CI check pass  
- [ ] Local DB files created with schemas

---

## 🟥 BLOCK B: Authentication System (P0)
**Dependencies:** A  
**Time:** ~3h

**Tasks**
- [ ] Email/password register, login, logout; basic validation
- [ ] Forgot password (email link)
- [ ] Persistent auth state; bootstrap on launch
- [ ] Minimal Profile screen (name, avatar)
  - [ ] Android Keystore token/session handling

**Acceptance**
- [ ] Can register, login, stay logged in across restarts  
- [ ] Reset link sent and handled  
- [ ] Invalid credentials show proper errors  
- [ ] Profile shows name + avatar (or default)

---

## 🟧 BLOCK C: Data Models & Firestore Wiring
**Dependencies:** A  
**Time:** ~2h

**Tasks**
- [ ] Define Firestore collections: users, chats, groups, messages (+metadata field)
- [ ] Client models + mappers to/from local DB
- [ ] Timezone/clock source; createdAt/updatedAt helpers
- [ ] Last-write-wins policy (timestamp) for MVP

**Acceptance**
- [ ] Read/write round-trip for a dummy chat + message  
- [ ] Metadata field present for extensibility

---

## 🟨 BLOCK D: Local Persistence Layer
**Dependencies:** A, C  
**Time:** ~2h

**Tasks**
- [ ] Implement messages, chats, send_queue tables/entities
- [ ] DAOs / repository methods for reads, upserts, pagination
  - Room + DataStore
- [ ] Migration strategy stubs

**Acceptance**
- [ ] History loads from local DB on cold start  
- [ ] Write-through cache on send; sync marks `synced=true`

---

## 🟩 BLOCK E: Real-Time 1:1 Chat (P0)
**Dependencies:** B, C, D  
**Time:** ~6–8h (parallelizable by screen)

**Tasks**
- [ ] Chat list (latest message, unread count, timestamp)
- [ ] Chat screen UI (bubbles, timestamps, optimistic send state)
- [ ] Send pipeline: local insert → enqueue → Firestore write → state update
- [ ] Firestore listeners → local insert/update; backfill to list
- [ ] Message states: sending → sent → delivered → read
- [ ] Presence (online/offline) & typing indicators
  - Presence doc with onDisconnect; typing ephemeral state

**Acceptance**
- [ ] Optimistic UI; recipient sees message <1s when online  
- [ ] States progress correctly; timestamps accurate  
- [ ] Presence + typing indicators visible

---

## 🟦 BLOCK F: Group Chat (P0)
**Dependencies:** E  
**Time:** ~3–4h

**Tasks**
- [ ] Create group flow (3+ members), editable name
- [ ] Group chat screen (sender attribution: name/avatar)
- [ ] Per-user read receipts (readBy array)
- [ ] Group list item (last message preview)

**Acceptance**
- [ ] 3 users exchange messages in real-time  
- [ ] Read receipts show who has read each message  
- [ ] History persists locally

**Note**
- Design members array to allow add/remove post-MVP.

---

## 🟪 BLOCK G: Media – Images (P0)
**Dependencies:** E (minimum), F (recommended)  
**Time:** ~2–3h

**Tasks**
- [ ] Pick from gallery, preview before send
- [ ] Upload to Storage; attach URL to message
- [ ] Inline display; local caching and progress
  - [ ] MediaStore + Coil

**Acceptance**
- [ ] Sender & recipient see image; cached for offline view  
- [ ] Progress indicators visible; thumbnails in lists

---

## 🟫 BLOCK H: Push Notifications (P0)
**Dependencies:** B, E  
**Time:** ~2h

**Tasks**
- [ ] Register device for FCM; store fcmToken on user doc
- [ ] Cloud Function (or server stub) sends notification on new message
- [ ] Foreground notification handling; route to chat on tap
  - [ ] Notification channels; PendingIntent routing

**Acceptance**
- [ ] Foreground notifications reliable; correct chat opens  
- [ ] No duplicate notifications

**Note**
- [ ] Background behavior varies by Android version and OEM. For MVP, foreground handling is sufficient; plan signed APK testing for full behavior.

---

## 🟧 BLOCK I: Offline Support & Sync/Queue (P0)
**Dependencies:** D, E  
**Time:** ~3h

**Tasks**
- [ ] Send queue with retry/backoff; survives process death
  - [ ] WorkManager OneTime/Periodic requests
- [ ] Firestore offline persistence toggled
- [ ] Network state detection & graceful transitions
- [ ] Conflict policy: timestamp LWW (MVP)

**Acceptance**
- [ ] View history offline  
- [ ] Offline sends queue; auto-send on reconnect  
- [ ] Force-quit mid-send doesn’t lose message

---

## 🟨 BLOCK J: Presence Banner & Connection Status
**Dependencies:** E, I  
**Time:** ~1h

**Tasks**
- [ ] Connection indicator (online/offline/reconnecting)
- [ ] Optional toast on reconnect

**Acceptance**
- [ ] Indicator reflects real connectivity in near-real-time

---

## 🟩 BLOCK K: Testing Matrix & Hard Scenarios
**Dependencies:** E–I  
**Time:** ~2–3h

**Scenarios (run on 2 devices/emulators)**
1. Real-time back-and-forth (1:1)  
2. Offline receive/replay  
3. Background → send → foreground sync  
4. Force-quit mid-send → reopen → delivers  
5. Throttled network; burst 20+ msgs, all deliver  
6. Group chat (3 users), read receipts  
7. Image send/preview/cache  
8. Persistence across restarts

**Acceptance**
- [ ] All 8 pass cleanly; log known issues

---

## 🟦 BLOCK L: Packaging & Delivery
**Dependencies:** K  
**Time:** ~1–2h

**Tasks**
- [ ] On-device run (physical) and smoke test
- [ ] Build artifacts:  
  - Signed APK for testing
- [ ] MVP README with setup, limits, and next steps

**Acceptance**
- [ ] Artifact installable; minimal docs provided

---

# Post-MVP Roadmap

## Week 2 – Tactical UX
- [ ] Precise geo-share + pin-drop with map previews  
- [ ] Self-destruct (time-from-read)  
- [ ] NATO templates (forms + validation)

## Week 3 – Security
- [ ] E2E encryption module; key exchange; at-rest encryption  
 - [ ] Hardware-backed keys (Keystore) from day 1 in Kotlin

## Week 4 – AI Assist
- [ ] AI chat, template generation, summarization, action extraction; provider-swappable interface

## Future – Mesh Prep
- [ ] Transport abstraction over Firestore; future Bluetooth/Wi-Fi Direct transports  
- [ ] Offline multi-hop relay + routing

---

## Tiny Stack Diffs (only where it matters)

- **Secure storage:** Android Keystore  
- **Local DB:** Room (DAOs, migrations)  
- **Images:** MediaStore/Coil  
- **Queue/background:** WorkManager  
- **Notifications:** Notification channels + intents

---

## Recommended Execution Strategy

**Day 1 (8h):** A, B, C, D → baseline running  
**Day 2 (10h):** E core (chat list + send/receive + states) + I queue; start J  
**Day 3 (6–8h):** F groups + G media + H notifications; K tests; L packaging
