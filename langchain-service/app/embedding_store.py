from __future__ import annotations

from typing import Any, Dict, List, Optional

from .firestore_client import FirestoreReader


class FirestoreEmbeddingStore:
    """
    Simple embedding store backed by Firestore message documents.

    Storage layout (per message):
      chats/{chatId}/messages/{messageId}/chunks/{seq}
        - text: string
        - embed: List[float]
        - seq: int
        - len: int
    """

    def __init__(self, fs: Optional[FirestoreReader] = None) -> None:
        self.fs = fs or FirestoreReader()

    # Reads recent chunk docs for a chat (already stored by CF trigger or backfill)
    def read_recent_chunks(self, chat_id: str, message_limit: int = 200) -> List[Dict[str, Any]]:
        return self.fs.fetch_recent_chunks(chat_id, limit_messages=message_limit)

    # Write helpers used by backfill/warm endpoint
    def write_chunks(self, chat_id: str, message_id: str, chunks: List[Dict[str, Any]]) -> None:
        self.fs.write_message_chunks(chat_id, message_id, chunks)


