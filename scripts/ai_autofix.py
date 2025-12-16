#!/usr/bin/env python3
import os
import subprocess
import json
import requests
import sys
import re

# ================= CONFIG =================
BUILD_CMD = "./gradlew build"

GEMINI_API_KEY = os.getenv("GEMINI_API_KEY")
if not GEMINI_API_KEY:
    print("❌ Error: GEMINI_API_KEY environment variable is not set.")
    sys.exit(1)

GEMINI_URL = (
    "https://generativelanguage.googleapis.com/v1beta/models/"
    "gemini-pro:generateContent"
)

# ================ HELPERS =================
def run(cmd):
    p = subprocess.run(cmd, shell=True, text=True, capture_output=True)
    return p.stdout + p.stderr, p.returncode


def ask_gemini(prompt: str) -> str:
    payload = {
        "contents": [
            {
                "parts": [
                    {"text": prompt}
                ]
            }
        ]
    }

    r = requests.post(
        f"{GEMINI_URL}?key={GEMINI_API_KEY}",
        headers={"Content-Type": "application/json"},
        json=payload,
        timeout=60,
    )
    r.raise_for_status()
    return r.json()["candidates"][0]["content"]["parts"][0]["text"]


def extract_first_kotlin_error(logs: str):
    """
    Returns (file, line, message) or None
    """
    for line in logs.splitlines():
        m = re.search(r"(.+\.kt):(\d+):(.+)", line)
        if m:
            return m.group(1), int(m.group(2)), m.group(3).strip()
    return None


# ================= MAIN =================
if __name__ == "__main__":
    print("🤖 Gemini Kotlin AutoFix started")

    print("\n🔨 Running Gradle build...")
    logs, code = run(BUILD_CMD)
    print(logs)
    print("Exit code:", code)

    if code == 0:
        print("✅ Build successful. Nothing to fix.")
        sys.exit(0)

    kt_error = extract_first_kotlin_error(logs)

    if not kt_error:
        print("❌ Build failed, but no Kotlin error detected.")
        sys.exit(2)

    file_path, line_no, message = kt_error

    print("\n🧨 Kotlin compilation error detected")
    print(f"📂 File : {file_path}")
    print(f"📍 Line : {line_no}")
    print(f"💬 Error: {message}")

    # === Load source context ===
    context = ""
    try:
        with open(file_path, "r") as f:
            lines = f.readlines()
        start = max(0, line_no - 6)
        end = min(len(lines), line_no + 5)
        for i in range(start, end):
            prefix = ">" if i + 1 == line_no else " "
            context += f"{prefix} {i+1}: {lines[i]}"
    except Exception as e:
        context = f"(Failed to read source file: {e})"

    prompt = f"""
You are a senior Android/Kotlin engineer.

Fix ONLY the Kotlin compilation error below.

Rules:
- Modify ONLY the affected file
- Output ONLY a unified git diff
- NO explanations
- If unsure, output NO_FIX_POSSIBLE

File: {file_path}
Line: {line_no}

Compiler error:
{message}

Build log:
{logs}

Source context:
{context}
"""

    print("\n📡 Sending error to Gemini...\n")

    try:
        diff = ask_gemini(prompt)
        print("🧩 Gemini response:\n")
        print(diff)
    except Exception as e:
        print("❌ Gemini request failed:", e)
        sys.exit(3)
