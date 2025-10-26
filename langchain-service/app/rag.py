from __future__ import annotations

from typing import Any, Dict, List, Tuple
import math
import json
import logging

from .providers import OpenAIProvider
from .embedding_store import FirestoreEmbeddingStore


def _cosine(a: List[float], b: List[float]) -> float:
    if not a or not b or len(a) != len(b):
        return 0.0
    dot = sum(x * y for x, y in zip(a, b))
    na = math.sqrt(sum(x * x for x in a))
    nb = math.sqrt(sum(y * y for y in b))
    if na == 0 or nb == 0:
        return 0.0
    return dot / (na * nb)


class RAGCache:
    def __init__(self, llm: OpenAIProvider, store: FirestoreEmbeddingStore | None = None) -> None:
        self.llm = llm
        self._embeds: Dict[str, List[float]] = {}
        self._texts: Dict[str, str] = {}
        self._logger = logging.getLogger("messageai.rag")
        self.store = store

    def index_messages(self, messages: List[Dict[str, Any]], max_items: int = 300, chat_id: str | None = None) -> None:
        for m in messages[:max_items]:
            mid = m.get("id") or m.get("_id") or str(id(m))
            text = str(m.get("text") or "").strip()
            if not text:
                continue
            if mid in self._embeds:
                continue
            self._texts[mid] = text
            vec: List[float] | None = None
            # Read from store if available
            if self.store and chat_id:
                try:
                    # Chunks are preferred; if present, skip per-message embed here
                    # The warm path writes chunk vectors; index_messages keeps compatibility for non-warm runs
                    vec = None
                except Exception:
                    vec = None
            if vec is None:
                try:
                    vec = self.llm.embed(text)
                except Exception as e:
                    self._logger.error(json.dumps({"event": "embed_error", "message_id": str(mid), "error": str(e)}))
                    vec = []
            self._embeds[mid] = vec or []

    def top_k(self, query: str, k: int = 20) -> List[Tuple[str, str, float]]:
        try:
            qv = self.llm.embed(query)
        except Exception as e:
            self._logger.error(json.dumps({"event": "query_embed_error", "error": str(e)}))
            qv = []
        scored: List[Tuple[str, str, float]] = []
        for mid, vec in self._embeds.items():
            score = _cosine(qv, vec) if qv else 0.0
            scored.append((mid, self._texts.get(mid, ""), score))
        scored.sort(key=lambda t: t[2], reverse=True)
        return scored[:k]

    def build_context(self, query: str, max_chars: int = 4000) -> str:
        chunks = []
        total = 0
        for _, text, _ in self.top_k(query, k=30):
            if not text:
                continue
            if total + len(text) > max_chars:
                break
            chunks.append(text)
            total += len(text)
        return "\n".join(chunks)

    # Use chunk vectors from store directly (fast-path) ---------------------------------
    def build_context_from_chunks(self, query: str, chunk_rows: List[Dict[str, Any]], max_chars: int = 4000) -> str:
        try:
            qv = self.llm.embed(query)
        except Exception as e:
            self._logger.error(json.dumps({"event": "query_embed_error", "error": str(e)}))
            qv = []
        scored: List[Tuple[int, str, float]] = []
        for row in chunk_rows:
            vec = row.get("embed") or []
            text = row.get("text") or ""
            seq = int(row.get("seq") or 0)
            score = _cosine(qv, vec) if qv else 0.0
            if text:
                scored.append((seq, text, score))
        scored.sort(key=lambda t: t[2], reverse=True)
        out: List[str] = []
        total = 0
        for _, text, _ in scored[:60]:
            if total + len(text) > max_chars:
                break
            out.append(text)
            total += len(text)
        return "\n".join(out)


