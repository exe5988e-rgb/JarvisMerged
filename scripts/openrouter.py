import os
import requests
from scripts.logger import logger

OPENROUTER_API_KEY = os.getenv("OPENROUTER_API_KEY")
OPENROUTER_URL = "https://openrouter.ai/api/v1/chat/completions"
OPENROUTER_MODEL = "kwaipilot/kat-coder-pro:free"


class OpenRouterProvider:
    def __init__(self):
        if not OPENROUTER_API_KEY:
            raise RuntimeError("OPENROUTER_API_KEY not set")

    def ask(self, prompt: str, retry_count: int) -> str:
        if retry_count <= 1:
            system = (
                "Output ONLY a unified git diff.\n"
                "Do NOT replace full files."
            )
        elif retry_count == 2:
            system = (
                "Output ONLY a unified git diff.\n"
                "Full-file replacement allowed only if required."
            )
        else:
            system = (
                "Emergency mode.\n"
                "Full-file replacement permitted."
            )

        payload = {
            "model": OPENROUTER_MODEL,
            "messages": [
                {"role": "system", "content": system},
                {"role": "user", "content": prompt},
            ],
            "temperature": 0.1,
            "max_tokens": 2000,
        }

        headers = {
            "Authorization": f"Bearer {OPENROUTER_API_KEY}",
            "Content-Type": "application/json",
        }

        r = requests.post(
            OPENROUTER_URL,
            headers=headers,
            json=payload,
            timeout=90,
        )

        r.raise_for_status()

        data = r.json()
        return data["choices"][0]["message"]["content"]


class CompositeProvider:
    def __init__(self):
        self.provider = OpenRouterProvider()

    def ask(self, prompt: str, retry_count: int) -> str:
        logger.info(f"🧠 Using OpenRouter, retry={retry_count}")
        return self.provider.ask(prompt, retry_count)


def get_llm_provider():
    logger.info("🧠 LLM provider initialized (OpenRouter)")
    return CompositeProvider()
