from typing import Any, Dict, List, Optional
import os

from google.cloud import firestore

from .config import FIRESTORE_PROJECT_ID, FIRESTORE_FORCE_PROD, LOG_LEVEL
import logging


class FirestoreReader:
    def __init__(self) -> None:
        project = FIRESTORE_PROJECT_ID or None
        # If FIRESTORE_FORCE_PROD is set, remove emulator host env var so SDK targets real Firestore
        if FIRESTORE_FORCE_PROD:
            # Common emulator env var used by Google SDKs
            if os.environ.get("FIRESTORE_EMULATOR_HOST"):
                os.environ.pop("FIRESTORE_EMULATOR_HOST", None)
        self.client = firestore.Client(project=project)
        try:
            emulator = os.environ.get("FIRESTORE_EMULATOR_HOST")
            logging.getLogger("messageai").info(
                {
                    "event": "firestore_client_init",
                    "project": project or "auto",
                    "using_emulator": bool(emulator),
                    "emulator_host": emulator or "",
                    "force_prod": FIRESTORE_FORCE_PROD,
                }
            )
        except Exception:
            pass

    def fetch_recent_messages(self, chat_id: Optional[str], limit: int = 50) -> List[Dict[str, Any]]:
        # If chat_id is provided, read from that chat; else query recent across all chats (dev-friendly approximation)
        if chat_id:
            coll = self.client.collection("chats").document(chat_id).collection("messages")
            docs: List[Any] = []
            # Prefer createdAt ordering when present; gracefully fall back to timestamp
            try:
                docs = list(
                    coll.order_by("createdAt", direction=firestore.Query.DESCENDING).limit(limit).stream()
                )
                if not docs:
                    raise ValueError("no_docs_createdAt")
            except Exception:
                docs = list(
                    coll.order_by("timestamp", direction=firestore.Query.DESCENDING).limit(limit).stream()
                )
            return [d.to_dict() | {"id": d.id} for d in docs]
        else:
            # Sample across a few chats: read last N from a synthetic index if available; fallback empty
            return []

    # Chunk I/O -----------------------------------------------------------------
    def fetch_recent_chunks(self, chat_id: str, limit_messages: int = 200) -> List[Dict[str, Any]]:
        coll = self.client.collection("chats").document(chat_id).collection("messages")
        try:
            msgs = list(coll.order_by("createdAt", direction=firestore.Query.DESCENDING).limit(limit_messages).stream())
        except Exception:
            msgs = list(coll.order_by("timestamp", direction=firestore.Query.DESCENDING).limit(limit_messages).stream())
        chunks: List[Dict[str, Any]] = []
        for m in msgs:
            mid = m.id
            ccoll = coll.document(mid).collection("chunks")
            cd = list(ccoll.order_by("seq").stream())
            for d in cd:
                data = d.to_dict() or {}
                chunks.append({
                    "messageId": mid,
                    "seq": data.get("seq"),
                    "text": data.get("text"),
                    "embed": data.get("embed"),
                    "len": data.get("len"),
                })
        return chunks

    def write_message_chunks(self, chat_id: str, message_id: str, chunks: List[Dict[str, Any]]) -> None:
        base = self.client.collection("chats").document(chat_id).collection("messages").document(message_id)
        batch = self.client.batch()
        for ch in chunks:
            ref = base.collection("chunks").document(str(ch.get("seq")))
            batch.set(ref, {
                "seq": ch.get("seq"),
                "text": ch.get("text"),
                "embed": ch.get("embed"),
                "len": ch.get("len"),
            }, merge=True)
        batch.commit()


