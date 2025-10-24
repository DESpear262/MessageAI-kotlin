#!/usr/bin/env python3
"""
MessageAI – Chat preloader CLI

Purpose
    Load one or more conversations into Firestore for the dev project. It will:
    1) Resolve users by exact email in `users` collection.
    2) Ensure a deterministic direct chat exists for the two users of each convo.
    3) Append messages to `chats/{chatId}/messages` with metadata mirroring
       the Android `SendWorker` (e.g., status, readBy, deliveredBy, server timestamps).

Usage
    python scripts/load_chat.py path/to/chat.json

Accepted JSON formats
  A) Single chat (backward compatible):
    {
      "users": ["alice@example.com", "bob@example.com"],
      "chat": [
        { "from": "alice@example.com", "contents": "Hi Bob" },
        { "from": "bob@example.com",   "contents": "Hey Alice" }
      ]
    }

  B) Multiple chats (top-level `chats` array):
    {
      "chats": [
        {
          "users": ["alice@example.com", "bob@example.com"],
          "chat": [ { "from": "alice@example.com", "contents": "..." } ]
        },
        {
          "users": ["charlie@example.com", "dana@example.com"],
          "chat": [ { "from": "charlie@example.com", "contents": "..." } ]
        }
      ]
    }

Environment
    Requires GOOGLE_APPLICATION_CREDENTIALS pointing to the dev project's
    service account JSON. The Firestore project will be auto-detected from
    the credentials. No --project flag is needed.
"""

from __future__ import annotations

import argparse
import json
import sys
import time
import uuid
from pathlib import Path
from typing import Any, Dict, List, Tuple, Optional

from google.cloud import firestore  # type: ignore


USERS_COL = "users"
CHATS_COL = "chats"
MESSAGES_COL = "messages"


def load_json(path: Path) -> Dict[str, Any]:
    with path.open("r", encoding="utf-8") as f:
        return json.load(f)


def require(condition: bool, message: str) -> None:
    if not condition:
        print(f"Error: {message}", file=sys.stderr)
        sys.exit(1)


def resolve_users_by_email(
    db: firestore.Client,
    emails: List[str],
    cache: Optional[Dict[str, Dict[str, Any]]] = None,
) -> List[Dict[str, Any]]:
    """Resolve a list of emails to user docs, with optional caching across chats."""
    resolved: List[Dict[str, Any]] = []
    if cache is None:
        cache = {}
    for email in emails:
        if email in cache:
            resolved.append(cache[email])
            continue
        q = db.collection(USERS_COL).where("email", "==", email).limit(1)
        docs = list(q.stream())
        require(len(docs) == 1, f"User not found by email: {email}")
        doc = docs[0]
        data = doc.to_dict() or {}
        user = {
            "uid": doc.id,
            "email": data.get("email", email),
            "displayName": data.get("displayName")
            or data.get("displayNameLower")
            or email.split("@")[0],
            "photoUrl": data.get("photoURL") or data.get("photoUrl") or None,
        }
        cache[email] = user
        resolved.append(user)
    return resolved


def direct_chat_id(uid_a: str, uid_b: str) -> str:
    a = uid_a.strip()
    b = uid_b.strip()
    return f"{a}_{b}" if a <= b else f"{b}_{a}"


def ensure_chat(db: firestore.Client, user_a: Dict[str, Any], user_b: Dict[str, Any]) -> str:
    uid_a = user_a["uid"]
    uid_b = user_b["uid"]
    chat_id = direct_chat_id(uid_a, uid_b)
    chat_ref = db.collection(CHATS_COL).document(chat_id)
    snap = chat_ref.get()

    participants = [uid_a] if uid_a == uid_b else [uid_a, uid_b]
    participant_details = (
        {uid_a: {"name": "Note to self", "photoUrl": None}}
        if uid_a == uid_b
        else {
            uid_a: {"name": user_a["displayName"], "photoUrl": user_a.get("photoUrl")},
            uid_b: {"name": user_b["displayName"], "photoUrl": user_b.get("photoUrl")},
        }
    )

    base_doc = {
        "id": chat_id,
        "participants": participants,
        "participantDetails": participant_details,
        "lastMessage": None,
        "metadata": None,
        "createdAt": firestore.SERVER_TIMESTAMP,
        "updatedAt": firestore.SERVER_TIMESTAMP,
    }

    # Merge ensures we do not clobber existing metadata if present.
    chat_ref.set(base_doc, merge=True)
    return chat_id


def build_message_doc(chat_id: str, sender_id: str, text: str) -> Dict[str, Any]:
    now_ms = int(time.time() * 1000)
    return {
        "id": str(uuid.uuid4()),
        "chatId": chat_id,
        "senderId": sender_id,
        "text": text,
        "clientTimestamp": now_ms,
        "status": "SENT",  # mirrors SendWorker behavior for non-image messages
        "readBy": [],
        "deliveredBy": [],
        "localOnly": False,
        "timestamp": firestore.SERVER_TIMESTAMP,
        "metadata": None,
    }


def batch_commit(db: firestore.Client, ops: List[Tuple[str, Dict[str, Any]]]) -> None:
    # ops: list of (doc_path, data)
    BATCH_LIMIT = 450  # headroom under 500
    for i in range(0, len(ops), BATCH_LIMIT):
        batch = db.batch()
        for doc_path, data in ops[i : i + BATCH_LIMIT]:
            ref = db.document(doc_path)
            batch.set(ref, data, merge=True)
        batch.commit()


def append_messages(db: firestore.Client, chat_id: str, sender_map: Dict[str, str], chat_items: List[Dict[str, Any]]) -> None:
    ops: List[Tuple[str, Dict[str, Any]]] = []
    last_text = None
    last_sender = None

    for item in chat_items:
        require(isinstance(item, dict), "Each chat entry must be an object")
        frm = item.get("from")
        text = item.get("contents")
        require(isinstance(frm, str) and "@" in frm, "'from' must be an email string for each message")
        require(isinstance(text, str) and len(text) > 0, "'contents' must be a non-empty string")

        require(frm in sender_map, f"Sender email '{frm}' not in provided users list")
        sender_id = sender_map[frm]
        doc = build_message_doc(chat_id, sender_id, text)
        ops.append((f"{CHATS_COL}/{chat_id}/{MESSAGES_COL}/{doc['id']}", doc))
        last_text = text
        last_sender = sender_id

    # Commit messages in batches
    if ops:
        batch_commit(db, ops)

    # Update lastMessage and updatedAt once, mirroring app behavior semantically
    if last_text is not None and last_sender is not None:
        chat_ref = db.collection(CHATS_COL).document(chat_id)
        chat_ref.update({
            "lastMessage": {
                "text": last_text,
                "senderId": last_sender,
                "timestamp": firestore.SERVER_TIMESTAMP,
            },
            "updatedAt": firestore.SERVER_TIMESTAMP,
        })


def _load_single_chat(
    db: firestore.Client,
    obj: Dict[str, Any],
    user_cache: Dict[str, Dict[str, Any]],
) -> int:
    require(
        "users" in obj and isinstance(obj["users"], list) and len(obj["users"]) == 2,
        "'users' must be a list of two emails",
    )
    require(
        "chat" in obj and isinstance(obj["chat"], list),
        "'chat' must be an array of messages",
    )

    user_emails = obj["users"]
    for e in user_emails:
        require(isinstance(e, str) and "@" in e, "Each user must be an email string")

    # Resolve users and map email -> uid
    user_docs = resolve_users_by_email(db, user_emails, cache=user_cache)
    sender_map = {u["email"]: u["uid"] for u in user_docs}

    # Ensure chat exists and append messages
    chat_id = ensure_chat(db, user_docs[0], user_docs[1])
    append_messages(db, chat_id, sender_map, obj["chat"])
    return len(obj["chat"])


def main() -> None:
    parser = argparse.ArgumentParser(description="Load a chat JSON into Firestore (dev project).")
    parser.add_argument("json_path", type=Path, help="Path to conversation JSON")
    args = parser.parse_args()

    data = load_json(args.json_path)

    db = firestore.Client()  # project inferred from GOOGLE_APPLICATION_CREDENTIALS

    # Support single-chat and multi-chat formats
    total_msgs = 0
    user_cache: Dict[str, Dict[str, Any]] = {}

    if isinstance(data, dict) and isinstance(data.get("chats"), list):
        for idx, obj in enumerate(data["chats"]):
            require(isinstance(obj, dict), f"chats[{idx}] must be an object")
            loaded = _load_single_chat(db, obj, user_cache)
            total_msgs += loaded
        print(f"Loaded {total_msgs} messages across {len(data['chats'])} chats")
    else:
        # Backward compatible single-chat path
        loaded = _load_single_chat(db, data, user_cache)
        print(f"Loaded {loaded} messages into single chat")


if __name__ == "__main__":
    main()



