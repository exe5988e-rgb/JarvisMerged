import re
import os
from scripts.logger import logger


def _safe_int(value):
    try:
        return int(value)
    except Exception:
        return None


def parse_build_errors(build_output: str) -> list[dict]:
    """
    Flexible Gradle/Kotlin/AAPT error parser.
    Returns list of {file, line, message}.
    Never crashes.
    """
    errors = []

    # Modern + classic patterns
    patterns = [
        # Kotlin/Java modern: e.g., e: file://app/src/Main.kt:23: error: ...
        re.compile(r"(?:e:\s*)?(file://)?(.+?\.(kt|java)):(\d+):\s*(error:?.+)", re.IGNORECASE),
        # Classic Kotlin/Java
        re.compile(r"(.+?\.(kt|java)):(\d+):\s*error:\s*(.+)", re.IGNORECASE),
        # XML errors with line
        re.compile(r"(.+?\.xml):(\d+):\s*error:\s*(.+)", re.IGNORECASE),
        # AAPT resource errors (no line)
        re.compile(r"AAPT:\s*error:\s*(.+)", re.IGNORECASE),
        # Generic fallback: any "error:" line
        re.compile(r"(.+?):(\d+):\s*error:\s*(.+)", re.IGNORECASE),
    ]

    for raw_line in build_output.splitlines():
        line = raw_line.strip()
        if "warning:" in line.lower():
            continue  # ignore warnings

        matched = False
        for pattern in patterns:
            m = pattern.search(line)
            if not m:
                continue

            groups = m.groups()
            file_path = None
            line_num = None
            message = None

            # Kotlin/Java
            if len(groups) >= 4 and groups[1]:
                file_path = groups[1].replace("file://", "").strip()
                line_num = _safe_int(groups[3])
                message = groups[-1]

            # XML
            elif len(groups) == 3 and groups[0].endswith(".xml"):
                file_path = groups[0].strip()
                line_num = _safe_int(groups[1])
                message = groups[2]

            # Generic fallback (no file/line)
            else:
                message = groups[-1]

            errors.append({
                "file": file_path,
                "line": line_num,
                "message": message.strip()
            })
            logger.debug(f"Parsed error: {file_path}:{line_num} → {message}")
            matched = True
            break

        if not matched and "error:" in line.lower():
            # fallback generic error line
            errors.append({
                "file": None,
                "line": None,
                "message": line
            })

    # Deduplicate
    seen = set()
    unique = []
    for e in errors:
        key = (e["file"], e["line"], e["message"])
        if key not in seen:
            seen.add(key)
            unique.append(e)

    logger.info(f"📋 Parsed {len(unique)} build errors")
    return unique


def extract_code_snippet(file_path: str, error_line: int | None, context_lines: int = 15) -> dict | None:
    """
    Extracts code snippet around an error line.
    Never throws.
    """
    try:
        if not file_path or not error_line or not os.path.exists(file_path):
            return None

        with open(file_path, "r", encoding="utf-8", errors="ignore") as f:
            lines = f.readlines()

        total = len(lines)
        if error_line < 1 or error_line > total:
            return None

        start = max(1, error_line - context_lines)
        end = min(total, error_line + context_lines)

        snippet = []
        for i in range(start - 1, end):
            ln = i + 1
            prefix = ">>> " if ln == error_line else "    "
            snippet.append(f"{ln:4d}{prefix}{lines[i].rstrip()}")

        return {
            "code": "\n".join(snippet),
            "start_line": start,
            "end_line": end
        }

    except Exception as e:
        logger.debug(f"Snippet skipped for {file_path}:{error_line} → {e}")
        return None
