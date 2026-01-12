import asyncio
import os
import time
import re
from playwright.async_api import async_playwright, TimeoutError

from scripts.logger import logger


CHAT_URL = "https://chat.openai.com/"
MAX_WAIT_SECONDS = 60
MAX_RESPONSE_CHARS = 12000
POLL_INTERVAL_MS = 1200


class BrowserLLMRunner:
    def __init__(self):
        self.headless = True

    async def _wait_for_input(self, page):
        for _ in range(50):
            textarea = await page.query_selector("textarea")
            if textarea:
                return textarea
            await page.wait_for_timeout(500)
        raise RuntimeError("Chat input not found")

    async def _collect_response(self, page) -> str:
        start = time.time()
        last_text = ""

        while time.time() - start < MAX_WAIT_SECONDS:
            await page.wait_for_timeout(POLL_INTERVAL_MS)

            blocks = await page.query_selector_all("div.markdown")
            if not blocks:
                continue

            text = (await blocks[-1].inner_text()).strip()
            if not text or text == last_text:
                continue

            last_text = text

            if text.startswith("diff --git"):
                return self._sanitize(text)

        raise RuntimeError("Timed out waiting for LLM response")

    def _sanitize(self, text: str) -> str:
        text = text.replace("\u200b", "")
        text = text.replace("\r\n", "\n").strip()

        match = re.search(r"(diff --git[\s\S]+)", text)
        if not match:
            raise RuntimeError("No valid git diff found in response")

        diff = match.group(1)
        return diff[:MAX_RESPONSE_CHARS]

    async def _run(self, prompt: str) -> str:
        async with async_playwright() as p:
            browser = await p.chromium.launch(
                headless=self.headless,
                args=[
                    "--no-sandbox",
                    "--disable-dev-shm-usage",
                    "--disable-blink-features=AutomationControlled",
                ],
            )

            context = await browser.new_context(
                viewport={"width": 1280, "height": 800}
            )
            page = await context.new_page()

            logger.info("🌐 Opening ChatGPT UI")
            await page.goto(CHAT_URL, timeout=90_000)

            logger.info("⏳ Waiting for chat input")
            textarea = await self._wait_for_input(page)

            await textarea.fill(prompt)
            await textarea.press("Enter")

            logger.info("🧠 Waiting for browser LLM response")
            response = await self._collect_response(page)

            await browser.close()
            logger.info("✅ Browser LLM response captured")

            return response

    def ask(self, prompt: str) -> str:
        logger.info("🧠 Using browser-based LLM")
        try:
            return asyncio.run(self._run(prompt))
        except TimeoutError:
            raise RuntimeError("Browser LLM timed out")
        except Exception as e:
            logger.error(f"❌ Browser LLM failed: {e}")
            raise


def get_browser_llm():
    return BrowserLLMRunner()
