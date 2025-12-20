import os
import re
import sys
import subprocess

FILE_HEADER = re.compile(
    r"(?:^|\n)(?:File:|\*\*File:|###)\s*(.+)", re.IGNORECASE
)

CODE_BLOCK = re.compile(r"```[\w+-]*\n(.*?)```", re.DOTALL)

def sanitize(path):
    path = path.strip().replace("\\", "/")
    if ".." in path or path.startswith("/"):
        raise ValueError(f"Unsafe path: {path}")
    return path

def parse_ai_output(text):
    matches = list(FILE_HEADER.finditer(text))
    files = {}

    for i, match in enumerate(matches):
        file_path = sanitize(match.group(1))
        start = match.end()
        end = matches[i + 1].start() if i + 1 < len(matches) else len(text)
        chunk = text[start:end]

        code = CODE_BLOCK.findall(chunk)
        content = "\n\n".join(code).strip()

        if content:
            files[file_path] = content

    return files

def write_files(files):
    for path, content in files.items():
        os.makedirs(os.path.dirname(path), exist_ok=True)
        with open(path, "w", encoding="utf-8") as f:
            f.write(content)
        print(f"✅ Written: {path}")

def git(cmd):
    subprocess.run(cmd, check=True)

def setup_git_identity():
    """Set git name/email if not already configured."""
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
            git(["git", "config", "user.name", "Amir Shams"])
        if not email:
            git(["git", "config", "user.email", "amir@example.com"])
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
    write_files(files)
    auto_commit_push()
