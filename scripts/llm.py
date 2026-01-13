import os
import requests
from scripts.logger import logger

GEMINI_API_KEY = os.getenv("GEMINI_API_KEY")
GEMINI_URL = "https://gemini.api/v1/completions"
GEMINI_MODEL = "gemini-coder-pro"

class GeminiProvider:
    def __init__(self):
        if not GEMINI_API_KEY:
            raise RuntimeError("GEMINI_API_KEY not set")

    def ask(self, prompt: str) -> str:
        payload = {
            "model": GEMINI_MODEL,
            "prompt": prompt,
            "temperature": 0.1,
            "max_tokens": 2000,
        }
        headers = {"Authorization": f"Bearer {GEMINI_API_KEY}"}
        r = requests.post(GEMINI_URL, json=payload, headers=headers, timeout=60)
        r.raise_for_status()
        data = r.json()
        return data["choices"][0]["text"]

class CompositeProvider:
    def __init__(self):
        self.provider = GeminiProvider()

    def ask(self, prompt: str) -> str:
        logger.info("🧠 Using Gemini (coder-pro)")
        return self.provider.ask(prompt)

def get_llm_provider():
    logger.info("🧠 LLM provider initialized (Gemini only)")
    return CompositeProvider()
