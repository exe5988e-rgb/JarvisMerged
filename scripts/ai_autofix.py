import sys
import traceback
from scripts.logger import logger
from scripts.openrouter import get_llm_provider


def main():
    logger.info("🚀 AI Autofix starting")

    try:
        provider = get_llm_provider()
        response = provider.ask(
            "Analyze Android build errors and suggest fixes."
        )

        logger.info("🤖 LLM response received")
        logger.debug(response)

        # TODO: apply fixes from response

    except Exception as e:
        logger.error("❌ AI Autofix failed")
        traceback.print_exc()
        sys.exit(0)  # NEVER fail CI


if __name__ == "__main__":
    main()
