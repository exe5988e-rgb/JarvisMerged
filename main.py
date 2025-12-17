import sys
import os
import traceback
import subprocess
from scripts.logger import logger
from scripts.openrouter import get_llm_provider
from scripts.error_parser import parse_build_errors, extract_code_snippet
from scripts.patch_applier import apply_patch
from scripts.git_utils import commit_changes
from scripts.github import create_branch, push_branch, open_pull_request


def main():
    logger.info("🚀 AI Autofix starting")

    try:
        # 🔧 FIX: Read build output from workflow log
        if len(sys.argv) < 2:
            logger.error("❌ Build log file not provided")
            return

        build_log_path = sys.argv[1]

        if not os.path.exists(build_log_path):
            logger.error(f"❌ Build log not found: {build_log_path}")
            return

        with open(build_log_path, "r", encoding="utf-8", errors="ignore") as f:
            build_output = f.read()

        # ✅ FIX: Correct build status detection
        if "BUILD SUCCESSFUL" in build_output:
            logger.info("✅ Build passed - no fixes needed")
            return

        if "BUILD FAILED" not in build_output:
            logger.warning("⚠️ Build status unclear - skipping autofix")
            return

        logger.info("❌ Build failed - analyzing errors...")

        # Step 2: Parse errors
        errors = parse_build_errors(build_output)

        if not errors:
            logger.warning("⚠️ Could not parse any errors from build output")
            return

        # Step 3: Extract code snippets
        context_parts = []
        affected_files = set()

        for error in errors:
            file_path = error["file"]
            line_num = error["line"]
            error_msg = error["message"]

            snippet = extract_code_snippet(file_path, line_num, context_lines=20)

            if snippet:
                affected_files.add(file_path)
                context_parts.append(
                    f"### Error in `{file_path}` at line {line_num}:\n"
                    f"**Error message:** {error_msg}\n\n"
                    f"**Code snippet (lines {snippet['start_line']}-{snippet['end_line']}):**\n"
                    f"```\n{snippet['code']}\n```\n"
                )

        if not context_parts:
            logger.warning("⚠️ Could not extract code snippets")
            return

        # Step 4: Ask LLM for patch
        prompt = f"""You are a code fixer. Analyze these Android build errors and provide ONLY a unified diff patch to fix them.

{chr(10).join(context_parts)}

Respond with ONLY the patch in unified diff format (starting with --- and +++). No explanations.
"""

        provider = get_llm_provider()
        patch_response = provider.ask(prompt)

        logger.info("🤖 LLM patch received")

        # Step 5: Apply patch
        patched_files = apply_patch(patch_response)

        if not patched_files:
            logger.warning("⚠️ No files were patched")
            return

        logger.info(f"📝 Patched files: {patched_files}")

        # Step 6: Commit & PR
        branch_name = "ai-autofix/build-fix"

        create_branch(branch_name)
        commit_changes("fix: AI-generated build fix", list(patched_files))
        push_branch(branch_name)

        open_pull_request(
            branch_name,
            "🤖 AI Autofix: Build Error Fix",
            f"This PR was automatically generated to fix build errors.\n\n"
            f"**Fixed files:**\n" + "\n".join(f"- `{f}`" for f in patched_files)
        )

        logger.info("🎉 AI Autofix completed successfully!")

    except Exception as e:
        logger.error(f"❌ AI Autofix failed: {e}")
        traceback.print_exc()
        sys.exit(0)  # NEVER fail CI


if __name__ == "__main__":
    main()
