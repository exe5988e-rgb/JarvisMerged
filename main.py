import sys
import traceback
import subprocess
from scripts.logger import logger
from scripts.openrouter import get_llm_provider
from scripts.error_parser import parse_build_errors, extract_code_snippet
from scripts.patch_applier import apply_patch
from scripts.git_utils import commit_changes
from scripts.github import create_branch, push_branch, open_pull_request

def run_build() -> tuple[bool, str]:
    """Run the build and return (success, output)."""
    result = subprocess.run(
        ["./gradlew", "build"],
        capture_output=True,
        text=True
    )
    return result.returncode == 0, result.stdout + result.stderr

def main():
    logger.info("🚀 AI Autofix starting")

    try:
        # Step 1: Run initial build and capture errors
        success, build_output = run_build()
        
        if success:
            logger.info("✅ Build passed - no fixes needed")
            return

        logger.info("❌ Build failed - analyzing errors...")

        # Step 2: Parse errors and extract code snippets
        errors = parse_build_errors(build_output)
        
        if not errors:
            logger.warning("⚠️ Could not parse any errors from build output")
            sys.exit(0)

        # Step 3: Build context with error snippets (±20 lines)
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
            sys.exit(0)

        # Step 4: Send to AI for patch generation
        prompt = f"""You are a code fixer. Analyze these Android build errors and provide ONLY a unified diff patch to fix them.

{chr(10).join(context_parts)}

Respond with ONLY the patch in unified diff format (starting with --- and +++). No explanations."""

        provider = get_llm_provider()
        patch_response = provider.ask(prompt)

        logger.info("🤖 LLM patch received")
        logger.debug(patch_response)

        # Step 5: Apply the patch
        patched_files = apply_patch(patch_response)
        
        if not patched_files:
            logger.warning("⚠️ No files were patched")
            sys.exit(0)

        logger.info(f"📝 Patched files: {patched_files}")

        # Step 6: Verify build passes (green build check)
        logger.info("🔨 Running verification build...")
        verify_success, _ = run_build()
        
        if not verify_success:
            logger.error("❌ Patch did not fix the build - aborting")
            # Revert changes
            subprocess.run(["git", "checkout", "--"] + list(patched_files), check=True)
            sys.exit(0)

        logger.info("✅ Verification build passed!")

        # Step 7: Commit, push, and create PR
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
