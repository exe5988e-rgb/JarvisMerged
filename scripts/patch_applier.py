//===== FILE: scripts/patch_applier.py =====
import os
import re
import subprocess
import tempfile
from typing import List, Optional, Tuple
from pathlib import Path

try:
    from scripts.logger import logger
except ImportError:
    import logging
    logger = logging.getLogger("patch_applier")


# --------------------------------------------------
# Structural corruption detector
# --------------------------------------------------
def is_structurally_corrupt(content: str) -> bool:
    return (
        content.count("class ") > 3
        or ("override fun onCreate" in content and "class" not in content)
        or content.count("package ") > 1
    )


# --------------------------------------------------
# Helpers
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
# Diff extraction and cleaning
# --------------------------------------------------
def clean_diff_text(text: str) -> str:
    text = re.sub(r"```(?:diff|patch|unified)?\s*\n?", "", text)
    text = re.sub(r"```\s*$", "", text, flags=re.MULTILINE)
    lines = []
    for line in text.splitlines():
        if line.startswith(('diff ', '---', '+++', '@@', '+', '-', ' ', 'index ', 'new file', 'deleted file')):
            lines.append(line)
        else:
            lines.append(line.strip())
    return "\n".join(lines)


def extract_diff(text: str) -> str:
    text = clean_diff_text(text)
    lines = []
    in_diff = False
    for line in text.splitlines():
        if line.startswith("diff --git") or line.startswith("diff -"):
            in_diff = True
            lines.append(line)
        elif in_diff:
            if line and not line.startswith(('---', '+++', '@@', '+', '-', ' ', 'index ', 'new file', 'deleted file', 'diff ')):
                if any(word in line.lower() for word in ['this fix', 'the change', 'explanation', 'note:', 'this will']):
                    break
            lines.append(line)
    result = "\n".join(lines).strip()
    if result and not result.endswith("\n"):
        result += "\n"
    return result


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
            match = re.match(r"diff --git a/(.*) b/(.*)", line)
            if match:
                path_a = normalize_file_path(match.group(1), repo_root)
                path_b = normalize_file_path(match.group(2), repo_root)
                line = f"diff --git a/{path_a} b/{path_b}"
        elif line.startswith("--- a/") or line.startswith("--- "):
            path = line[6:] if line.startswith("--- a/") else line[4:]
            path = normalize_file_path(path, repo_root)
            line = f"--- a/{path}"
        elif line.startswith("+++ b/") or line.startswith("+++ "):
            path = line[6:] if line.startswith("+++ b/") else line[4:]
            path = normalize_file_path(path, repo_root)
            line = f"+++ b/{path}"
        lines.append(line)
    return "\n".join(lines) + "\n"


def extract_files_from_diff(diff: str) -> List[str]:
    files = []
    for line in diff.splitlines():
        if line.startswith("+++ b/"):
            files.append(line[6:])
        elif line.startswith("+++ ") and not line.startswith("+++ /dev/null"):
            path = line[4:]
            path = re.sub(r"^[ab]/", "", path)
            files.append(path)
    return files


# --------------------------------------------------
# Patch application strategies
# --------------------------------------------------
def try_git_apply(diff: str, options: List[str] = None) -> Tuple[bool, List[str]]:
    options = options or []
    with tempfile.NamedTemporaryFile("w+", suffix=".patch", delete=False) as tf:
        tf.write(diff)
        patch_file = tf.name
    try:
        check_cmd = ["git", "apply", "--check"] + options + [patch_file]
        code, output = run(check_cmd)
        if code != 0:
            logger.debug(f"git apply --check failed: {output}")
            return False, []
        apply_cmd = ["git", "apply"] + options + [patch_file]
        code, output = run(apply_cmd)
        if code != 0:
            logger.debug(f"git apply failed: {output}")
            return False, []
        files = extract_files_from_diff(diff)
        return True, files
    finally:
        os.unlink(patch_file)


def try_patch_command(diff: str, repo_root: str) -> Tuple[bool, List[str]]:
    with tempfile.NamedTemporaryFile("w+", suffix=".patch", delete=False) as tf:
        tf.write(diff)
        patch_file = tf.name
    try:
        cmd = ["patch", "-p1", "-l", "-F", "3", "--no-backup-if-mismatch", "-i", patch_file]
        code, output = run(cmd, cwd=repo_root)
        if code == 0 or "succeeded" in output.lower():
            files = extract_files_from_diff(diff)
            existing_files = [f for f in files if os.path.exists(os.path.join(repo_root, f))]
            if existing_files:
                return True, existing_files
        logger.debug(f"patch command failed: {output}")
        return False, []
    finally:
        os.unlink(patch_file)
        for f in extract_files_from_diff(diff):
            orig_file = os.path.join(repo_root, f + ".orig")
            if os.path.exists(orig_file):
                os.unlink(orig_file)


def apply_diff(diff: str) -> List[str]:
    if not diff or not diff.strip():
        return []
    repo_root = get_repo_root()
    diff = fix_diff_paths(diff, repo_root)
    # Try multiple strategies
    for opts in [[], ["--ignore-whitespace"], ["--3way"], ["--reject", "--ignore-whitespace"]]:
        success, files = try_git_apply(diff, opts)
        if success and files:
            return files
    success, files = try_patch_command(diff, repo_root)
    if success and files:
        return files
    return []


# --------------------------------------------------
# Code block extraction
# --------------------------------------------------
def extract_code_blocks(text: str) -> List[Tuple[str, str]]:
    pattern = r"```(\w*)\s*\n(.*?)```"
    return [(lang.lower() if lang else "", code.strip()) for lang, code in re.findall(pattern, text, re.DOTALL) if code.strip()]


def extract_code(text: str) -> str:
    blocks = extract_code_blocks(text)
    for lang, code in blocks:
        if lang in ("kotlin", "kt", "java", "groovy", "gradle", "kts"):
            return code
    for lang, code in blocks:
        if looks_like_code(code):
            return code
    match = re.search(r"(package\s+[\w.]+.*)", text, re.DOTALL)
    if match:
        return match.group(1).strip()
    return text.strip()


def looks_like_code(content: str) -> bool:
    indicators = [
        "class ", "fun ", "object ", "interface ",
        "public class", "private class", "void ",
        "dependencies {", "plugins {", "android {",
        "import ", "package "
    ]
    return any(ind in content for ind in indicators) and len(content.splitlines()) > 5


# --------------------------------------------------
# Target file resolution
# --------------------------------------------------
def extract_target_file_from_response(text: str) -> Optional[str]:
    patterns = [
        r"(?:file|path):\s*[`'\"]?([^\s`'\"]+\.(kt|java|xml|gradle|kts))[`'\"]?",
        r"(?:modify|update|change|fix)\s+[`'\"]?([^\s`'\"]+\.(kt|java|xml|gradle|kts))[`'\"]?",
        r"^//\s*([^\s]+\.(kt|java|xml|gradle|kts))\s*$",
        r"[`'\"]([^\s`'\"]+\.(kt|java|xml|gradle|kts))[`'\"]",
    ]
    for pattern in patterns:
        match = re.search(pattern, text, re.IGNORECASE | re.MULTILINE)
        if match:
            return match.group(1)
    return None


def find_file_in_repo(filename: str, repo_root: str) -> Optional[str]:
    full_path = os.path.join(repo_root, filename)
    if os.path.isfile(full_path):
        return filename
    basename = os.path.basename(filename)
    for root, dirs, files in os.walk(repo_root):
        dirs[:] = [d for d in dirs if not d.startswith('.') and d not in ('build', 'node_modules', '__pycache__')]
        if basename in files:
            return os.path.relpath(os.path.join(root, basename), repo_root)
    return None


# --------------------------------------------------
# Main patch application
# --------------------------------------------------
def apply_patch(ai_response: str, build_log: str) -> List[str]:
    repo_root = get_repo_root()

    # 1️⃣ Try diff
    diff = extract_diff(ai_response)
    if diff:
        logger.info("📝 Found diff, applying...")
        files = apply_diff(diff)
        if files:
            return files
        logger.warning("⚠️ Diff failed, falling back to code overwrite...")

    # 2️⃣ Extract code
    content = extract_code(ai_response)
    if not looks_like_code(content):
        logger.warning("⚠️ No valid code found in response")
        return []

    # 3️⃣ Robust target file detection
    target_candidates = []

    # From response
    tf = extract_target_file_from_response(ai_response)
    if tf:
        target_candidates.append(tf)

    # From build log
    target_candidates.extend(re.findall(r"File:\s*(\S+)", build_log))

    # From diff headers
    for line in diff.splitlines():
        if line.startswith("+++ b/"):
            target_candidates.append(line[6:])

    # Normalize and pick first existing file
    target = None
    for t in target_candidates:
        t = normalize_file_path(t, repo_root)
        found = find_file_in_repo(t, repo_root)
        if found:
            target = found
            break

    if not target and target_candidates:
        # Create new file if all else fails
        target = normalize_file_path(target_candidates[0], repo_root)
        logger.info(f"📝 Creating new file: {target}")
        full_path = os.path.join(repo_root, target)
        write_file(full_path, "")

    if not target:
        logger.warning("⚠️ Could not determine target file")
        return []

    full_path = os.path.join(repo_root, target)
    if is_structurally_corrupt(content):
        logger.warning("⚠️ Extracted code appears structurally corrupt")
        return []

    logger.info(f"📝 Writing to file: {target}")
    write_file(full_path, content)
    return [target]
