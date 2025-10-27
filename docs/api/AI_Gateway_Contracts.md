# AI Gateway Contracts

All requests use a common envelope:

```json
{ "requestId": "uuid", "context": { "chatId": "..." }, "payload": { } }
```

Responses:

```json
{ "requestId": "uuid", "status": "ok", "data": { ... } }
```

## Security (HMAC)

Headers: `x-request-id`, `x-sig-ts`, `x-sig`

- Compute `payload_hash = sha256(JSON.stringify(body))`
- Base string: `{requestId}.{ts}.{payload_hash}`
- Signature: `hex(hmac_sha256(LANGCHAIN_SHARED_SECRET, base))`

Requests with missing headers, invalid timestamps, stale `ts`, or mismatched signatures are rejected.

## Endpoints

### POST /template/warnord | /template/opord | /template/frago
Request payload:
```json
{ "prompt": "optional free text", "candidateChats": [ { "id": "...", "name": "...", "updatedAt": 0, "lastMessage": "..." } ] }
```
Response data:
```json
{ "content": "markdown string" }
```

### POST /sitrep/summarize
Payload:
```json
{ "timeWindow": "6h" }
```
Response:
```json
{ "content": "markdown string" }
```

### POST /threats/extract
Payload options:
```json
{
  "message": { "id": "msgId", "text": "..." },
  "triggerMessageId": "msgId",
  "currentLocation": { "lat": 0.0, "lon": 0.0 },
  "maxMessages": 50
}
```
Response:
```json
{
  "threats": [
    {
      "summary": "...",
      "severity": 1,
      "confidence": 0.9,
      "radiusM": 250,
      "positionMode": "absolute",
      "abs": { "lat": 0.0, "lon": 0.0 },
      "offset": { "north": 0.0, "east": 0.0 },
      "tags": ["IED"],
      "sourceMsgId": "..."
    }
  ]
}
```

### POST /assistant/gate
Payload:
```json
{ "prompt": "single message" }
```
Response:
```json
{ "escalate": true }
```

### POST /assistant/route
Payload:
```json
{ "prompt": "...", "candidateChats": [ {"id": "...", "name": "...", "updatedAt": 0, "lastMessage": "..."} ] }
```
Response:
```json
{ "decision": "{\"tool\":\"template/warnord\",\"args\":{},\"reply\":\"...\"}" }
```

### POST /intent/casevac/detect
Payload (one form):
```json
{ "messages": ["...", "..."] }
```
Response:
```json
{ "intent": "casevac", "confidence": 0.92 }
```

### POST /workflow/casevac/run
Payload: `{ ... }` (opaque workflow args)

Response: `{ ... }` (summary of steps; schema intentionally minimal for forward compatibility)

### POST /tasks/extract
Payload:
```json
{ "contextText": "..." }
```
Response:
```json
{ "tasks": [ { "title": "...", "description": "...", "priority": 3 } ] }
```

### POST /missions/plan
Payload:
```json
{ "prompt": "optional", "candidateChats": [ {"id":"...", "name":"...", "updatedAt":0, "lastMessage":"..."} ] }
```
Response:
```json
{ "title": "...", "description": "...", "priority": 5, "tasks": [ {"title":"...","description":"...","priority":3} ] }
```


