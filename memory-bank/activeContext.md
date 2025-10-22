# Active Context

## Current Focus
- Block F: Groups completed (unified chat list, name attribution, presence in header). Block G (Media) complete; awaiting rules integration.

## Recent Changes
- Gallery and camera attach added (Android Photo Picker + TakePicture with FileProvider authority `${applicationId}.fileprovider`).
- Image pipeline:
  - Copy picked/captured URI to app cache for retry durability
  - Resize to max edge 2048px and JPEG compress at ~85 quality
  - EXIF stripped by re-encode; HEIC decoded via ImageDecoder on API 28+
  - Upload to Storage at `chat-media/{chatId}/{messageId}.jpg` with metadata (contentType `image/jpeg`, custom: `chatId`, `messageId`, `senderId`); patch message doc `imageUrl` + `status=SENT` and `lastMessage`.
- UI: Inline image rendering with Coil fixed (sizing/contentScale), prefetch for visible + next items; simple pre-send preview row (cancel/send) with indeterminate progress UX.
- Workers: `SendWorker` creates/merges message doc (SENDING for image-first); `ImageUploadWorker` uploads and patches URL/status; WorkManager configured with HiltWorkerFactory; senderId plumbed through worker.

## Next Steps
- Integrate finalized Storage/Firestore rules (pending other agent).
- Optional polish: upload progress %, oversize warning, and full preview.
- Validate presence/typing aggregation for groups.

## Risks
- Large images can lead to longer uploads (indeterminate spinner only for MVP).

