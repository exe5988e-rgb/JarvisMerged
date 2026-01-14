import os
import re
import subprocess
import tempfile
from typing import List, Optional, Tuple

try:
    from scripts.logger import logger
except ImportError:
    import logging
    logger = logging.getLogger("patch_applier")


# --------------------------------------------------
# Utilities
# --------------------------------------------------

def run(cmd: List[str], cwd: Optional[str] = None) -> Tuple[int, str]:
    p = subprocess.run(cmd, capture_output=True, text=True, cwd=cwd)
    return p.returncode, p.stdout + p.stderr


# --------------------------------------------------
# Full-file diff detection
# --------------------------------------------------

def is_full_file_diff(diff: str) -> bool:
    if "@@" not in diff:
        return True

    deleted = 0
    added = 0

    for line in diff.splitlines():
        if line.startswith("---") or line.startswith("+++"):
            continue
        if line.startswith("-"):
            deleted += 1
        elif line.startswith("+"):
            added += 1

    return deleted > 50 and added > 50


# --------------------------------------------------
# Diff sanitization
# --------------------------------------------------

HEADER_TOKENS = ("diff --git ", "index ", "--- ", "+++ ")


def sanitize_unified_diff(diff: str) -> str:
    if not diff:
        return ""

    diff = diff.replace("\r\n", "\n").replace("\r", "\n")
    diff = diff.replace("\u00a0", " ").replace("\u200b", "").replace("\ufeff", "")

    lines = [l.rstrip() for l in diff.splitlines()]
    text = "\n".join(lines).strip() + "\n"

    if not text.startswith("diff --git"):
        return ""

    return text


def extract_diff(text: str) -> str:
    text = re.sub(r"```(?:diff|patch)?", "", text)
    text = text.replace("```", "")

    lines = []
    in_diff = False
    for line in text.splitlines():
        if line.startswith("diff --git"):
            in_diff = True
        if in_diff:
            lines.append(line)

    return "\n".join(lines).strip() + "\n"


# --------------------------------------------------
# Patch application
# --------------------------------------------------

def extract_files_from_diff(diff: str) -> List[str]:
    return [
        line[6:]
        for line in diff.splitlines()
        if line.startswith("+++ b/")
    ]


def try_git_apply(diff: str, options: List[str] = None) -> bool:
    options = options or []

    with tempfile.NamedTemporaryFile("w+", delete=False) as tf:
        tf.write(diff)
        path = tf.name

    try:
        code, _ = run(["git", "apply", "--check"] + options + [path])
        if code != 0:
            return False

        code, _ = run(["git", "apply"] + options + [path])
        return code == 0
    finally:
        os.unlink(path)


# --------------------------------------------------
# Public API
# --------------------------------------------------

def apply_patch(ai_response: str, build_log: str, retry_count: int = 1) -> List[str]:
    diff = extract_diff(ai_response)
    diff = sanitize_unified_diff(diff)

    if not diff:
        logger.warning("⚠️ No valid diff found")
        return []

    is_full = is_full_file_diff(diff)

    # 🔒 HARD BLOCK only on first retry
    if retry_count <= 1 and is_full:
        logger.error("🚫 Full-file overwrite rejected on retry 1")
        return []

    diff = fix_diff_paths(diff)
    files = extract_files_from_diff(diff)

    if not files:
        logger.warning("⚠️ No target files detected")
        return []

    # 1️⃣ Try normal git apply first
    strategies = [
        [],
        ["--ignore-whitespace"],
        ["--3way"],
    ]

    for opts in strategies:
        if try_git_apply(diff, opts):
            logger.info(f"✅ Patch applied via git: {files}")
            return files

    # 2️⃣ FULL-FILE FALLBACK (retry ≥ 2)
    if is_full and retry_count >= 2:
        logger.warning("📝 Git apply failed — using full-file overwrite fallback")

        repo_root = get_repo_root()

        # Split diff into per-file blocks
        file_blocks = re.split(r"(?=^diff --git )", diff, flags=re.MULTILINE)

        written = []

        for block in file_blocks:
            if not block.strip():
                continue

            m = re.search(r"\+\+\+ b/(.+)", block)
            if not m:
                continue

            path = m.group(1)
            abs_path = os.path.join(repo_root, path)

            content_lines = []
            in_hunk = False

            for line in block.splitlines():
                if line.startswith("@@"):
                    in_hunk = True
                    continue
                if not in_hunk:
                    continue
                if line.startswith("+") and not line.startswith("+++"):
                    content_lines.append(line[1:])
                elif not line.startswith("-"):
                    content_lines.append(line)

            content = "\n".join(content_lines).strip() + "\n"

            if is_structurally_corrupt(content):
                logger.error(f"❌ Structural validation failed: {path}")
                continue

            write_file(abs_path, content)
            written.append(path)

        if written:
            logger.info(f"✅ Full-file overwrite successful: {written}")
            return written

        logger.error("❌ Full-file fallback failed")

    logger.warning("⚠️ All patch strategies failed")
    return []

# --------------------------------------------------
# Structural validation
# --------------------------------------------------

def is_structurally_corrupt(content: str) -> bool:
    if not content:
        return True
    if "package " not in content:
        return True
    if content.count("{") < content.count("}"):
        return True
    return False
