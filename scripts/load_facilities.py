#!/usr/bin/env python3
"""
MessageAI – Facilities preloader CLI

Loads medical facilities into Firestore for CASEVAC testing.

Schema (matches app FacilityService and docs):
  /facilities/{facilityId}
    - name: string
    - lat: number
    - lon: number
    - capabilities: string[] (optional)
    - available: boolean (default true)

Usage:
  python scripts/load_facilities.py path/to/facilities.json

JSON input format examples:
  1) Array of facilities
  [
    {"name":"Alpha Med","lat":34.01,"lon":-117.01,"capabilities":["trauma"],"available":true}
  ]

  2) Object with facilities field
  { "facilities": [ { ... }, { ... } ] }

Environment:
  - GOOGLE_APPLICATION_CREDENTIALS must point to the dev service account.
"""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path
from typing import Any, Dict, Iterable, List, Tuple

from google.cloud import firestore  # type: ignore


COL = "facilities"


def parse_input(path: Path) -> List[Dict[str, Any]]:
    data = json.loads(path.read_text(encoding="utf-8"))
    if isinstance(data, list):
        facilities = data
    elif isinstance(data, dict) and isinstance(data.get("facilities"), list):
        facilities = data["facilities"]
    else:
        print("Error: input must be an array or an object with 'facilities' array", file=sys.stderr)
        sys.exit(1)
    # Basic validation
    out: List[Dict[str, Any]] = []
    for idx, f in enumerate(facilities):
        if not isinstance(f, dict):
            print(f"Error: facilities[{idx}] must be an object", file=sys.stderr)
            sys.exit(1)
        name = f.get("name")
        lat = f.get("lat")
        lon = f.get("lon")
        if not isinstance(name, str) or name.strip() == "":
            print(f"Error: facilities[{idx}].name must be a non-empty string", file=sys.stderr)
            sys.exit(1)
        if not isinstance(lat, (int, float)) or not isinstance(lon, (int, float)):
            print(f"Error: facilities[{idx}] must include numeric lat/lon", file=sys.stderr)
            sys.exit(1)
        caps = f.get("capabilities")
        if caps is not None and not (isinstance(caps, list) and all(isinstance(x, str) for x in caps)):
            print(f"Error: facilities[{idx}].capabilities must be an array of strings if present", file=sys.stderr)
            sys.exit(1)
        available = f.get("available", True)
        if not isinstance(available, bool):
            print(f"Error: facilities[{idx}].available must be boolean if present", file=sys.stderr)
            sys.exit(1)
        out.append({
            "name": name.strip(),
            "lat": float(lat),
            "lon": float(lon),
            "capabilities": caps if isinstance(caps, list) else None,
            "available": available,
        })
    return out


def batch_commit(db: firestore.Client, ops: List[Tuple[str, Dict[str, Any]]]) -> None:
    BATCH_LIMIT = 450
    for i in range(0, len(ops), BATCH_LIMIT):
        batch = db.batch()
        for path, data in ops[i : i + BATCH_LIMIT]:
            ref = db.document(path)
            batch.set(ref, data, merge=True)
        batch.commit()


def main() -> None:
    parser = argparse.ArgumentParser(description="Load facilities into Firestore (dev)")
    parser.add_argument("json_path", type=Path, help="Path to facilities JSON")
    args = parser.parse_args()

    items = parse_input(args.json_path)

    db = firestore.Client()

    ops: List[Tuple[str, Dict[str, Any]]] = []
    for f in items:
        # Deterministic id from name (lowercase, hyphenated); if collision, Firestore merge updates
        fid = "-".join("".join(ch for ch in f["name"].lower() if ch.isalnum() or ch.isspace()).split())
        if not fid:
            # fallback to random
            import uuid as _uuid
            fid = _uuid.uuid4().hex

        data: Dict[str, Any] = {
            "name": f["name"],
            "lat": f["lat"],
            "lon": f["lon"],
            "available": bool(f.get("available", True)),
        }
        caps = f.get("capabilities")
        if isinstance(caps, list):
            data["capabilities"] = caps
        ops.append((f"{COL}/{fid}", data))

    batch_commit(db, ops)
    print(f"Upserted {len(items)} facilities into '{COL}' collection")


if __name__ == "__main__":
    main()


