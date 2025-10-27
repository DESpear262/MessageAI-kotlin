# Reporting Module - Block D

## Overview

The Reporting Module generates AI-powered tactical reports (SITREP, WARNORD, OPORD, FRAGO) via the LangChain service and provides a simple preview UI for sharing.

## Architecture
```
┌──────────────────┐
│  ReportService   │ ← Report generation
└────────┬─────────┘
         │
    ┌────┴────┐
    │         │
┌───▼──────┐ ┌────▼──────────┐
│LangChain │ │ ReportViewModel│
│ Adapter  │ │    (MVVM)      │
│(Block B) │ └────┬───────────┘
└──────────┘      │
              ┌───▼──────────┐
              │ReportPreview │
              │   Screen     │
              └───┬──────────┘
                  │
              ┌───▼──────────┐
              │ ReportShare  │
              │(FileProvider)│
              └──────────────┘
```

## Components

### ReportService
- `suspend fun generateSITREP(chatId: String, timeWindow: String): Result<String>`
- `suspend fun generateWarnord(): Result<String>`
- `suspend fun generateOpord(): Result<String>`
- `suspend fun generateFrago(): Result<String>`
- In-memory TTL cache to avoid redundant calls

### ReportPreviewScreen
- Displays markdown report with a share FAB

## Usage
```kotlin
reportService.generateSITREP("chat123", "6h")
    .onSuccess { md -> show(md) }
    .onFailure { e -> showError(e.message) }
```

## Integration
- LangChainAdapter posts to `/sitrep/summarize` and `/template/*`
- FileProvider used by `ReportShare`

## Testing
- Add unit tests for cache hit/miss behavior
- ViewModel state tests for loading/error success paths

## Future Improvements
- PDF export and offline caching
- Friendly error messages and retries
