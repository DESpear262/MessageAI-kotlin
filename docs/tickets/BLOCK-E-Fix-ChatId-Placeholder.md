# TICKET: Fix Placeholder ChatId in MissionBoardViewModel

**Status:** 🟡 Backlog  
**Priority:** High  
**Type:** Bug / Enhancement  
**Estimated Effort:** 1-2 hours  
**Assignee:** TBD  
**Created:** 2025-10-24

---

## Problem Summary

`MissionBoardViewModel` uses a hardcoded placeholder chatId instead of the currently selected chat:

```kotlin
// MissionBoardViewModel.kt line 20
private val currentChatId: String = "global" // replace with selected chat scope in future
```

**Problems:**
1. Shows missions for non-existent "global" chat
2. User can't see missions for their actual chats
3. No way to switch between chats
4. Placeholder comment indicates intentional MVP shortcut

**Impact:**
- High: Core functionality doesn't work as expected
- UX: Users can't view their actual missions
- Data Isolation: All missions shown together instead of per-chat

---

## Root Cause

MVP implementation prioritized infrastructure over proper chat selection integration.

**Architectural Context:**
- MessageAI is a chat-based application
- Missions should be scoped to specific chats
- App likely has a "currently selected chat" state somewhere
- MissionBoard needs to read this state

---

## Solution Options

### Option 1: Pass ChatId as Navigation Parameter (Recommended)

**What:** Navigate to MissionBoardScreen with chatId as parameter

**Pros:**
- Clean separation of concerns
- Works with Navigation Component
- Easy to test
- No global state needed

**Cons:**
- Requires navigation route update

**Implementation:**

```kotlin
// Navigation setup
composable("mission_board/{chatId}") { backStackEntry ->
    val chatId = backStackEntry.arguments?.getString("chatId") ?: return@composable
    MissionBoardScreen(chatId = chatId)
}

// MissionBoardScreen
@Composable
fun MissionBoardScreen(chatId: String) {
    val vm: MissionBoardViewModel = hiltViewModel()
    
    LaunchedEffect(chatId) {
        vm.setChatId(chatId)
    }
    
    // ... rest of UI
}

// MissionBoardViewModel
@HiltViewModel
class MissionBoardViewModel @Inject constructor(
    private val missions: MissionService,
    private val auth: FirebaseAuth
) : ViewModel() {
    
    private val _chatId = MutableStateFlow<String?>(null)
    
    val missions: StateFlow<List<Pair<String, Mission>>> =
        _chatId.flatMapLatest { chatId ->
            if (chatId != null) {
                missions.observeMissions(chatId)
            } else {
                flowOf(emptyList())
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    
    fun setChatId(chatId: String) {
        _chatId.value = chatId
    }
    
    suspend fun updateStatus(missionId: String, status: String) {
        missions.updateMission(missionId, mapOf("status" to status, "updatedAt" to System.currentTimeMillis()))
        missions.archiveIfCompleted(missionId)
    }
}
```

**Navigation to MissionBoard:**
```kotlin
// From chat screen
Button(onClick = { navController.navigate("mission_board/$currentChatId") }) {
    Text("View Missions")
}
```

---

### Option 2: Inject Current Chat State

**What:** Create a `CurrentChatProvider` or similar singleton

**Pros:**
- No navigation changes needed
- Accessible from anywhere

**Cons:**
- Global state management
- Harder to test
- Tight coupling

**Not recommended** - Option 1 is cleaner

---

### Option 3: Multi-Chat View

**What:** Show missions from all chats user participates in

**Pros:**
- Single unified view
- No chat selection needed

**Cons:**
- Doesn't match task plan requirement (missions are per-chat)
- Harder to filter/organize
- Not the intended design

**Not recommended** - Doesn't match requirements

---

## Recommended Solution: Option 1 (Navigation Parameter)

### Implementation Steps

1. **Update MissionBoardViewModel:**
   ```kotlin
   private val _chatId = MutableStateFlow<String?>(null)
   
   val missions: StateFlow<List<Pair<String, Mission>>> =
       _chatId.flatMapLatest { chatId ->
           if (chatId != null) {
               missions.observeMissions(chatId)
           } else {
               flowOf(emptyList())
           }
       }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
   
   fun setChatId(chatId: String) {
       _chatId.value = chatId
   }
   ```

2. **Update MissionBoardScreen:**
   ```kotlin
   @Composable
   fun MissionBoardScreen(chatId: String, viewModel: MissionBoardViewModel = hiltViewModel()) {
       LaunchedEffect(chatId) {
           viewModel.setChatId(chatId)
       }
       
       // ... rest of UI (unchanged)
   }
   ```

3. **Update Navigation Route:**
   ```kotlin
   composable("mission_board/{chatId}") { backStackEntry ->
       val chatId = backStackEntry.arguments?.getString("chatId") ?: return@composable
       MissionBoardScreen(chatId = chatId)
   }
   ```

4. **Update Call Sites:**
   ```kotlin
   // From chat screen or other entry points
   navController.navigate("mission_board/$currentChatId")
   ```

---

## Acceptance Criteria

- [ ] MissionBoardViewModel accepts chatId parameter
- [ ] Missions filtered by selected chat
- [ ] Real-time updates work for specific chat
- [ ] Navigation route updated with chatId parameter
- [ ] All call sites updated
- [ ] No hardcoded "global" chatId
- [ ] Status updates still work
- [ ] Unit tests updated
- [ ] Manual testing confirms correct chat filtering

---

## Testing Checklist

### Unit Tests
```kotlin
@Test
fun `setChatId updates missions flow`() = runTest {
    val mockMissions = listOf(
        "mission-1" to Mission(chatId = "chat-123", title = "Test", ...)
    )
    coEvery { missionService.observeMissions("chat-123") } returns flowOf(mockMissions)
    
    viewModel.setChatId("chat-123")
    advanceUntilIdle()
    
    assertEquals(mockMissions, viewModel.missions.value)
}

@Test
fun `setChatId switches between chats`() = runTest {
    val chat1Missions = listOf(...)
    val chat2Missions = listOf(...)
    
    coEvery { missionService.observeMissions("chat-1") } returns flowOf(chat1Missions)
    coEvery { missionService.observeMissions("chat-2") } returns flowOf(chat2Missions)
    
    viewModel.setChatId("chat-1")
    advanceUntilIdle()
    assertEquals(chat1Missions, viewModel.missions.value)
    
    viewModel.setChatId("chat-2")
    advanceUntilIdle()
    assertEquals(chat2Missions, viewModel.missions.value)
}
```

### Integration Tests
```bash
# 1. Create missions in chat-123
# 2. Create missions in chat-456
# 3. Navigate to mission_board/chat-123
# 4. Verify only chat-123 missions shown
# 5. Navigate to mission_board/chat-456
# 6. Verify only chat-456 missions shown
```

---

## Related Files

### Files to Modify
- `app/src/main/java/com/messageai/tactical/ui/main/MissionBoardViewModel.kt` - Remove placeholder, add chatId flow
- `app/src/main/java/com/messageai/tactical/ui/main/MissionBoardScreen.kt` - Accept chatId parameter
- Navigation setup file (find and update composable route)
- All call sites that navigate to MissionBoard

### Files to Create/Update
- `app/src/test/java/com/messageai/tactical/ui/main/MissionBoardViewModelTest.kt` - Add tests for chatId switching

---

## Alternative: Fallback to Current User's Chats

If chat selection is complex, temporary workaround:

```kotlin
// Show all missions for current user's chats
val missions: StateFlow<List<Pair<String, Mission>>> =
    auth.currentUser?.let { user ->
        // Query all chats user participates in
        // Then observe missions for those chats
        // Merge into single list
    } ?: flowOf(emptyList())
```

**But this is NOT recommended** - proper chat scoping is better

---

## Success Metrics

✅ **Definition of Done:**
1. No hardcoded "global" chatId
2. Missions filtered by selected chat
3. Navigation route includes chatId parameter
4. Real-time updates work per-chat
5. Unit tests pass
6. Manual testing confirms correct behavior
7. Code review approved
8. Clean git commit

---

## References

- QC Report: `docs/reviews/BLOCKS_E_F_QC_REPORT.md`
- Task Plan: `docs/product/messageai-sprint2-task-plan.md` (Block E requirements)
- Jetpack Compose Navigation: https://developer.android.com/jetpack/compose/navigation

---

**Created by:** QC Agent (Blocks E & F Review)  
**Related Sprint:** Sprint 2 - AI Integration (Bug Fix)  
**Blocks:** E  
**Ticket ID:** BLOCK-E-003

