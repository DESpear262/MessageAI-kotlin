# Block B2 – QC Requirements for LangChain Service

Provide the following to validate endpoints end-to-end via the Firebase CF proxy and directly against the service:

## 1) Firestore Sample Data (JSON seed or live project)
- Project: set `FIRESTORE_PROJECT_ID` and provide access (service account JSON or emulator)
- Collections expected:
  - `chats/{chatId}/messages/{messageId}` with fields:
    - `text: string` (message content)
    - `senderId: string`
    - `createdAt: timestamp`
    - `metadata: map` (optional; may include `aiContext`, `geoTag`)
- Seed requirement:
  - At least 2 chats with 30–100 messages each covering:
    - Casual conversation, tactical updates, casualty reports, and threat intel
    - Time distribution to support SITREP windows (6h/12h/24h)

## 2) Templates Coverage
- Ensure examples cover the following for template autofill evaluation:
  - MEDEVAC 9-line data scattered across messages (pickup location, frequency/call sign, precedence, equipment, patient type, security/injury, marking method, nationality/status, terrain)
  - WARNO and FRAGO fields present in narrative form

## 3) Environment
- Env vars for local run or container:
  - `OPENAI_API_KEY` (optional; mock mode if absent)
  - `FIRESTORE_PROJECT_ID`
  - `GOOGLE_APPLICATION_CREDENTIALS` (path to service account JSON), or run via `gcloud auth application-default login`

## 4) Latency Targets (Warm)
- P95 < 2s for `/template/generate`, `/intent/casevac/detect`
- P95 < 8s for `/workflow/casevac/run`

## 5) Test Plan
- Direct calls using Postman collection (provided in repo) to:
  - `/template/generate` with `{ type: "MEDEVAC", maxMessages: 50 }`
  - `/threats/extract` with `{ maxMessages: 100 }`
  - `/sitrep/summarize` with `{ timeWindow: "6h" }`
  - `/intent/casevac/detect` with recent messages
  - `/workflow/casevac/run` with `{ chatId, priority }`
- Verify envelopes: service echoes `requestId`, `status` is `ok|error`, and `data` conforms to schema.

## 6) Geo Inputs
- Geo parsing comes from device hardware (Android) and is not required as text input for this block. No additional dataset required.

## 7) Deliverables Confirmation
- Service runs locally via Docker and responds on port 8080
- Firebase CF proxy (to be implemented in Block B) forwards requests correctly
- Logs include `requestId`, endpoint, and latency
