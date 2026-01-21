import os
import re
import sys
import subprocess

# Match both // and <!-- --> headers, multiline-safe
FILE_HEADER = re.compile(
    r"^[ \t]*(?:\/\/|<!--)\s*={5,}\s*FILE:\s*(.+?)\s*={5,}(?:\s*-->)?\s*$",
    re.IGNORECASE | re.MULTILINE
)

def sanitize(path):
    path = path.strip().replace("\\", "/")
    if ".." in path or path.startswith("/") or re.match(r"^[A-Za-z]:", path):
        raise ValueError(f"Unsafe path: {path}")
    return path

def parse_ai_output(text: str) -> dict[str, str]:
    """
    Parse AI output and return a mapping of file_path -> content.
    If the same file appears multiple times, the LAST occurrence wins.
    """
    matches = list(FILE_HEADER.finditer(text))
    files: dict[str, str] = {}

    for i, match in enumerate(matches):
        file_path = sanitize(match.group(1))

        start = match.end()
        end = matches[i + 1].start() if i + 1 < len(matches) else len(text)
        code = text[start:end].strip("\n")

        if not code.strip():
            continue

        # ✅ Overwrite on duplicates (later definition wins)
        if file_path in files:
            print(f"🔁 Overwriting duplicate file: {file_path}")

        files[file_path] = code

    return files

def write_files(files):
    for path, content in files.items():
        dir_path = os.path.dirname(path)
        if dir_path:
            os.makedirs(dir_path, exist_ok=True)

        with open(path, "w", encoding="utf-8", newline="\n") as f:
            f.write(content.rstrip() + "\n")

        print(f"✅ Written: {path}")

def git(cmd):
    subprocess.run(cmd, check=True)

def setup_git_identity():
    try:
        name = subprocess.run(
            ["git", "config", "--get", "user.name"],
            capture_output=True, text=True
        ).stdout.strip()

        email = subprocess.run(
            ["git", "config", "--get", "user.email"],
            capture_output=True, text=True
        ).stdout.strip()

        if not name:
            git(["git", "config", "user.name", "AI Bot"])
        if not email:
            git(["git", "config", "user.email", "ai-bot@users.noreply.github.com"])
    except Exception as e:
        print(f"⚠️ Git identity setup failed: {e}")

def auto_commit_push(message="AI: generate project structure"):
    setup_git_identity()

    print("🔄 Git add")
    git(["git", "add", "."])

    print("📝 Git commit")
    try:
        git(["git", "commit", "-m", message])
    except subprocess.CalledProcessError:
        print("ℹ️ Nothing to commit")
        return

    print("🚀 Git push")
    git(["git", "push"])

if __name__ == "__main__":
    if len(sys.argv) != 2:
        print("Usage: python ai_code_to_tree_autopush.py ai_output.txt")
        sys.exit(1)

    with open(sys.argv[1], "r", encoding="utf-8") as f:
        text = f.read()

    files = parse_ai_output(text)

    print(f"📦 Detected {len(files)} files")
    for fpath in files:
        print(" -", fpath)

    if not files:
        print("⚠️ No files detected in AI output")
        sys.exit(1)

    write_files(files)
    auto_commit_push()
