# Missions Module - Block E

## Overview
Tracks missions and tasks per chat with real-time Firestore updates. Integrates with AI and CASEVAC to create/maintain mission status.

## Components
- MissionService: CRUD, realtime flows, archive-on-complete.
- MissionBoardViewModel: exposes per-chat missions via Flow.

## Firestore Schema
```
/missions/{missionId}
  chatId: string
  title: string
  description: string?
  status: string (open|in_progress|done)
  priority: number
  assignees: string[]
  createdAt: number
  updatedAt: number
  archived: boolean
  sourceMsgId: string?
  casevacCasualties: number
/missions/{missionId}/tasks/{taskId}
  title, description, status, priority, assignees, timestamps
```

## Usage
```kotlin
val id = missionService.createMission(Mission(chatId, title = "Secure Route"))
missionService.updateMission(id, mapOf("status" to "done"))
missionService.archiveIfCompleted(id)
```

## Future
- Filters, paging, and richer task metadata.

