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


def write_file(path: str, content: str):
    os.makedirs(os.path.dirname(path) or ".", exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        f.write(content)


def get_repo_root() -> str:
    code, out = run(["git", "rev-parse", "--show-toplevel"])
    return out.strip() if code == 0 else os.getcwd()


# --------------------------------------------------
# Full-file diff detection (HARD GATE)
# --------------------------------------------------

def is_full_file_diff(diff: str) -> bool:
    if "@@" not in diff:
        return True

    deleted = 0
    added = 0

    for line in diff.splitlines():
        if line.startswith("--- ") or line.startswith("+++ "):
            continue
        if line.startswith("-"):
            deleted += 1
        elif line.startswith("+"):
            added += 1

    return deleted > 50 and added > 50


def extract_full_file_content(diff: str) -> str:
    """
    Reconstruct full file from a full-file diff.
    """
    lines = []
    for line in diff.splitlines():
        if line.startswith("+") and not line.startswith("+++"):
            lines.append(line[1:])
    return "\n".join(lines).rstrip() + "\n"


# --------------------------------------------------
# Diff sanitization
# --------------------------------------------------

HEADER_TOKENS = ("diff --git ", "index ", "--- ", "+++ ")


def split_glued_headers(line: str) -> List[str]:
    parts = []
    rest = line

    while True:
        indices = [(rest.find(tok), tok) for tok in HEADER_TOKENS if rest.find(tok) > 0]
        if not indices:
            parts.append(rest.rstrip())
            break

        idx, _ = min(indices, key=lambda x: x[0])
        parts.append(rest[:idx].rstrip())
        rest = rest[idx:]

    return [p for p in parts if p]


def sanitize_unified_diff(diff: str) -> str:
    if not diff:
        return ""

    diff = diff.replace("\r\n", "\n").replace("\r", "\n")
    diff = diff.replace("\u00a0", " ").replace("\u200b", "").replace("\ufeff", "")

    fixed = []
    for line in diff.splitlines():
        if any(tok in line for tok in HEADER_TOKENS):
            fixed.extend(split_glued_headers(line))
        else:
            fixed.append(line.rstrip())

    text = "\n".join(fixed).strip() + "\n"

    if not text.startswith("diff --git"):
        return ""

    return text


# --------------------------------------------------
# Diff extraction
# --------------------------------------------------

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
# Path normalization
# --------------------------------------------------

def normalize_file_path(path: str) -> str:
    return re.sub(r"^[ab]/", "", path)


def fix_diff_paths(diff: str) -> str:
    lines = []
    for line in diff.splitlines():
        if line.startswith("diff --git"):
            m = re.match(r"diff --git a/(.*) b/(.*)", line)
            if m:
                a = normalize_file_path(m.group(1))
                b = normalize_file_path(m.group(2))
                line = f"diff --git a/{a} b/{b}"
        elif line.startswith("--- "):
            path = normalize_file_path(line[4:])
            line = f"--- a/{path}"
        elif line.startswith("+++ "):
            path = normalize_file_path(line[4:])
            line = f"+++ b/{path}"
        lines.append(line)

    return "\n".join(lines) + "\n"


# --------------------------------------------------
# Patch application
# --------------------------------------------------

def extract_files_from_diff(diff: str) -> List[str]:
    files = []
    for line in diff.splitlines():
        if line.startswith("+++ b/"):
            files.append(line[6:])
    return files


def try_git_apply(diff: str, options: List[str] = None) -> bool:
    options = options or []

    with tempfile.NamedTemporaryFile("w+", delete=False) as tf:
        tf.write(diff)
        patch_path = tf.name

    try:
        code, _ = run(["git", "apply", "--check"] + options + [patch_path])
        if code != 0:
            return False

        code, _ = run(["git", "apply"] + options + [patch_path])
        return code == 0
    finally:
        os.unlink(patch_path)


# --------------------------------------------------
# Structural validation
# --------------------------------------------------

def looks_like_kotlin_code(content: str) -> bool:
    if not content:
        return False
    if "package " not in content:
        return False
    if not re.search(r"\b(class|object|interface|fun)\b", content):
        return False
    if content.count("{") < content.count("}"):
        return False
    return True


def is_structurally_corrupt(content: str) -> bool:
    return not looks_like_kotlin_code(content)


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

    # 🔒 Hard gate
    if retry_count <= 1 and is_full:
        logger.error("🚫 Full-file overwrite rejected on retry 1")
        return []

    diff = fix_diff_paths(diff)
    files = extract_files_from_diff(diff)

    if not files:
        return []

    # 1️⃣ Try normal git apply first
    for opts in ([], ["--ignore-whitespace"], ["--3way"]):
        if try_git_apply(diff, opts):
            logger.info(f"✅ Patch applied via git: {files}")
            return files

    # 2️⃣ Full-file overwrite fallback (retry ≥ 2)
    if retry_count >= 2 and is_full and len(files) == 1:
        path = files[0]
        content = extract_full_file_content(diff)

        if is_structurally_corrupt(content):
            logger.error("🚨 Structural corruption detected — refusing overwrite")
            return []

        write_file(path, content)
        logger.warning(f"⚠️ Full-file overwrite applied: {path}")
        return [path]

    logger.warning("⚠️ All git apply strategies failed")
    return []
