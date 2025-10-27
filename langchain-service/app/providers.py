from typing import Any, Dict
import os

from openai import OpenAI

from .config import OPENAI_API_KEY


class OpenAIProvider:
    def __init__(self) -> None:
        self.enabled = bool(OPENAI_API_KEY)
        self.client = OpenAI(api_key=OPENAI_API_KEY) if self.enabled else None

    def chat(self, system_prompt: str, user_prompt: str, model: str = "gpt-4o-mini") -> str:
        if not self.enabled or not self.client:
            # Fallback mock response if no key present
            return "[MOCK] " + user_prompt[:256]
        resp = self.client.chat.completions.create(
            model=model,
            messages=[
                {"role": "system", "content": system_prompt},
                {"role": "user", "content": user_prompt},
            ],
            temperature=0.3,
            max_tokens=800,
        )
        return resp.choices[0].message.content or ""

    def embed(self, text: str, model: str = "text-embedding-3-small") -> Any:
        if not self.enabled or not self.client:
            # Deterministic small vector for mock mode
            return [0.0] * 5
        resp = self.client.embeddings.create(model=model, input=text)
        return resp.data[0].embedding


