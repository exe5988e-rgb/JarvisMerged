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
    if code == 0:
        return out.strip()
    return os.getcwd()

# --------------------------------------------------
# Diff sanitization (CRITICAL FIX)
# --------------------------------------------------

def sanitize_unified_diff(diff: str) -> str:
    """
    Repair common AI-generated unified diff corruption so git apply won't fail.
    """
    if not diff:
        return ""

    # Normalize line endings
    diff = diff.replace("\r\n", "\n").replace("\r", "\n")

    # Remove zero-width / non-breaking spaces
    diff = diff.replace("\u00a0", " ").replace("\u200b", "")

    lines = diff.splitlines()
    fixed = []

    for line in lines:
        # Fix glued index lines:
        # file.ktindex 123..456 100644
        if "index " in line and not line.startswith("index "):
            before, after = line.split("index ", 1)
            if before.strip():
                fixed.append(before.rstrip())
            fixed.append("index " + after.strip())
            continue

        # Fix "--- a/file+++ b/file"
        if line.startswith("--- ") and "+++" in line:
            a, b = line.split("+++", 1)
            fixed.append(a.rstrip())
            fixed.append("+++ " + b.strip())
            continue

        fixed.append(line.rstrip())

    text = "\n".join(fixed).strip()

    # Validate minimal diff structure
    required = ["diff --git", "--- ", "+++ ", "@@"]
    if not all(k in text for k in required):
        return ""

    if not text.endswith("\n"):
        text += "\n"

    return text

# --------------------------------------------------
# Diff extraction & cleaning
# --------------------------------------------------

def clean_diff_text(text: str) -> str:
    text = re.sub(r"```(?:diff|patch|unified)?\s*\n?", "", text)
    text = re.sub(r"```\s*$", "", text, flags=re.MULTILINE)
    lines = []
    for line in text.splitlines():
        if line.startswith(
            ("diff ", "---", "+++", "@@", "+", "-", " ", "index ", "new file", "deleted file")
        ):
            lines.append(line)
        else:
            lines.append(line.strip())
    return "\n".join(lines)


def extract_diff(text: str) -> str:
    text = clean_diff_text(text)
    lines = []
    in_diff = False

    for line in text.splitlines():
        if line.startswith("diff --git"):
            in_diff = True
            lines.append(line)
        elif in_diff:
            if line and not line.startswith(
                ("---", "+++", "@@", "+", "-", " ", "index ", "new file", "deleted file", "diff ")
            ):
                break
            lines.append(line)

    result = "\n".join(lines).strip()
    if result and not result.endswith("\n"):
        result += "\n"
    return result

# --------------------------------------------------
# Path normalization
# --------------------------------------------------

def normalize_file_path(path: str, repo_root: str) -> str:
    path = re.sub(r"^[ab]/", "", path)

    if path.startswith("/"):
        if path.startswith(repo_root):
            path = path[len(repo_root):].lstrip("/")
        else:
            parts = path.split("/")
            for i, part in enumerate(parts):
                if part in ("app", "modules", "src", "main"):
                    path = "/".join(parts[i:])
                    break

    path = re.sub(r"^/workspaces/[^/]+/", "", path)
    path = re.sub(r"^workspaces/[^/]+/", "", path)
    return path


def fix_diff_paths(diff: str, repo_root: str) -> str:
    lines = []
    for line in diff.splitlines():
        if line.startswith("diff --git"):
            m = re.match(r"diff --git a/(.*) b/(.*)", line)
            if m:
                a = normalize_file_path(m.group(1), repo_root)
                b = normalize_file_path(m.group(2), repo_root)
                line = f"diff --git a/{a} b/{b}"
        elif line.startswith("--- "):
            path = normalize_file_path(line[4:], repo_root)
            line = f"--- a/{path}"
        elif line.startswith("+++ "):
            path = normalize_file_path(line[4:], repo_root)
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


def try_git_apply(diff: str, options: List[str] = None) -> Tuple[bool, List[str]]:
    options = options or []

    with tempfile.NamedTemporaryFile("w+", suffix=".patch", delete=False) as tf:
        tf.write(diff)
        patch_file = tf.name

    try:
        code, out = run(["git", "apply", "--check"] + options + [patch_file])
        if code != 0:
            return False, []

        code, out = run(["git", "apply"] + options + [patch_file])
        if code != 0:
            return False, []

        return True, extract_files_from_diff(diff)
    finally:
        os.unlink(patch_file)


def apply_diff(diff: str) -> List[str]:
    repo_root = get_repo_root()

    # 🔧 SANITIZE FIRST (THIS FIXES YOUR ERROR)
    diff = sanitize_unified_diff(diff)
    if not diff:
        logger.warning("⚠️ Diff unrecoverable after sanitization")
        return []

    diff = fix_diff_paths(diff, repo_root)

    strategies = [
        [],
        ["--ignore-whitespace"],
        ["--3way"],
        ["--reject", "--ignore-whitespace"],
    ]

    for opts in strategies:
        ok, files = try_git_apply(diff, opts)
        if ok and files:
            logger.info(f"✅ Patch applied: {files}")
            return files

    logger.warning("⚠️ All git apply strategies failed")
    return []

# --------------------------------------------------
# Structural validation
# --------------------------------------------------

def looks_like_kotlin_code(content: str) -> bool:
    return (
        len(content.splitlines()) > 5
        and any(k in content for k in ("class ", "object ", "fun ", "package "))
    )


def is_structurally_corrupt(content: str) -> bool:
    return not looks_like_kotlin_code(content)

# --------------------------------------------------
# Main entry
# --------------------------------------------------

def apply_patch(ai_response: str, build_log: str) -> List[str]:
    diff = extract_diff(ai_response)
    if diff:
        logger.info("📝 Found diff in response, attempting to apply...")
        files = apply_diff(diff)
        if files:
            return files

    logger.warning("⚠️ Patch application failed")
    return []
