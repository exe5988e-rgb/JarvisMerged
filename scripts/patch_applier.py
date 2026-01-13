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

# -----------------------------
# Utilities
# -----------------------------
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

# -----------------------------
# Diff extraction & cleaning
# -----------------------------
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

# -----------------------------
# Patch application strategies
# -----------------------------
def try_git_apply(diff: str, options: List[str] = None) -> Tuple[bool, List[str]]:
    options = options or []
    with tempfile.NamedTemporaryFile("w+", suffix=".patch", delete=False) as tf:
        tf.write(diff)
        patch_file = tf.name
    try:
        code, output = run(["git", "apply", "--check"] + options + [patch_file])
        if code != 0:
            logger.debug(f"git apply --check failed: {output}")
            return False, []
        code, output = run(["git", "apply"] + options + [patch_file])
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
            files = [f for f in extract_files_from_diff(diff) if os.path.exists(os.path.join(repo_root, f))]
            if files:
                return True, files
        logger.debug(f"patch command failed: {output}")
        return False, []
    finally:
        os.unlink(patch_file)
        # Clean up .orig files
        for f in extract_files_from_diff(diff):
            orig_file = os.path.join(repo_root, f + ".orig")
            if os.path.exists(orig_file):
                os.unlink(orig_file)

def apply_diff(diff: str) -> List[str]:
    if not diff.strip():
        return []
    repo_root = get_repo_root()
    diff = fix_diff_paths(diff, repo_root)

    strategies = [
        ("strict", []),
        ("ignore-whitespace", ["--ignore-whitespace"]),
        ("3way", ["--3way"]),
        ("reject", ["--reject", "--ignore-whitespace"])
    ]

    for name, opts in strategies:
        logger.debug(f"Trying git apply strategy: {name}")
        success, files = try_git_apply(diff, opts)
        if success and files:
            logger.info(f"✅ Patch applied ({name}): {files}")
            return files

    # Fuzzy patch fallback
    logger.debug("Trying patch command with fuzzy matching...")
    success, files = try_patch_command(diff, repo_root)
    if success and files:
        logger.info(f"✅ Patch applied (fuzzy): {files}")
        return files

    logger.warning("⚠️ All patch strategies failed")
    return []

# -----------------------------
# Code extraction & validation
# -----------------------------
def looks_like_kotlin_code(content: str) -> bool:
    content = content.strip()
    if not content.startswith("package ") and "class " not in content and "object " not in content:
        return False
    indicators = ["class ", "object ", "fun ", "interface ", "import ", "package "]
    return any(ind in content for ind in indicators) and len(content.splitlines()) > 5

def extract_code_blocks(text: str) -> List[Tuple[str, str]]:
    blocks = []
    pattern = r"```(\w*)\s*\n(.*?)```"
    matches = re.findall(pattern, text, re.DOTALL)
    for lang, code in matches:
        lang = lang.lower() if lang else ""
        code = code.strip()
        if code:
            blocks.append((lang, code))
    return blocks

def extract_code(text: str) -> str:
    blocks = extract_code_blocks(text)
    for lang, code in blocks:
        if lang in ("kotlin", "kt", "java", "groovy", "gradle", "kts"):
            return code
    for lang, code in blocks:
        if looks_like_kotlin_code(code):
            return code
    match = re.search(r"(package\s+[\w.]+.*)", text, re.DOTALL)
    if match:
        return match.group(1).strip()
    return text.strip()

# -----------------------------
# Target file detection
# -----------------------------
def extract_target_file_from_response(text: str) -> Optional[str]:
    patterns = [
        r"(?:file|path):\s*[`'\"]?([^\s`'\"]+\.(kt|java|xml|gradle|kts))[`'\"]?",
        r"(?:modify|update|change|fix)\s+[`'\"]?([^\s`'\"]+\.(kt|java|xml|gradle|kts))[`'\"]?"
    ]
    for pattern in patterns:
        match = re.search(pattern, text, re.IGNORECASE)
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

# -----------------------------
# Main patch application
# -----------------------------
def apply_patch(ai_response: str, build_log: str) -> List[str]:
    repo_root = get_repo_root()

    # Strategy 1: Try diff application
    diff = extract_diff(ai_response)
    if diff:
        logger.info("📝 Found diff in response, attempting to apply...")
        files = apply_diff(diff)
        if files:
            return files
        logger.warning("⚠️ Diff application failed, trying code overwrite fallback...")

    # Strategy 2: Extract code and overwrite file
    code_content = extract_code(ai_response)
    if not looks_like_kotlin_code(code_content):
        logger.warning("⚠️ Extracted code is not valid Kotlin; aborting overwrite.")
        return []

    target_file = extract_target_file_from_response(ai_response)
    if not target_file:
        match = re.search(r"File:\s*(\S+)", build_log)
        if match:
            target_file = match.group(1)

    if not target_file:
        logger.warning("⚠️ Could not determine target file for code overwrite")
        return []

    target_file = normalize_file_path(target_file, repo_root)
    found_file = find_file_in_repo(target_file, repo_root)
    if not found_file:
        logger.warning(f"⚠️ Target file not found in repo: {target_file}")
        return []

    full_path = os.path.join(repo_root, found_file)
    write_file(full_path, code_content)
    logger.info(f"📝 Overwrote file with validated code: {found_file}")
    return [found_file]
