import os
import requests
from requests import HTTPError, RequestException
from scripts.logger import logger

GEMINI_API_KEY = os.getenv("GEMINI_API_KEY")
GEMINI_MODEL = "gemini-2.0-flash"


class GeminiProvider:
    def __init__(self):
        if not GEMINI_API_KEY:
            raise RuntimeError("GEMINI_API_KEY not set")
        self.url = f"https://generativelanguage.googleapis.com/v1beta/models/{GEMINI_MODEL}:generateContent?key={GEMINI_API_KEY}"

    def ask(self, prompt: str) -> str:
        payload = {
            "contents": [
                {
                    "parts": [
                        {"text": prompt}
                    ]
                }
            ],
            "generationConfig": {
                "temperature": 0.1,
                "maxOutputTokens": 8192,
            },
            "systemInstruction": {
                "parts": [
                    {"text": "You are an expert Android/Kotlin build fixer. Output ONLY valid unified git diffs."}
                ]
            }
        }

        headers = {
            "Content-Type": "application/json",
        }

        r = requests.post(
            self.url,
            headers=headers,
            json=payload,
            timeout=120,
        )

        if not r.ok:
            logger.error(f"❌ Gemini HTTP {r.status_code}: {r.text}")
            r.raise_for_status()

        try:
            data = r.json()
        except ValueError:
            logger.error("❌ Gemini returned non-JSON response")
            raise RuntimeError("Gemini returned invalid JSON")

        try:
            return data["candidates"][0]["content"]["parts"][0]["text"]
        except (KeyError, IndexError) as e:
            logger.error(f"❌ Malformed Gemini response: {data}")
            raise RuntimeError("Gemini response malformed") from e


class CompositeProvider:
    def __init__(self):
        self.provider = GeminiProvider()

    def ask(self, prompt: str) -> str:
        logger.info(f"🧠 Using Gemini ({GEMINI_MODEL})")
        try:
            return self.provider.ask(prompt)
        except (HTTPError, RequestException, RuntimeError) as e:
            logger.error(f"❌ LLM request failed: {e}")
            raise


def get_llm_provider():
    logger.info("🧠 LLM provider initialized (Gemini)")
    return CompositeProvider()
