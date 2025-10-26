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

## Observability & Troubleshooting
- Structured logs (JSON) emitted by `MissionService`:
  - `mission_create` { missionId, chatId, title, status, priority }
  - `mission_task_add` { missionId, taskId, title, status, priority }
  - `mission_update` { missionId, fields }
  - `missions_observe_emit` { chatId, count }
  - `missions_observe_error` { chatId, message }
  - `tasks_observe_emit` { missionId, count }
  - `tasks_observe_error` { missionId, message }
  - `mission_archive` { missionId }
- ViewModel logs (MissionBoardViewModel):
  - `mission_board_set_chat` { chatId }
  - `mission_board_update_status` { missionId, status }

Tips:
- If UI doesn’t update, filter Logcat for `missions_observe_emit` to see counts.
- Verify Firestore rules and indexes if observe errors appear.

## Future
- Filters, paging, and richer task metadata.

