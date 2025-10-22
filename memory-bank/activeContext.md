# Active Context

## Current Focus
- Block G: Media – Images complete; policy metadata wired and UI rendering fixed.

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
- Optional polish: oversize warning UX, percentage progress indicator, and a dedicated full preview screen post-MVP.
- Validate on-device with poor network; confirm retry/backoff experience for large images.

## Risks
- Large images can lead to longer uploads (indeterminate spinner only for MVP).

