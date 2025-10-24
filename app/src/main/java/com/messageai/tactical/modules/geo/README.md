# Geo Intelligence Module - Block C

## Overview

The Geo Intelligence Module provides location-based threat awareness and alerting capabilities for tactical operations. It integrates with the AI Core Module (Block A) to extract, analyze, and monitor threats using LangChain-powered intelligence.

## Architecture
```
┌─────────────────┐
│   GeoService    │ ← Threat management & alerts
└────────┬────────┘
         │
    ┌────┴────┬──────────────┬─────────────┐
    │         │              │             │
┌───▼────┐ ┌─▼─────────┐ ┌──▼────────┐ ┌─▼──────────┐
│AIService│ │ Firestore │ │Fused Loc  │ │Notification│
│(Block A)│ │ (threats) │ │ Provider  │ │  Manager   │
└─────────┘ └───────────┘ └───────────┘ └────────────┘
```

## Components

### GeoService
Central service for geolocation intelligence operations.

Constructor:
```kotlin
class GeoService(
    private val context: Context,
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val aiService: AIService
)
```

Key methods:
- `suspend fun analyzeChatThreats(chatId: String, maxMessages: Int = 100): Result<Int>`
- `fun summarizeThreatsNear(latitude: Double, longitude: Double, ...)`
- `fun checkGeofenceEnter(latitude: Double, longitude: Double, ...)`
- `fun alertSignalLossIfNeeded(consecutiveMisses: Int)`

## Usage Examples
```kotlin
// Trigger threat extraction
viewModelScope.launch {
    geoService.analyzeChatThreats(chatId)
        .onSuccess { count -> showToast("Extracted $count threats") }
        .onFailure { e -> showToast("Failed: ${e.message}") }
}
```

## Integration Points
- AIService (Block A): LangChain `/threats/extract`
- Firestore: `/threats` collection
- FusedLocationProviderClient: device location
- NotificationManager: alert notifications

## Configuration
- Permissions: `ACCESS_FINE_LOCATION`, `POST_NOTIFICATIONS`
- Notification channel: `alerts_channel`

## Testing
- Unit test distance helpers and error paths
- Integration test geofence checks and AI extraction

## Known Limitations
- Mixed data freshness; TTL 8h
- No deduplication of identical threats
- Runtime permission handling expected at UI layer

## Future Improvements
- WorkManager for periodic geofence checks
- Threat clustering and deduplication
- Room cache for offline viewing
