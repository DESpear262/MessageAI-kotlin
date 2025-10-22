# Code Index

## Kotlin (Android)
- `android-kotlin/app/src/main/java/com/messageai/tactical/MainActivity.kt`: Activity entry; hosts Compose root.
- `android-kotlin/app/src/main/java/com/messageai/tactical/MessageAiApp.kt`: Application class; Hilt init.
- `android-kotlin/app/src/main/java/com/messageai/tactical/ui/AppRoot.kt`: Compose app root, nav graph.
- `android-kotlin/app/src/main/java/com/messageai/tactical/ui/RootViewModel.kt`: Auth state ViewModel.
- `android-kotlin/app/src/main/java/com/messageai/tactical/ui/auth/AuthScreen.kt`: Auth screen.
- `android-kotlin/app/src/main/java/com/messageai/tactical/ui/auth/ForgotPasswordScreen.kt`: Forgot password screen.
- `android-kotlin/app/src/main/java/com/messageai/tactical/ui/auth/AuthViewModel.kt`: Auth logic (login/register/reset).
- `android-kotlin/app/src/main/java/com/messageai/tactical/ui/main/MainTabs.kt`: Main tabs placeholder.
- `android-kotlin/app/src/main/java/com/messageai/tactical/ui/theme/Theme.kt`: Compose theme wrapper.
- `android-kotlin/app/src/main/java/com/messageai/tactical/di/FirebaseModule.kt`: Firebase singletons (Auth/Firestore/Storage).

### Data – Room
- `android-kotlin/app/src/main/java/com/messageai/tactical/data/db/AppDatabase.kt`: Room DB + Hilt module for DAOs.
- `android-kotlin/app/src/main/java/com/messageai/tactical/data/db/Dao.kt`: DAOs for messages/chats/send queue/remote keys.
- `android-kotlin/app/src/main/java/com/messageai/tactical/data/db/Entities.kt`: Room entities and indices.

### Data – Remote (Firestore/RTDB)
- `android-kotlin/app/src/main/java/com/messageai/tactical/data/remote/FirestorePaths.kt`: Firestore path constants; deterministic direct chat ID.
- `android-kotlin/app/src/main/java/com/messageai/tactical/data/remote/TimeUtils.kt`: Timestamp conversions and LWW resolver.
- `android-kotlin/app/src/main/java/com/messageai/tactical/data/remote/Mapper.kt`: Mappers between Firestore DTOs and Room entities.
- `android-kotlin/app/src/main/java/com/messageai/tactical/data/remote/MessageService.kt`: Message Firestore CRUD + paging.
- `android-kotlin/app/src/main/java/com/messageai/tactical/data/remote/ChatService.kt`: Chat creation and lastMessage updates.
- `android-kotlin/app/src/main/java/com/messageai/tactical/data/remote/MessageRepository.kt`: Paging3 repository.
- `android-kotlin/app/src/main/java/com/messageai/tactical/data/remote/MessageRemoteMediator.kt`: Paging RemoteMediator bridging Room/Firestore.
- `android-kotlin/app/src/main/java/com/messageai/tactical/data/remote/MessageListener.kt`: Firestore listener → Room write-through; deliveredBy.
- `android-kotlin/app/src/main/java/com/messageai/tactical/data/remote/ReadReceiptUpdater.kt`: Batch read receipts for visible messages.
- `android-kotlin/app/src/main/java/com/messageai/tactical/data/remote/RtdbPresenceService.kt`: RTDB presence and typing.
- `android-kotlin/app/src/main/java/com/messageai/tactical/data/remote/model/FirestoreModels.kt`: Firestore document models.

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


