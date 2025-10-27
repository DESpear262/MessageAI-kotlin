## Load medical facilities into Firestore (dev)

This seeds `/facilities` used by `FacilityService.nearest()` and the CASEVAC workflow.

### Firestore schema
```
/facilities/{facilityId}
  name: string
  lat: number
  lon: number
  capabilities: string[]   # optional
  available: boolean       # default true
```

### JSON format
Either an array or an object with `facilities`:
```json
[
  { "name": "Alpha Med Station", "lat": 34.010, "lon": -117.010, "capabilities": ["trauma"], "available": true },
  { "name": "Bravo Field Hospital", "lat": 34.100, "lon": -117.200, "capabilities": ["surgery","trauma"], "available": true }
]
```

### Prereqs
- Python 3.10+
- `GOOGLE_APPLICATION_CREDENTIALS` set to dev service account
- `pip install google-cloud-firestore`

### Run
From repo root:
```bash
python scripts/load_facilities.py path/to/facilities.json
```

### Notes
- Deterministic doc IDs are derived from lowercase hyphenated names; running again updates existing docs.
- Writes are batched for efficiency.
- Invalid records (missing name/lat/lon) abort with an error message.


