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
# Diff sanitization (HARDENED)
# --------------------------------------------------

HEADER_TOKENS = ("diff --git ", "index ", "--- ", "+++ ")


def split_glued_headers(line: str) -> List[str]:
    """
    Split lines where multiple diff headers are glued together.
    Preserves order and content.
    """
    parts = []
    rest = line

    while True:
        indices = [(rest.find(tok), tok) for tok in HEADER_TOKENS if rest.find(tok) > 0]
        if not indices:
            parts.append(rest.rstrip())
            break

        idx, tok = min(indices, key=lambda x: x[0])
        parts.append(rest[:idx].rstrip())
        rest = rest[idx:]

    return [p for p in parts if p]


def is_valid_unified_diff(text: str) -> bool:
    if not text.startswith("diff --git"):
        return False

    seen_file = False
    for line in text.splitlines():
        if line.startswith("diff --git"):
            seen_file = True
        elif line.startswith(("@@", "--- ", "+++ ")) and not seen_file:
            return False

    return seen_file


def sanitize_unified_diff(diff: str) -> str:
    """
    Repair common AI-generated unified diff corruption so git apply won't fail.
    """
    if not diff:
        return ""

    # Normalize line endings
    diff = diff.replace("\r\n", "\n").replace("\r", "\n")

    # Remove zero-width / NBSP characters
    diff = (
        diff.replace("\u00a0", " ")
            .replace("\u200b", "")
            .replace("\ufeff", "")
    )

    fixed_lines: List[str] = []

    for raw_line in diff.splitlines():
        if any(tok in raw_line for tok in HEADER_TOKENS):
            fixed_lines.extend(split_glued_headers(raw_line))
        else:
            fixed_lines.append(raw_line.rstrip())

    text = "\n".join(fixed_lines)

    # Trim only leading/trailing blank lines
    text = re.sub(r"^\n+", "", text)
    text = re.sub(r"\n+$", "\n", text)

    if not is_valid_unified_diff(text):
        return ""

    if not text.endswith("\n"):
        text += "\n"

    return text


# --------------------------------------------------
# Diff extraction
# --------------------------------------------------

def clean_diff_text(text: str) -> str:
    # Remove markdown fences only
    text = re.sub(r"```(?:diff|patch|unified)?\s*\n?", "", text)
    text = re.sub(r"```\s*$", "", text, flags=re.MULTILINE)
    return text


def extract_diff(text: str) -> str:
    text = clean_diff_text(text)

    lines = []
    in_diff = False

    for line in text.splitlines():
        if line.startswith("diff --git"):
            in_diff = True

        if in_diff:
            if line.startswith("```"):
                break
            lines.append(line)

    result = "\n".join(lines)
    result = re.sub(r"^\n+", "", result)
    result = re.sub(r"\n+$", "\n", result)

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
                if part in ("app", "modules", "src"):
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
            raw = re.sub(r"^[ab]/", "", line[4:])
            path = normalize_file_path(raw, repo_root)
            line = f"--- a/{path}"

        elif line.startswith("+++ "):
            raw = re.sub(r"^[ab]/", "", line[4:])
            path = normalize_file_path(raw, repo_root)
            line = f"+++ b/{path}"

        lines.append(line)

    return "\n".join(lines) + "\n"


# --------------------------------------------------
# Patch application
# --------------------------------------------------

def extract_files_from_diff(diff: str) -> List[str]:
    files = []
    seen = set()

    for line in diff.splitlines():
        if line.startswith("+++ b/"):
            path = line[6:]
            if path in seen:
                raise ValueError(f"Duplicate diff for file: {path}")
            seen.add(path)
            files.append(path)

    return files


def try_git_apply(diff: str, options: List[str] = None) -> Tuple[bool, List[str]]:
    options = options or []

    with tempfile.NamedTemporaryFile("w+", suffix=".patch", delete=False) as tf:
        tf.write(diff)
        patch_file = tf.name

    try:
        code, _ = run(["git", "apply", "--check"] + options + [patch_file])
        if code != 0:
            return False, []

        code, _ = run(["git", "apply"] + options + [patch_file])
        if code != 0:
            return False, []

        code, status = run(["git", "status", "--porcelain"])
        if ".rej" in status:
            logger.warning("⚠️ Reject files detected; reverting patch")
            run(["git", "reset", "--hard"])
            return False, []

        return True, extract_files_from_diff(diff)

    finally:
        os.unlink(patch_file)


def apply_diff(diff: str) -> List[str]:
    repo_root = get_repo_root()

    diff = sanitize_unified_diff(diff)
    if not diff:
        logger.warning("⚠️ Diff unrecoverable after sanitization")
        return []

    diff = fix_diff_paths(diff, repo_root)

    try:
        files = extract_files_from_diff(diff)
    except ValueError as e:
        logger.warning(str(e))
        return []

    ALLOWED_EXTENSIONS = {".kt", ".kts", ".java"}
    for f in files:
        if os.path.splitext(f)[1] not in ALLOWED_EXTENSIONS:
            logger.warning(f"⚠️ Disallowed file type: {f}")
            return []
        if not os.path.exists(f):
            logger.warning(f"⚠️ File does not exist: {f}")
            return []

    strategies = [
        [],
        ["--ignore-whitespace"],
        ["--3way"],
        ["--reject", "--ignore-whitespace"],
    ]

    for opts in strategies:
        ok, applied = try_git_apply(diff, opts)
        if ok and applied:
            logger.info(f"✅ Patch applied: {applied}")
            return applied

    logger.warning("⚠️ All git apply strategies failed")
    return []


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
# --------------------------------------------------
# Structural validation
# --------------------------------------------------

def is_structurally_corrupt(repo_root: Optional[str] = None) -> bool:
    """
    Returns True if repository structure is invalid or unsafe to patch.
    """
    repo_root = repo_root or get_repo_root()

    required_paths = [
        "main.py",
        "scripts",
    ]

    for path in required_paths:
        if not os.path.exists(os.path.join(repo_root, path)):
            logger.warning(f"❌ Missing required path: {path}")
            return True

    # Git sanity check
    code, _ = run(["git", "rev-parse", "--is-inside-work-tree"], cwd=repo_root)
    if code != 0:
        logger.warning("❌ Not inside a git repository")
        return True

    return False
