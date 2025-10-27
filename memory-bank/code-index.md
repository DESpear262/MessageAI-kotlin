# Code Index

## Kotlin (Android)
- `app/src/main/java/com/messageai/tactical/MainActivity.kt`: Activity entry; hosts Compose root.
- `app/src/main/java/com/messageai/tactical/MessageAiApp.kt`: Application class; Hilt init.
- `app/src/main/java/com/messageai/tactical/ui/AppRoot.kt`: Compose app root, nav graph.
- `app/src/main/java/com/messageai/tactical/ui/RootViewModel.kt`: Auth state ViewModel.
- `app/src/main/java/com/messageai/tactical/ui/auth/AuthScreen.kt`: Auth screen.
- `app/src/main/java/com/messageai/tactical/ui/auth/ForgotPasswordScreen.kt`: Forgot password screen.
- `app/src/main/java/com/messageai/tactical/ui/auth/AuthViewModel.kt`: Auth logic (login/register/reset).
- `app/src/main/java/com/messageai/tactical/ui/main/MainTabs.kt`: Main tabs placeholder.
- `app/src/main/java/com/messageai/tactical/ui/theme/Theme.kt`: Compose theme wrapper.
- `app/src/main/java/com/messageai/tactical/di/FirebaseModule.kt`: Firebase singletons (Auth/Firestore/Storage).

### Data – Room
- `app/src/main/java/com/messageai/tactical/data/db/AppDatabase.kt`: Room DB + Hilt module for DAOs.
- `app/src/main/java/com/messageai/tactical/data/db/Dao.kt`: DAOs for messages/chats/send queue/remote keys.
- `app/src/main/java/com/messageai/tactical/data/db/Entities.kt`: Room entities and indices.

### Data – Remote (Firestore/RTDB)
- `app/src/main/java/com/messageai/tactical/data/remote/FirestorePaths.kt`: Firestore path constants; deterministic direct chat ID.
- `app/src/main/java/com/messageai/tactical/data/remote/TimeUtils.kt`: Timestamp conversions and LWW resolver.
- `app/src/main/java/com/messageai/tactical/data/remote/Mapper.kt`: Mappers between Firestore DTOs and Room entities.
- `app/src/main/java/com/messageai/tactical/data/remote/MessageService.kt`: Message Firestore CRUD + paging.
- `app/src/main/java/com/messageai/tactical/data/remote/ChatService.kt`: Chat creation and lastMessage updates.
- `app/src/main/java/com/messageai/tactical/data/remote/MessageRepository.kt`: Paging3 repository.
- `app/src/main/java/com/messageai/tactical/data/remote/MessageRemoteMediator.kt`: Paging RemoteMediator bridging Room/Firestore.
- `app/src/main/java/com/messageai/tactical/data/remote/MessageListener.kt`: Firestore listener → Room write-through; deliveredBy.
- `app/src/main/java/com/messageai/tactical/data/remote/ReadReceiptUpdater.kt`: Batch read receipts for visible messages.
- `app/src/main/java/com/messageai/tactical/data/remote/RtdbPresenceService.kt`: RTDB presence and typing.
- `app/src/main/java/com/messageai/tactical/data/remote/model/FirestoreModels.kt`: Firestore document models.

### App Module Config
- `app/src/main/FirebaseConfig.kt`: Firebase providers for the app module.

## React Native (Expo)
### Routing
- `app/_layout.tsx`: Root layout; theme + stack routes.
- `app/(tabs)/_layout.tsx`: Tabs navigator with icons and colors.
- `app/(tabs)/index.tsx`: Home screen with parallax header.
- `app/(tabs)/explore.tsx`: Explore screen showcasing components.
- `app/modal.tsx`: Example modal route.

### Components
- `components/parallax-scroll-view.tsx`: Parallax header scroll view.
- `components/external-link.tsx`: External link wrapper.
- `components/hello-wave.tsx`: Animated waving hand.
- `components/themed-text.tsx`: Theme-aware text with variants.
- `components/themed-view.tsx`: Theme-aware container view.
- `components/haptic-tab.tsx`: Tab button with iOS haptics.
- `components/ui/icon-symbol.tsx`: Cross-platform icon (Material on Android/Web).
- `components/ui/icon-symbol.ios.tsx`: iOS SF Symbols renderer.
- `components/ui/collapsible.tsx`: Simple collapsible panel.

### Hooks & Constants
- `hooks/use-color-scheme.ts`: Native color scheme passthrough.
- `hooks/use-color-scheme.web.ts`: Web color scheme with hydration safety.
- `hooks/use-theme-color.ts`: Select colors from theme with overrides.
- `constants/theme.ts`: Color tokens and fonts.

## Cloud Functions
- `firebase-functions/functions/src/index.ts`: Firestore v2 triggers for message notifications; v1 auth deletion trigger for presence.

## Scripts
- `scripts/reset-project.js`: Reset script to bootstrap a fresh RN app directory.

## Documentation
- `docs/reviews/`: Code review artifacts and summaries.
- `docs/testing/`: Test plans, instructions, and summaries.
- `docs/runbooks/`: Operational guides for urgent fixes and finalization.
- `docs/changelog/`: Bug fixes and change summaries.
- `docs/product/`: PRD and MVP task plan.
- `docs/architecture/`: Architecture diagrams and specs.


