import os
import requests
from scripts.logger import logger

GROQ_API_KEY = os.getenv("GROQ_API_KEY")
GROQ_MODEL = "llama-3.1-8b-instant"
GROQ_URL = "https://api.groq.com/openai/v1/chat/completions"


class GroqProvider:
    def __init__(self):
        if not GROQ_API_KEY:
            raise RuntimeError("GROQ_API_KEY not set")

    def ask(self, prompt: str, retry_count: int) -> str:
        # 🔒 HARD RULES BY RETRY
        if retry_count <= 1:
            system_rules = (
                "You are an expert Android/Kotlin build fixer.\n"
                "OUTPUT ONLY A UNIFIED GIT DIFF.\n"
                "DO NOT output full files.\n"
                "DO NOT replace entire files.\n"
                "Modify the smallest possible hunks only."
            )
        elif retry_count == 2:
            system_rules = (
                "You are an expert Android/Kotlin build fixer.\n"
                "OUTPUT ONLY A UNIFIED GIT DIFF.\n"
                "Full-file replacement is allowed ONLY if absolutely necessary."
            )
        else:
            system_rules = (
                "You are an expert Android/Kotlin build fixer.\n"
                "OUTPUT ONLY A UNIFIED GIT DIFF.\n"
                "EMERGENCY MODE: full-file replacement is permitted."
            )

        payload = {
            "model": GROQ_MODEL,
            "messages": [
                {"role": "system", "content": system_rules},
                {"role": "user", "content": prompt},
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

    def ask(self, prompt: str, retry_count: int) -> str:
        logger.info(f"🧠 Using Groq ({GROQ_MODEL}), retry={retry_count}")
        return self.provider.ask(prompt, retry_count)


def get_llm_provider():
    logger.info("🧠 LLM provider initialized (Groq)")
    return CompositeProvider()
