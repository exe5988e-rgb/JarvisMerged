import asyncio
import json
import time
from pathlib import Path
from playwright.async_api import async_playwright, TimeoutError
from scripts.logger import logger

CHATGPT_URL = "https://chat.openai.com/"
DEFAULT_TIMEOUT = 45
MAX_RESPONSE_CHARS = 12000

COOKIES_FILE = Path(".cookies.json")


class BrowserLLMRunner:
    """
    API-less LLM runner using headless Chromium with cookie-based login.
    Safe for GitHub Actions.
    """

    def __init__(self):
        self.headless = True
        self.timeout = DEFAULT_TIMEOUT

    # ----------------------------
    # Cookie handling (FIXED)
    # ----------------------------
    def _normalize_samesite(self, value):
        if not value:
            return "Lax"
        value = value.lower()
        if value == "strict":
            return "Strict"
        if value == "none":
            return "None"
        return "Lax"

    async def _load_cookies(self, context):
        if not COOKIES_FILE.exists():
            logger.warning("⚠️ Cookie file not found; login screen may appear")
            return

        try:
            cookies = json.loads(COOKIES_FILE.read_text())
            for c in cookies:
                c["sameSite"] = self._normalize_samesite(c.get("sameSite"))
            await context.add_cookies(cookies)
            logger.info("🔑 Cookies loaded successfully")
        except Exception as e:
            logger.error(f"❌ Failed to load cookies: {e}")

    # ----------------------------
    # Chat input detection (FIXED)
    # ----------------------------
    async def _wait_for_input(self, page):
        try:
            textarea = page.locator("textarea[placeholder*='Message']")
            await textarea.wait_for(state="visible", timeout=60000)
            return textarea
        except Exception as e:
            raise RuntimeError(f"Chat input not found: {e}")

    # ----------------------------
    # Main browser LLM runner
    # ----------------------------
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

            context = await browser.new_context()
            await self._load_cookies(context)

            page = await context.new_page()
            logger.info("🌐 Opening ChatGPT UI")
            await page.goto(CHATGPT_URL, timeout=60_000)

            logger.info("⏳ Waiting for chat input")
            textarea = await self._wait_for_input(page)

            logger.info("✍️ Sending prompt")
            await textarea.click()
            await textarea.fill(prompt)
            await textarea.press("Enter")

            start = time.time()
            last_text = ""

            while time.time() - start < self.timeout:
                await page.wait_for_timeout(1500)

                messages = await page.query_selector_all("div.markdown")
                if not messages:
                    continue

                last = messages[-1]
                text = (await last.inner_text()).strip()

                if text and text != last_text:
                    last_text = text

                # heuristic: response complete
                if len(text) > 200 and not text.endswith("…"):
                    break

            await browser.close()

            if not last_text:
                raise RuntimeError("No response captured from browser LLM")

            logger.info("✅ Browser LLM response captured")
            return last_text[:MAX_RESPONSE_CHARS]

    # ----------------------------
    # Public API
    # ----------------------------
    def ask(self, prompt: str) -> str:
        logger.info("🧠 Using Browser-based LLM (API-less, cookies)")
        try:
            return asyncio.run(self._run(prompt))
        except TimeoutError:
            raise RuntimeError("Browser LLM timed out")
        except Exception as e:
            logger.error(f"❌ Browser LLM failed: {e}")
            raise


def get_browser_llm():
    return BrowserLLMRunner()
