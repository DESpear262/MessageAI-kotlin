# TICKET: Seed Firestore `facilities` Collection for CASEVAC

Status: 🟡 Backlog
Priority: Medium
Type: Data / Test Setup
Estimated Effort: 1-2 hours
Assignee: TBD
Created: 2025-10-24

---

## Summary
Create and populate the Firestore `facilities` collection with sample medical facilities used by the CASEVAC workflow to select the nearest medical asset.

## Collection Schema
Path: `/facilities/{facilityId}`

Fields:
- `name: string` (e.g., "Role 2 Field Hospital")
- `lat: number` (decimal degrees)
- `lon: number` (decimal degrees)
- `capabilities: string[]` (e.g., ["surgery","trauma","airlift"]) optional
- `available: boolean` (default true)
- `createdAt: Timestamp` (optional)

## Seed Data (example)
```json
[
  { "name": "Alpha Med Station", "lat": 34.010, "lon": -117.010, "capabilities": ["trauma"], "available": true },
  { "name": "Bravo Field Hospital", "lat": 34.100, "lon": -117.200, "capabilities": ["surgery","trauma"], "available": true },
  { "name": "Charlie Clinic", "lat": 33.950, "lon": -117.050, "capabilities": ["basic"], "available": true }
]
```

## Steps
1. Create collection `/facilities` in Firestore.
2. Insert at least 5 facility documents across different lat/lon.
3. Set `available=true` for all test docs; toggle to test selection behavior if needed.
4. Verify nearest facility lookup using `FacilityService.nearest()` in the app.

## Acceptance Criteria
- [ ] `/facilities` collection exists with ≥5 documents
- [ ] Each document has `name`, `lat`, `lon`, `available`
- [ ] App selects the nearest facility in CASEVAC workflow
- [ ] Manual test: send `/casevac` in `system` chat → workflow completes and logs selected facility

## Notes
- Data can be non-sensitive training locations.
- In production, integrate real facility feeds or admin UI.
