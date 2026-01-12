import os
from scripts.logger import logger

# Optional OpenRouter imports
import requests
from requests import HTTPError, RequestException

# Browser runner import
from scripts.llm_runners.browser import get_browser_llm

# -----------------------------
# Configuration
# -----------------------------
LLM_MODE = os.getenv("LLM_MODE", "browser")  # "browser" or "openrouter"

OPENROUTER_API_KEY = os.getenv("OPENROUTER_API_KEY")
OPENROUTER_URL = "https://openrouter.ai/api/v1/chat/completions"
OPENROUTER_MODEL = "kwaipilot/kat-coder-pro:free"

# -----------------------------
# Providers
# -----------------------------
class OpenRouterProvider:
    def __init__(self):
        if not OPENROUTER_API_KEY:
            raise RuntimeError("OPENROUTER_API_KEY not set")

    def ask(self, prompt: str) -> str:
        payload = {
            "model": OPENROUTER_MODEL,
            "messages": [
                {"role": "system", "content": "You are an expert Android/Kotlin build fixer."},
                {"role": "user", "content": prompt}
            ],
            "temperature": 0.1,
            "max_tokens": 2000,
        }

        headers = {
            "Authorization": f"Bearer {OPENROUTER_API_KEY}",
            "Content-Type": "application/json",
        }

        r = requests.post(OPENROUTER_URL, headers=headers, json=payload, timeout=60)
        if not r.ok:
            logger.error(f"❌ OpenRouter HTTP {r.status_code}: {r.text}")
            r.raise_for_status()

        try:
            data = r.json()
        except ValueError:
            logger.error("❌ OpenRouter returned non-JSON response")
            raise RuntimeError("OpenRouter returned invalid JSON")

        return data["choices"][0]["message"]["content"]

class CompositeProvider:
    """
    Unified LLM provider. Chooses backend based on LLM_MODE.
    """

    def __init__(self):
        if LLM_MODE == "browser":
            logger.info("🧠 Using Browser-based API-less LLM")
            self.provider = get_browser_llm()
        else:
            logger.info("🧠 Using OpenRouter LLM")
            self.provider = OpenRouterProvider()

    def ask(self, prompt: str) -> str:
        try:
            return self.provider.ask(prompt)
        except (HTTPError, RequestException, RuntimeError) as e:
            logger.error(f"❌ LLM request failed: {e}")
            raise

# -----------------------------
# Factory
# -----------------------------
def get_llm_provider():
    logger.info(f"🧠 LLM provider initialized ({LLM_MODE})")
    return CompositeProvider()
