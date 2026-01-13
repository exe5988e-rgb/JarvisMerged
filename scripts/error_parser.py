import os
import re
from scripts.logger import logger


def parse_build_errors(build_output: str) -> list[dict]:
    """
    Modern Gradle / Kotlin / Java error parser.
    Permissive by design. NEVER misses real errors.
    """
    errors = []

    patterns = [
        # Kotlin compiler (modern)
        re.compile(
            r"^[ew]:\s*(file://)?(.+?\.(kt|java)):(\d+):(\d+)?\s+(.*)",
            re.IGNORECASE,
        ),

        # Kotlin / Java classic
        re.compile(
            r"(.+?\.(kt|java)):(\d+):\s*(error|exception):?\s*(.+)",
            re.IGNORECASE,
        ),

        # XML resource errors
        re.compile(
            r"(.+?\.xml):(\d+):\s*(error):?\s*(.+)",
            re.IGNORECASE,
        ),

        # AAPT / Gradle errors without file
        re.compile(
            r"(AAPT|Execution failed|Could not resolve|Build failed).*",
            re.IGNORECASE,
        ),
    ]

    for raw in build_output.splitlines():
        line = raw.strip()
        if not line:
            continue

        # Skip warnings explicitly
        if line.lower().startswith("w:"):
            continue

        for pattern in patterns:
            m = pattern.search(line)
            if not m:
                continue

            groups = m.groups()

            file_path = None
            line_num = None
            message = line

            if len(groups) >= 6 and groups[1]:
                file_path = groups[1].replace("file://", "")
                try:
                    line_num = int(groups[3])
                except Exception:
                    line_num = None
                message = groups[-1]

            elif len(groups) >= 4 and groups[0] and os.path.exists(groups[0]):
                file_path = groups[0]
                try:
                    line_num = int(groups[2])
                except Exception:
                    line_num = None
                message = groups[-1]

            errors.append({
                "file": file_path,
                "line": line_num,
                "message": message.strip(),
            })

            logger.debug(f"Parsed error: {file_path}:{line_num} -> {message}")
            break

    # Deduplicate
    unique = []
    seen = set()
    for e in errors:
        key = (e["file"], e["line"], e["message"])
        if key not in seen:
            seen.add(key)
            unique.append(e)

    logger.info(f"📋 Parsed {len(unique)} build errors")
    return unique


def extract_code_snippet(file_path: str, error_line: int | None, context: int = 20):
    """
    Extract +/- 20 lines around error.
    """
    if not file_path or not error_line:
        return None
    if not os.path.exists(file_path):
        return None

    try:
        with open(file_path, "r", encoding="utf-8", errors="ignore") as f:
            lines = f.readlines()

        start = max(0, error_line - context - 1)
        end = min(len(lines), error_line + context)

        snippet = []
        for i in range(start, end):
            prefix = ">>> " if i == error_line - 1 else "    "
            snippet.append(f"{i+1:4d}{prefix}{lines[i].rstrip()}")

        return {
            "code": "\n".join(snippet),
            "start_line": start + 1,
            "end_line": end,
        }

    except Exception:
        return None
