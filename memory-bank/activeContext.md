# Active Context

## Current Focus
- Block G: Media – Images completed; stabilizing and awaiting text policy integration for Storage/Firestore rules.

## Recent Changes
- Gallery and camera attach added (Android Photo Picker + TakePicture with FileProvider authority `${applicationId}.fileprovider`).
- Image pipeline:
  - Copy picked/captured URI to app cache for retry durability
  - Resize to max edge 2048px and JPEG compress at ~85 quality
  - EXIF stripped by re-encode; HEIC decoded via ImageDecoder on API 28+
  - Upload to Storage at `chat-media/{chatId}/{messageId}.jpg`; patch message doc `imageUrl` + `status=SENT`
- UI: Inline image rendering with Coil; prefetch for visible + next items; simple pre-send preview row (cancel/send) with indeterminate progress UX.
- Workers: `SendWorker` creates/merges message doc; `ImageUploadWorker` uploads and patches URL/status; WorkManager configured with HiltWorkerFactory.

## Next Steps
- Wire Firebase Storage/Firestore security rules once the text policy implementation lands (coordinate with the other agent).
- Optional polish: oversize warning UX, percentage progress indicator, and a dedicated full preview screen post-MVP.
- Validate on-device with poor network; confirm retry/backoff experience for large images.

## Risks
- Large images can lead to longer uploads (indeterminate spinner only for MVP).
- Until rules are wired, media access enforcement is not finalized (keep dev-only testing).

