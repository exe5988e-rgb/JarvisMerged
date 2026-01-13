import asyncio
import json
import os
import time
from pathlib import Path
from playwright.async_api import async_playwright, TimeoutError
from scripts.logger import logger

CHATGPT_URL = "https://chat.openai.com/"
DEFAULT_TIMEOUT = 60
MAX_RESPONSE_CHARS = 12000

COOKIES_FILE = Path(".cookies.json")
HEADLESS = os.getenv("HEADLESS", "1") != "0"


class BrowserLLMRunner:
    """
    API-less LLM runner using Chromium + cookies.
    No persistent profile.
    """

    def __init__(self):
        self.headless = HEADLESS
        self.timeout = DEFAULT_TIMEOUT

    # -------------------------------------------------
    # Cookie sanitization (Playwright-safe)
    # -------------------------------------------------
    def _sanitize_cookie(self, c: dict) -> dict:
        c = dict(c)

        # sameSite must be Strict | Lax | None
        ss = c.get("sameSite")
        if not ss:
            c["sameSite"] = "Lax"
        else:
            ss = ss.lower()
            if ss == "strict":
                c["sameSite"] = "Strict"
            elif ss == "none":
                c["sameSite"] = "None"
            else:
                c["sameSite"] = "Lax"

        # Remove invalid partitionKey
        if "partitionKey" in c and not isinstance(c["partitionKey"], str):
            del c["partitionKey"]

        return c

    async def _load_cookies(self, context):
        if not COOKIES_FILE.exists():
            logger.warning("⚠️ Cookie file not found; login may be required")
            return

        try:
            raw = json.loads(COOKIES_FILE.read_text())
            cookies = [self._sanitize_cookie(c) for c in raw]
            await context.add_cookies(cookies)
            logger.info("🔑 Cookies loaded successfully")
        except Exception as e:
            logger.error(f"❌ Failed to load cookies: {e}")

    # -------------------------------------------------
    # Robust chat input detection (page + iframes)
    # -------------------------------------------------
    async def _find_chat_input(self, page):
        start = time.time()

        while time.time() - start < 45:
            # Top-level page
            textarea = page.locator("textarea#prompt-textarea")
            if await textarea.count() > 0:
                await textarea.first.wait_for(state="visible", timeout=5000)
                return textarea.first

            textarea = page.locator("textarea")
            if await textarea.count() > 0:
                await textarea.first.wait_for(state="visible", timeout=5000)
                return textarea.first

            # Iframes
            for frame in page.frames:
                ta = frame.locator("textarea#prompt-textarea")
                if await ta.count() > 0:
                    await ta.first.wait_for(state="visible", timeout=5000)
                    return ta.first

                ta = frame.locator("textarea")
                if await ta.count() > 0:
                    await ta.first.wait_for(state="visible", timeout=5000)
                    return ta.first

            # Login / captcha detection
            url = page.url.lower()
            if any(x in url for x in ("login", "auth", "signin", "challenge")):
                raise RuntimeError(
                    "ChatGPT login or verification page detected. "
                    "Cookies may be invalid or expired."
                )

            await page.wait_for_timeout(1000)

        raise RuntimeError("Chat input not found (no textarea detected)")

    # -------------------------------------------------
    # Main browser LLM logic
    # -------------------------------------------------
    async def _run(self, prompt: str) -> str:
        async with async_playwright() as p:
            logger.info(f"🧠 Launching Chromium (headless={self.headless})")

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
            textarea = await self._find_chat_input(page)

            logger.info("✍️ Sending prompt")
            await textarea.click()
            await textarea.fill(prompt)
            await textarea.press("Enter")

            start = time.time()
            last_text = ""

            while time.time() - start < self.timeout:
                await page.wait_for_timeout(1500)

                for frame in page.frames:
                    messages = await frame.query_selector_all("div.markdown")
                    if not messages:
                        continue

                    text = (await messages[-1].inner_text()).strip()
                    if text and text != last_text:
                        last_text = text

                if len(last_text) > 200 and not last_text.endswith("…"):
                    break

            await browser.close()

            if not last_text:
                raise RuntimeError("No response captured from browser LLM")

            logger.info("✅ Browser LLM response captured")
            return last_text[:MAX_RESPONSE_CHARS]

    # -------------------------------------------------
    # Public API
    # -------------------------------------------------
    def ask(self, prompt: str) -> str:
        logger.info("🧠 Using Browser-based LLM (cookies, no profile)")
        try:
            return asyncio.run(self._run(prompt))
        except TimeoutError:
            raise RuntimeError("Browser LLM timed out")
        except Exception as e:
            logger.error(f"❌ Browser LLM failed: {e}")
            raise


def get_browser_llm():
    return BrowserLLMRunner()
