from __future__ import annotations

from typing import Any, Dict, List, Tuple
import math
import json
import logging

from .providers import OpenAIProvider


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
    def __init__(self, llm: OpenAIProvider) -> None:
        self.llm = llm
        self._embeds: Dict[str, List[float]] = {}
        self._texts: Dict[str, str] = {}
        self._logger = logging.getLogger("messageai.rag")

    def index_messages(self, messages: List[Dict[str, Any]], max_items: int = 300) -> None:
        for m in messages[:max_items]:
            mid = m.get("id") or m.get("_id") or str(id(m))
            text = str(m.get("text") or "").strip()
            if not text:
                continue
            if mid in self._embeds:
                continue
            self._texts[mid] = text
            try:
                self._embeds[mid] = self.llm.embed(text)
            except Exception as e:
                # mock mode or error: store empty vector and log
                self._logger.error(json.dumps({
                    "event": "embed_error",
                    "message_id": str(mid),
                    "error": str(e)
                }))
                self._embeds[mid] = []

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


