# Facility Module

## Overview
Provides nearest medical facility lookup for CASEVAC using Firestore facility data and Haversine distance.

## Usage
```kotlin
val nearest = facilityService.nearest(lat, lon, requireAvailable = true)
```

## Firestore Schema
```
/facilities/{id}
  name: string
  lat: number
  lon: number
  capabilities: string[]
  available: boolean
```

