from typing import Any, Dict, List, Optional
import os

from google.cloud import firestore

from .config import FIRESTORE_PROJECT_ID


class FirestoreReader:
    def __init__(self) -> None:
        project = FIRESTORE_PROJECT_ID or None
        self.client = firestore.Client(project=project)

    def fetch_recent_messages(self, chat_id: Optional[str], limit: int = 50) -> List[Dict[str, Any]]:
        # If chat_id is provided, read from that chat; else query recent across all chats (dev-friendly approximation)
        if chat_id:
            qs = (
                self.client.collection("chats").document(chat_id).collection("messages")
                .order_by("createdAt", direction=firestore.Query.DESCENDING)
                .limit(limit)
            )
            docs = qs.stream()
            return [d.to_dict() | {"id": d.id} for d in docs]
        else:
            # Sample across a few chats: read last N from a synthetic index if available; fallback empty
            return []


