import os
import requests
from requests import HTTPError, RequestException

from scripts.logger import logger

# --------------------------------------------------
# ENV
# --------------------------------------------------

LLM_MODE = os.getenv("LLM_MODE", "openrouter").lower()

OPENROUTER_API_KEY = os.getenv("OPENROUTER_API_KEY")
OPENROUTER_URL = "https://openrouter.ai/api/v1/chat/completions"
OPENROUTER_MODEL = "kwaipilot/kat-coder-pro:free"


# ==================================================
# OpenRouter Provider (fallback / optional)
# ==================================================

class OpenRouterProvider:
    def __init__(self):
        if not OPENROUTER_API_KEY:
            raise RuntimeError("OPENROUTER_API_KEY not set")

    def ask(self, prompt: str) -> str:
        payload = {
            "model": OPENROUTER_MODEL,
            "messages": [
                {
                    "role": "system",
                    "content": "You are an expert Android/Kotlin build-fixing agent.",
                },
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
            timeout=60,
        )

        if not r.ok:
            logger.error(f"❌ OpenRouter HTTP {r.status_code}: {r.text}")
            r.raise_for_status()

        try:
            data = r.json()
        except ValueError:
            logger.error("❌ OpenRouter returned non-JSON response")
            raise RuntimeError("OpenRouter returned invalid JSON")

        return data["choices"][0]["message"]["content"]


# ==================================================
# Composite Provider
# ==================================================

class CompositeProvider:
    """
    Provider selector:
    - browser     → Playwright-based UI automation (API-less)
    - openrouter  → Free OpenRouter model (fallback)
    """

    def __init__(self):
        self.provider = self._select_provider()

    def _select_provider(self):
        if LLM_MODE == "browser":
            logger.info("🧠 LLM mode: browser (API-less)")
            from scripts.llm_runners.browser import get_browser_llm
            return get_browser_llm()

        logger.info("🧠 LLM mode: openrouter (free tier)")
        return OpenRouterProvider()

    def ask(self, prompt: str) -> str:
        try:
            return self.provider.ask(prompt)
        except (HTTPError, RequestException, RuntimeError) as e:
            logger.error(f"❌ LLM request failed: {e}")
            raise


# ==================================================
# Factory
# ==================================================

def get_llm_provider():
    logger.info(f"🧠 Initializing LLM provider (mode={LLM_MODE})")
    return CompositeProvider()
