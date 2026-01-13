import asyncio
import os
import time
from pathlib import Path
from playwright.async_api import async_playwright, TimeoutError
from scripts.logger import logger

CHATGPT_URL = "https://chat.openai.com/"
PROFILE_DIR = Path(".browser_profile")
DEFAULT_TIMEOUT = 60
MAX_RESPONSE_CHARS = 12000


class BrowserLLMRunner:
    """
    Fully automatic:
    - First run: headful login
    - Later runs: headless
    - No cookies
    - No prompts
    """

    def __init__(self):
        self.timeout = DEFAULT_TIMEOUT

    def _should_run_headless(self) -> bool:
        # Headless ONLY if profile already exists
        return PROFILE_DIR.exists() and any(PROFILE_DIR.iterdir())

    async def _wait_for_input(self, page):
        """Try multiple selectors to find ChatGPT input"""
        selectors = [
            "textarea#prompt-textarea",                          # current textarea
            "textarea",                                         # fallback textarea
            "div[role='textbox'][contenteditable='true']"      # newest ChatGPT UI
        ]

        for sel in selectors:
            locator = page.locator(sel).first
            try:
                await locator.wait_for(state="visible", timeout=30000)
                return locator
            except Exception:
                continue

        raise RuntimeError("Chat input not found on page. Check ChatGPT UI changes.")

    async def _run(self, prompt: str) -> str:
        PROFILE_DIR.mkdir(exist_ok=True)

        headless = self._should_run_headless()

        logger.info(
            f"🧠 Chromium launch | persistent profile | headless={headless}"
        )

        async with async_playwright() as p:
            context = await p.chromium.launch_persistent_context(
                user_data_dir=str(PROFILE_DIR),
                headless=headless,
                args=[
                    "--no-sandbox",
                    "--disable-dev-shm-usage",
                ],
            )

            page = context.pages[0] if context.pages else await context.new_page()

            logger.info("🌐 Opening ChatGPT")
            await page.goto(CHATGPT_URL, timeout=60_000)

            if not headless:
                logger.info("🔐 First-time login mode")
                logger.info("➡️  Log in manually, then wait...")
                await self._wait_for_input(page)
                logger.info("✅ Login captured, continuing")

            textarea = await self._wait_for_input(page)

            logger.info("✍️ Sending prompt")
            await textarea.click()
            await textarea.fill(prompt)
            await textarea.press("Enter")

            start = time.time()
            last_text = ""

            while time.time() - start < self.timeout:
                await page.wait_for_timeout(1500)
                msgs = await page.query_selector_all("div.markdown")
                if not msgs:
                    continue

                text = (await msgs[-1].inner_text()).strip()
                if text and text != last_text:
                    last_text = text

                # Heuristic: response finished
                if len(text) > 200 and not text.endswith("…"):
                    break

            await context.close()

            if not last_text:
                raise RuntimeError("No response captured")

            logger.info("✅ Browser LLM response captured")
            return last_text[:MAX_RESPONSE_CHARS]

    def ask(self, prompt: str) -> str:
        try:
            return asyncio.run(self._run(prompt))
        except TimeoutError:
            raise RuntimeError("Browser LLM timed out")


def get_browser_llm():
    return BrowserLLMRunner()
