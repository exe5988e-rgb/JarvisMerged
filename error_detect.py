#!/usr/bin/env python3
import os
import subprocess
import time
import json
import requests
import sys

# ================= CONFIG =================
MAX_RETRIES = 3
PATCH_BRANCH = "ai-autofix"
BUILD_CMD = "./gradlew build"

GEMINI_API_KEY = os.getenv("GEMINI_API_KEY")
if not GEMINI_API_KEY:
    print("❌ Error: GEMINI_API_KEY environment variable is not set.")
    sys.exit(1)

GEMINI_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-pro:generateContent"

# ================ HELPERS =================
def run(cmd):
    p = subprocess.run(cmd, shell=True, text=True, capture_output=True)
    return p.stdout + p.stderr, p.returncode

def ask_gemini(prompt):
    payload = {
        "model": "gemini-3-pro",
        "messages": [
            {"role": "user", "content": prompt}
        ]
    }

    r = requests.post(
        f"{GEMINI_URL}?key={GEMINI_API_KEY}",
        headers={"Content-Type": "application/json"},
        data=json.dumps(payload),
        timeout=60
    )
    r.raise_for_status()
    return r.json()["choices"][0]["message"]["content"]

# ================ MAIN =================
if __name__ == "__main__":
    print("Gemini autofix script loaded correctly.")

    print("Running Gradle build...")
    logs, code = run(BUILD_CMD)
    print(logs)
    print("Exit code:", code)

    # === DETECT KOTLIN / JAVA COMPILE ERRORS ===
    if (
        "e: " in logs
        or "Compilation error" in logs
        or "compileDebugKotlin" in logs
        or ".kt:" in logs
    ):
        print("🧨 Kotlin/Java compilation error detected")

        error_lines = []
        for line in logs.splitlines():
            if ".kt:" in line or "e: " in line:
                error_lines.append(line)

        print("\n📄 Extracted error context:")
        for l in error_lines[:10]:
            print(l)

        print("\n🛑 Proceeding with first Kotlin error for AI suggestion.")
print("\n📡 Sending Kotlin error to Gemini...")

prompt = f"""

You are a senior Kotlin engineer.

Fix ONLY the following Kotlin compilation errors.

Return ONLY a unified git diff.

Do NOT explain anything.



Build log:

{logs}

"""



try:

    diff = ask_gemini(prompt)

    print("\n🧩 Gemini output:\n")

    print(diff)

except Exception as e:

    print("❌ Gemini call failed:", e)

    sys.exit(1)

    sys.exit(2)

    # === SHOW SOURCE CONTEXT FOR KOTLIN ERRORS ===
    import re

    kt_error = None
    for line in logs.splitlines():
        # Matches: path/file.kt:LINE:
        m = re.search(r'(.+\.kt):(\d+):', line)
        if m:
            kt_error = (m.group(1), int(m.group(2)))
            break

    if kt_error:
        file_path, line_no = kt_error
        print(f"\n📂 Kotlin file: {file_path}")
        print(f"📍 Error line: {line_no}")

        try:
            with open(file_path, "r") as f:
                lines = f.readlines()

            start = max(0, line_no - 6)
            end = min(len(lines), line_no + 5)

            print("\n📄 Source context:")
            for i in range(start, end):
                prefix = ">" if i + 1 == line_no else " "
                print(f"{prefix} {i+1:4d} | {lines[i].rstrip()}")

        except Exception as e:
            print("❌ Failed to read source file:", e)

        print("\n🛑 Stopping after showing context.")
        sys.exit(3)

    # === SHOW SOURCE CONTEXT FOR KOTLIN ERRORS ===
    import re

    kt_error = None
    for line in logs.splitlines():
        # Matches: path/file.kt:LINE:
        m = re.search(r'(.+\.kt):(\d+):', line)
        if m:
            kt_error = (m.group(1), int(m.group(2)))
            break

    if kt_error:
        file_path, line_no = kt_error
        print(f"\n📂 Kotlin file: {file_path}")
        print(f"📍 Error line: {line_no}")

        try:
            with open(file_path, "r") as f:
                lines = f.readlines()

            start = max(0, line_no - 6)
            end = min(len(lines), line_no + 5)

            print("\n📄 Source context:")
            for i in range(start, end):
                prefix = ">" if i + 1 == line_no else " "
                print(f"{prefix} {i+1:4d} | {lines[i].rstrip()}")

        except Exception as e:
            print("❌ Failed to read source file:", e)

        print("\n🛑 Stopping after showing context.")
        sys.exit(3)

    # === GEMINI SUGGESTION MODE (NO APPLY) ===
    if kt_error:
        file_path, line_no = kt_error

        context = ""
        try:
            with open(file_path, "r") as f:
                lines = f.readlines()
            start = max(0, line_no - 6)
            end = min(len(lines), line_no + 5)
            for i in range(start, end):
                context += f"{i+1}: {lines[i]}"
        except:
            pass

        prompt = f"""
You are an expert Android/Kotlin engineer.

The Gradle build failed with a Kotlin compilation error.

File: {file_path}
Line: {line_no}

Compiler error log:
{logs}

Source code context:
{context}

Rules:
- Fix ONLY the Kotlin error
- Modify ONLY the given file
- Output ONLY a unified git diff
- NO explanations
- If not sure, output NO_FIX_POSSIBLE
"""

        print("\n📡 Sending error to Gemini for suggestion...")

        payload = {
            "model": "gemini-1.5-pro",
            "contents": [
                {
                    "parts": [{"text": prompt}]
                }
            ]
        }

        r = requests.post(
            f"https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-pro:generateContent?key={GEMINI_API_KEY}",
            headers={"Content-Type": "application/json"},
            data=json.dumps(payload),
            timeout=60
        )

        r.raise_for_status()
        response = r.json()

        suggestion = response["candidates"][0]["content"]["parts"][0]["text"]

        print("\n🧠 Gemini suggestion:")
        print(suggestion)

        print("\n🛑 Suggestion only. Not applied.")
        sys.exit(4)

    # === GEMINI SUGGESTION MODE (NO APPLY) ===
    if kt_error:
        file_path, line_no = kt_error

        context = ""
        try:
            with open(file_path, "r") as f:
                lines = f.readlines()
            start = max(0, line_no - 6)
            end = min(len(lines), line_no + 5)
            for i in range(start, end):
                context += f"{i+1}: {lines[i]}"
        except:
            pass

        prompt = f"""
You are an expert Android/Kotlin engineer.

The Gradle build failed with a Kotlin compilation error.

File: {file_path}
Line: {line_no}

Compiler error log:
{logs}

Source code context:
{context}

Rules:
- Fix ONLY the Kotlin error
- Modify ONLY the given file
- Output ONLY a unified git diff
- NO explanations
- If not sure, output NO_FIX_POSSIBLE
"""

        print("\n📡 Sending error to Gemini for suggestion...")

        payload = {
            "model": "gemini-1.5-pro",
            "contents": [
                {
                    "parts": [{"text": prompt}]
                }
            ]
        }

        r = requests.post(
            f"https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-pro:generateContent?key={GEMINI_API_KEY}",
            headers={"Content-Type": "application/json"},
            data=json.dumps(payload),
            timeout=60
        )

        r.raise_for_status()
        response = r.json()

        suggestion = response["candidates"][0]["content"]["parts"][0]["text"]

        print("\n🧠 Gemini suggestion:")
        print(suggestion)

        print("\n🛑 Suggestion only. Not applied.")
        sys.exit(4)

    # === GEMINI SUGGESTION MODE (NO APPLY) ===
    if kt_error:
        file_path, line_no = kt_error

        context = ""
        try:
            with open(file_path, "r") as f:
                lines = f.readlines()
            start = max(0, line_no - 6)
            end = min(len(lines), line_no + 5)
            for i in range(start, end):
                context += f"{i+1}: {lines[i]}"
        except:
            pass

        prompt = f"""
You are an expert Android/Kotlin engineer.

The Gradle build failed with a Kotlin compilation error.

File: {file_path}
Line: {line_no}

Compiler error log:
{logs}

Source code context:
{context}

Rules:
- Fix ONLY the Kotlin error
- Modify ONLY the given file
- Output ONLY a unified git diff
- NO explanations
- If not sure, output NO_FIX_POSSIBLE
"""

        print("\n📡 Sending error to Gemini for suggestion...")

        payload = {
            "model": "gemini-1.5-pro",
            "contents": [
                {
                    "parts": [{"text": prompt}]
                }
            ]
        }

        r = requests.post(
            f"https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-pro:generateContent?key={GEMINI_API_KEY}",
            headers={"Content-Type": "application/json"},
            data=json.dumps(payload),
            timeout=60
        )

        r.raise_for_status()
        response = r.json()

        suggestion = response["candidates"][0]["content"]["parts"][0]["text"]

        print("\n🧠 Gemini suggestion:")
        print(suggestion)

        print("\n🛑 Suggestion only. Not applied.")
        sys.exit(4)

# === FORCE FIRST KOTLIN ERROR FOR GEMINI ===
if kt_errors:
    file_path, line_no, message = kt_errors[0]
    kt_error = (file_path, line_no)
    logs = message
    print(f"\n🎯 Using first Kotlin error for AI fix:")
    print(f"{file_path}:{line_no} → {message}")

# ================= GEMINI =================
def ask_gemini(prompt):
    headers = {
        "Content-Type": "application/json"
    }

    payload = {
        "contents": [
            {
                "parts": [
                    {"text": prompt}
                ]
            }
        ]
    }

    url = GEMINI_URL + "?key=" + GEMINI_API_KEY
    r = requests.post(url, headers=headers, json=payload, timeout=60)
    r.raise_for_status()

    return r.json()["candidates"][0]["content"]["parts"][0]["text"]

# ================= GEMINI =================
def ask_gemini(prompt):
    headers = {
        "Content-Type": "application/json"
    }

    payload = {
        "contents": [
            {
                "parts": [
                    {"text": prompt}
                ]
            }
        ]
    }

    url = GEMINI_URL + "?key=" + GEMINI_API_KEY
    r = requests.post(url, headers=headers, json=payload, timeout=60)
    r.raise_for_status()

    return r.json()["candidates"][0]["content"]["parts"][0]["text"]
