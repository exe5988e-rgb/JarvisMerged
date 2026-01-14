import subprocess
import traceback
from pathlib import Path

from scripts.logger import logger
from scripts.llm import get_llm_provider
from scripts.error_parser import parse_build_errors, extract_code_snippet
from scripts.patch_applier import apply_patch, is_structurally_corrupt
from scripts.git_utils import commit_changes, create_branch, push_branch

MAX_RETRIES = 3
DEBUG_DIR = Path(".ai_debug")
CONTEXT_DIR = Path(".ai_context")
MAX_FILE_CHARS = 8000


# --------------------------------------------------
# Context builders
# --------------------------------------------------

def build_file_tree() -> str:
    result = subprocess.run(
        ["find", "android", "-type", "f"],
        capture_output=True,
        text=True,
    )
    return "\n".join(sorted(result.stdout.strip().splitlines()))


def read_file_safe(path: Path) -> str:
    try:
        return path.read_text(encoding="utf-8", errors="ignore")
    except Exception:
        return ""


def prepare_context():
    CONTEXT_DIR.mkdir(exist_ok=True)
    (CONTEXT_DIR / "file_tree.txt").write_text(build_file_tree())
    (CONTEXT_DIR / "AndroidManifest.xml").write_text(
        read_file_safe(Path("android/app/src/main/AndroidManifest.xml"))
    )
    (CONTEXT_DIR / "app_build.gradle").write_text(
        read_file_safe(Path("android/app/build.gradle"))
    )


# --------------------------------------------------
# Build helpers
# --------------------------------------------------

def read_build_log():
    try:
        output = Path("build.log").read_text(encoding="utf-8", errors="ignore")
        return "BUILD FAILED" not in output, output
    except FileNotFoundError:
        return False, ""


def filter_errors_only(errors: list[dict]) -> list[dict]:
    filtered = []
    for e in errors:
        msg = (e.get("message") or "").lower()
        if any(k in msg for k in ["warning", "deprecated", "uses or overrides a deprecated api"]):
            continue
        filtered.append(e)
    return filtered


def classify_error(msg: str) -> str:
    msg = msg.lower()
    if "unresolved reference" in msg or "cannot find symbol" in msg:
        return "missing_symbol"
    if "type mismatch" in msg:
        return "type_error"
    if "overrides nothing" in msg:
        return "override_error"
    if "manifest merger failed" in msg:
        return "manifest"
    if "execution failed for task" in msg:
        return "gradle"
    if "cannot access" in msg:
        return "visibility"
    return "generic"


def retry_mode(attempt: int) -> str:
    if attempt == 1:
        return "normal"
    if attempt == 2:
        return "strict"
    return "emergency"


def estimate_confidence(diff: str) -> float:
    score = 1.0
    if diff.count("diff --git") > 4:
        score -= 0.3
    if "todo" in diff.lower() or "fixme" in diff.lower():
        score -= 0.3
    if "stub" in diff.lower():
        score -= 0.2
    if "temporary" in diff.lower():
        score -= 0.2
    return max(score, 0.0)


# --------------------------------------------------
# Autofix attempt
# --------------------------------------------------

def run_autofix_attempt(attempt: int) -> bool:
    logger.info(f"🔁 Autofix attempt {attempt}/{MAX_RETRIES}")

    build_ok, build_log = read_build_log()
    if build_ok:
        logger.info("✅ Build already successful")
        return True

    errors = parse_build_errors(build_log)
    errors = filter_errors_only(errors)
    logger.info(f"📋 Parsed {len(errors)} build errors (warnings ignored)")

    if not errors:
        return False

    error = errors[0]
    file_path = error.get("file")
    line = error.get("line")
    message = error.get("message")

    if not file_path or not line:
        return False

    full_file = read_file_safe(Path(file_path))
    snippet = extract_code_snippet(file_path, line, 12)

    # 🚨 Structural corruption detection BEFORE LLM
    if is_structurally_corrupt(full_file):
        logger.error("🚨 Structural corruption detected — aborting autofix")
        DEBUG_DIR.mkdir(exist_ok=True)
        (DEBUG_DIR / "structural_failure.txt").write_text(
            f"File: {file_path}\n\n{full_file}"
        )
        create_branch("ai-autofix/structural-failure")
        commit_changes(
            "debug: structural corruption detected",
            [str(DEBUG_DIR / "structural_failure.txt")]
        )
        push_branch("ai-autofix/structural-failure")
        return True

    prepare_context()

    error_type = classify_error(message)
    mode = retry_mode(attempt)

    extra_rules = ""
    if mode == "strict":
        extra_rules = "\nINVALID OUTPUT PREVIOUSLY. RETURN ONLY A GIT DIFF."
    elif mode == "emergency":
        extra_rules = "\nEMERGENCY MODE. APPLY MINIMAL STUB FIX ONLY."

    prompt = f"""SYSTEM:
You are an automated Android build-fixing agent.{extra_rules}

RULES (MANDATORY):
- Output ONLY a valid unified git diff.
- Do NOT include explanations, markdown, comments, or code fences.
- Do NOT repeat warnings.
- Fix ONLY build-breaking errors.
- Every change MUST be in diff format.
- If unsure, apply the smallest possible stub fix.

The output MUST start with:
diff --git

=== FIX STRATEGY ===
Error category: {error_type}

=== BUILD ERROR ===
File: {file_path}
Line: {line}
Error: {message}

=== CODE SNIPPET ===
{snippet.get('code') if snippet else ''}

=== FULL FILE ===
{full_file[:MAX_FILE_CHARS]}
"""

    provider = get_llm_provider()

    # ✅ CRITICAL FIX: pass retry count to LLM
    response = provider.ask(prompt, retry_count=attempt)

    DEBUG_DIR.mkdir(exist_ok=True)
    debug_file = DEBUG_DIR / f"attempt_{attempt}.txt"
    debug_file.write_text(response)

    response = response.lstrip()
    if not response.startswith("diff --git"):
        logger.warning("⚠️ Invalid LLM output (not a diff)")
        return False

    patched = apply_patch(response, build_log)
    if not patched:
        logger.warning("⚠️ Patch failed")
        return False

    subprocess.run(["./gradlew", "build"], check=False)
    ok, _ = read_build_log()
    if not ok:
        subprocess.run(["git", "reset", "--hard", "HEAD"], check=False)
        return False

    confidence = estimate_confidence(response)
    logger.info(f"🧠 Fix confidence: {confidence:.2f}")

    if confidence < 0.5:
        return False

    branch = "ai-autofix/build-fix"
    create_branch(branch)
    commit_changes("fix: AI autofix build error", patched)
    push_branch(branch)

    logger.info("🎉 Autofix successful")
    return True


def main():
    logger.info("🚀 AI Autofix starting")
    try:
        for i in range(1, MAX_RETRIES + 1):
            if run_autofix_attempt(i):
                return
        logger.error("❌ Autofix failed after max retries")
    except Exception as e:
        logger.error(f"💥 Autofix crashed: {e}")
        traceback.print_exc()


if __name__ == "__main__":
    main()
