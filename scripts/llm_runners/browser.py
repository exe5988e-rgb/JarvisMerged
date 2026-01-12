import asyncio
import os
import time
from playwright.async_api import async_playwright, TimeoutError
from scripts.logger import logger


# ==================================================
# CONFIG
# ==================================================

CHATGPT_URL = "https://chat.openai.com/"
DEFAULT_TIMEOUT = 45  # seconds
MAX_RESPONSE_CHARS = 12000


# ==================================================
# Browser LLM Runner
# ==================================================

class BrowserLLMRunner:
    """
    API-less LLM runner using headless Chromium.
    Runs ONLY inside GitHub Actions.
    """

    def __init__(self):
        self.headless = True
        self.timeout = DEFAULT_TIMEOUT

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
            page = await context.new_page()

            logger.info("🌐 Opening ChatGPT web UI")
            await page.goto(CHATGPT_URL, timeout=60_000)

            # -------- Login Wait (manual cookie reuse if needed later) --------
            logger.info("⏳ Waiting for chat input box")
            await page.wait_for_selector("textarea", timeout=60_000)

            textarea = await page.query_selector("textarea")
            if not textarea:
                raise RuntimeError("Chat input box not found")

            # -------- Send prompt --------
            logger.info("✍️ Sending prompt to browser LLM")
            await textarea.fill(prompt)
            await textarea.press("Enter")

            # -------- Wait for response --------
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

                # Heuristic: response looks complete
                if len(text) > 200 and not text.endswith("…"):
                    break

            await browser.close()

            if not last_text:
                raise RuntimeError("No response captured from browser LLM")

            logger.info("✅ Browser LLM response captured")
            return last_text[:MAX_RESPONSE_CHARS]

    # ==================================================
    # Public API (sync wrapper)
    # ==================================================

    def ask(self, prompt: str) -> str:
        logger.info("🧠 Using Browser-based LLM (API-less)")
        try:
            return asyncio.run(self._run(prompt))
        except TimeoutError:
            raise RuntimeError("Browser LLM timed out")
        except Exception as e:
            logger.error(f"❌ Browser LLM failed: {e}")
            raise


# ==================================================
# Factory
# ==================================================

def get_browser_llm():
    return BrowserLLMRunner()
