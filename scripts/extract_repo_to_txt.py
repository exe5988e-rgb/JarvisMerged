from pathlib import Path

# Output files
OUTPUT_COMPACT = "repo_source_dump.txt"
OUTPUT_FULL = "repo_source_dump_full.txt"

# ✅ WHITELIST folders (relative to repo root)
INCLUDE_DIRS = [
    "app",
    "modules",
    "gradle/wrapper",
    "scripts",
    ".github",
]

# ✅ Source extensions only
INCLUDE_EXTENSIONS = {
    ".kt", ".java", ".xml",
    ".gradle", ".kts",
    ".properties", ".json",
    ".yml", ".yaml",
    ".md", ".txt", ".pro",
    ".py", ".cpp", ".h"
}

# ❌ Always excluded
EXCLUDE_DIRS = {
    ".git", ".gradle",
    "build", "venv", "__pycache__",
    "node_modules"
}

EXCLUDE_FILES = {
    "gradle-wrapper.jar",
    ".keystore",
    ".zip"
}

# 🔥 Adaptive dump settings (compact mode)
MAX_FULL_FILE_SIZE = 1000  # bytes
HEAD_LINES = 80
TAIL_LINES = 30


def is_excluded(path: Path) -> bool:
    return (
        any(p in EXCLUDE_DIRS for p in path.parts)
        or any(path.name.endswith(f) for f in EXCLUDE_FILES)
    )


def generate_tree(root: Path) -> str:
    lines = ["."]
    for base_dir in INCLUDE_DIRS:
        base = root / base_dir
        if not base.exists():
            continue

        lines.append(f"├── {base_dir}")
        for path in sorted(base.rglob("*")):
            if is_excluded(path):
                continue

            depth = len(path.relative_to(base).parts)
            indent = "│   " * depth
            lines.append(f"{indent}├── {path.name}")

    return "\n".join(lines)


def dump_source_full(root: Path, out):
    for base_dir in INCLUDE_DIRS:
        base = root / base_dir
        if not base.exists():
            continue

        for path in sorted(base.rglob("*")):
            if (
                path.is_file()
                and not is_excluded(path)
                and path.suffix in INCLUDE_EXTENSIONS
            ):
                rel_path = path.relative_to(root)
                out.write(f"\n//===== FILE: {rel_path} =====\n")
                try:
                    out.write(path.read_text(encoding="utf-8"))
                except Exception:
                    out.write("[UNREADABLE FILE]")
                out.write("\n")


def dump_source_compact(root: Path, out):
    for base_dir in INCLUDE_DIRS:
        base = root / base_dir
        if not base.exists():
            continue

        for path in sorted(base.rglob("*")):
            if (
                path.is_file()
                and not is_excluded(path)
                and path.suffix in INCLUDE_EXTENSIONS
            ):
                rel_path = path.relative_to(root)
                out.write(f"\n//===== FILE: {rel_path} =====\n")

                try:
                    content = path.read_text(encoding="utf-8")
                    size = path.stat().st_size

                    if size <= MAX_FULL_FILE_SIZE:
                        out.write("// TYPE: FULL\n")
                        out.write(content)

                    else:
                        out.write("// TYPE: SNAPSHOT (LARGE FILE)\n")
                        lines = content.splitlines()

                        head = lines[:HEAD_LINES]
                        tail = lines[-TAIL_LINES:] if len(lines) > TAIL_LINES else []

                        out.write("\n".join(head))
                        out.write("\n\n// ... FILE TRUNCATED ...\n\n")
                        out.write("\n".join(tail))

                except Exception:
                    out.write("[UNREADABLE FILE]")

                out.write("\n")


def write_dump(root: Path, output_file: str, compact: bool):
    with open(output_file, "w", encoding="utf-8") as out:
        out.write("================================\n")
        out.write("FILE TREE (SELECTED FOLDERS)\n")
        out.write("================================\n\n")
        out.write(generate_tree(root))

        out.write("\n\n================================\n")
        out.write("SOURCE CODE\n")
        out.write("================================\n")

        if compact:
            dump_source_compact(root, out)
        else:
            dump_source_full(root, out)


def main():
    root = Path(".").resolve()

    # Write compact dump
    write_dump(root, OUTPUT_COMPACT, compact=True)
    print("✅ Compact repo dump generated")

    # Write full dump
    write_dump(root, OUTPUT_FULL, compact=False)
    print("✅ Full repo dump generated")


if __name__ == "__main__":
    main()
