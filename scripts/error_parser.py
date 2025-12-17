import re
import os
from scripts.logger import logger

def parse_build_errors(build_output: str) -> list[dict]:
    """
    Parse build output to extract error locations.
    Returns list of {file, line, message} dicts.
    """
    errors = []
    
    # Common Android/Gradle error patterns
    patterns = [
        # Kotlin/Java: e: /path/File.kt: (line, col): error message
        r"e:\s*(.+?\.(?:kt|java)):\s*\((\d+),\s*\d+\):\s*(.+)",
        # Standard: /path/File.java:123: error: message
        r"(.+?\.(?:kt|java|xml)):(\d+):\s*error:\s*(.+)",
        # AAPT errors
        r"(.+?\.xml):(\d+):\s*(.+error.+)",
    ]
    
    for line in build_output.splitlines():
        for pattern in patterns:
            match = re.search(pattern, line, re.IGNORECASE)
            if match:
                file_path = match.group(1).strip()
                line_num = int(match.group(2))
                message = match.group(3).strip()
                
                if os.path.exists(file_path):
                    errors.append({
                        "file": file_path,
                        "line": line_num,
                        "message": message
                    })
                    logger.debug(f"Found error: {file_path}:{line_num}")
                break
    
    # Deduplicate by file+line
    seen = set()
    unique_errors = []
    for e in errors:
        key = (e["file"], e["line"])
        if key not in seen:
            seen.add(key)
            unique_errors.append(e)
    
    logger.info(f"📋 Parsed {len(unique_errors)} unique errors")
    return unique_errors


def extract_code_snippet(file_path: str, error_line: int, context_lines: int = 20) -> dict | None:
    """
    Extract code snippet around the error line (±context_lines).
    Returns {code, start_line, end_line} or None.
    """
    try:
        with open(file_path, "r", encoding="utf-8") as f:
            lines = f.readlines()
        
        total_lines = len(lines)
        start_line = max(1, error_line - context_lines)
        end_line = min(total_lines, error_line + context_lines)
        
        # Extract lines with line numbers
        snippet_lines = []
        for i in range(start_line - 1, end_line):
            line_num = i + 1
            marker = " >>> " if line_num == error_line else "     "
            snippet_lines.append(f"{line_num:4d}{marker}{lines[i].rstrip()}")
        
        return {
            "code": "\n".join(snippet_lines),
            "start_line": start_line,
            "end_line": end_line,
            "raw_lines": lines[start_line-1:end_line]
        }
    
    except Exception as e:
        logger.warning(f"⚠️ Could not read {file_path}: {e}")
        return None
