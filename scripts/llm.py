import os
import requests
from scripts.logger import logger

GROQ_API_KEY = os.getenv("GROQ_API_KEY")
GROQ_MODEL = "llama3-8b-8192"
GROQ_URL = "https://api.groq.com/openai/v1/chat/completions"


class GroqProvider:
    def __init__(self):
        if not GROQ_API_KEY:
            raise RuntimeError("GROQ_API_KEY not set")

    def ask(self, prompt: str) -> str:
        payload = {
            "model": GROQ_MODEL,
            "messages": [
                {
                    "role": "system",
                    "content": "You are an expert Android/Kotlin build fixer. Output ONLY valid unified git diffs."
                },
                {
                    "role": "user",
                    "content": prompt
                }
            ],
            "temperature": 0.1,
            "max_tokens": 4096,
        }

        headers = {
            "Authorization": f"Bearer {GROQ_API_KEY}",
            "Content-Type": "application/json",
        }

        r = requests.post(
            GROQ_URL,
            headers=headers,
            json=payload,
            timeout=60,
        )

        if not r.ok:
            logger.error(f"❌ Groq HTTP {r.status_code}: {r.text}")
            r.raise_for_status()

        data = r.json()

        try:
            return data["choices"][0]["message"]["content"]
        except (KeyError, IndexError) as e:
            logger.error(f"❌ Malformed Groq response: {data}")
            raise RuntimeError("Groq response malformed") from e


class CompositeProvider:
    def __init__(self):
        self.provider = GroqProvider()

    def ask(self, prompt: str) -> str:
        logger.info(f"🧠 Using Groq ({GROQ_MODEL})")
        return self.provider.ask(prompt)


def get_llm_provider():
    logger.info("🧠 LLM provider initialized (Groq)")
    return CompositeProvider()
