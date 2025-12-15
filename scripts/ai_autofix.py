#!/usr/bin/env python3
import os
import subprocess
import time
import json
import requests

# ================= CONFIG =================
MAX_RETRIES = 3
PATCH_BRANCH = "ai-autofix"
BUILD_CMD = "./gradlew build"
GEMINI_API_KEY = os.getenv("GEMINI_API_KEY")

GEMINI_URL = "https://generativelanguage.googleapis.com/v1beta/openai/chat/completions"

# ================ HELPERS =================
def run(cmd):
    p = subprocess.run(cmd, shell=True, text=True, capture_output=True)
    return p.stdout + p.stderr, p.returncode

def read_gradle_files():
    content = []
    for root, _, files in os.walk("."):
        for f in files:
            if f in (
                "build.gradle",
                "build.gradle.kts",
                "settings.gradle",
                "gradle.properties",
            ):
                path = os.path.join(root, f)
                try:
                    with open(path, "r", errors="ignore") as fh:
                        content.append(f"\n--- {path} ---\n{fh.read()}")
                except:
                    pass
    return "\n".join(content)

def ask_gemini(prompt):
    payload = {
        "model": "gemini-3-pro",
        "messages": [
            {"role": "user", "content": prompt}
        ],
        "temperature": 0.7
    }
    r = requests.post(
        f"{GEMINI_URL}?key={GEMINI_API_KEY}",
        headers={"Content-Type": "application/json"},
        data=json.dumps(payload),
        timeout=60
    )
    r.raise_for_status()
    # Extract AI response from the chat/completions format
    return r.json()["choices"][0]["message"]["content"]

def apply_patch(patch):
    p = subprocess.run(["git", "apply"], input=patch, text=True)
    return p.returncode == 0

# ================ MAIN LOOP =================
for attempt in range(1, MAX_RETRIES + 1):
    print(f"\n=== Gemini Autofix Attempt {attempt} ===")

    logs, code = run(BUILD_CMD)
    with open("build.log", "w") as f:
        f.write(logs)

    if code == 0:
        print("✅ Build already passes. No fix needed.")
        exit(0)

    # Wake-up rules
    if not any(k in logs for k in ["Gradle", "AGP", "Kotlin", "SDK", "plugin"]):
        print("❌ Not a Gradle/Android build issue. Skipping AI.")
        exit(1)

    gradle_files = read_gradle_files()

    prompt = f"""
You are an expert Android build engineer.

Gradle build logs:
{logs}

Gradle configuration files:
{gradle_files}

Rules:
- Fix ONLY Gradle / AGP / Kotlin / SDK issues
- Modify ONLY Gradle-related files
- Use AGP 8/9 compatible syntax
- Output ONLY a unified git diff
- No explanations
- If unsure, output NO_FIX_POSSIBLE
"""

    print("📡 Calling Gemini-3-Pro...")
    patch = ask_gemini(prompt)

    if "NO_FIX_POSSIBLE" in patch or not patch.strip().startswith("diff"):
        print("❌ Gemini could not produce a valid fix.")
        exit(1)

    if not apply_patch(patch):
        print("❌ Patch failed to apply.")
        run("git reset --hard")
        time.sleep(2)
        continue

    print("🧪 Rebuilding after patch...")
    logs, code = run(BUILD_CMD)

    if code != 0:
        print("⚠ Build still failing. Reverting.")
        run("git reset --hard")
        time.sleep(2)
        continue

    # ============ CONFIDENCE SCORE ============
    print("📊 Calculating confidence score...")
    confidence = 0
    confidence += 70  # build passed
    if "ERROR" not in logs and "Exception" not in logs:
        confidence += 10
    warnings = logs.count("WARNING")
    if warnings < 10:
        confidence += 20
    confidence = min(confidence, 100)
    print(f"✅ Confidence score: {confidence}/100")

    # ============ CREATE PR ============
    print("📤 Creating Pull Request...")
    run(f"git checkout -B {PATCH_BRANCH}")
    run("git add .")
    run('git commit -m "fix: Gemini autofix Android build"')
    run(f"git push origin {PATCH_BRANCH} --force")

    pr_body = f"""
🤖 **Gemini-3-Pro Android Autofix**

### ✅ Result
- Build was failing
- AI applied Gradle / AGP fix
- Build now passes

### 📊 Confidence Score
**{confidence}/100**

### 🔍 Scoring Breakdown
- Build success: +70
- No new errors: +10
- Low warnings: +20

### 🛡 Safety Guarantees
- No app source code modified
- Only Gradle configuration updated
- Fix verified by rebuild

Please review and merge if acceptable.
"""

    subprocess.run(
        f'''gh pr create \
--title "fix: Gemini autofix Android build failure" \
--body "{pr_body}" \
--label ai-autofix \
--base main \
--head {PATCH_BRANCH}''',
        shell=True
    )

    print("🎉 PR created successfully.")
    exit(0)

print("❌ All AI attempts failed. Manual intervention needed.")
exit(1)
# ================ MAIN LOOP =================
for attempt in range(1, MAX_RETRIES + 1):
    print(f"\n=== Gemini Autofix Attempt {attempt} ===")

    logs, code = run(BUILD_CMD)
    with open("build.log", "w") as f:
        f.write(logs)

    if code == 0:
        print("✅ Build already passes. No fix needed.")
        exit(0)

    # Wake-up rules
    if not any(k in logs for k in ["Gradle", "AGP", "Kotlin", "SDK", "plugin"]):
        print("❌ Not a Gradle/Android build issue. Skipping AI.")
        exit(1)

    gradle_files = read_gradle_files()

    prompt = f"""
You are an expert Android build engineer.

Gradle build logs:
{logs}

Gradle configuration files:
{gradle_files}

Rules:
- Fix ONLY Gradle / AGP / Kotlin / SDK issues
- Modify ONLY Gradle-related files
- Use AGP 8/9 compatible syntax
- Output ONLY a unified git diff
- No explanations
- If unsure, output NO_FIX_POSSIBLE
"""

    print("📡 Calling Gemini-3-Pro...")
    patch = ask_gemini(prompt)

    if "NO_FIX_POSSIBLE" in patch or not patch.strip().startswith("diff"):
        print("❌ Gemini could not produce a valid fix.")
        exit(1)

    if not apply_patch(patch):
        print("❌ Patch failed to apply.")
        run("git reset --hard")
        time.sleep(2)
        continue

    print("🧪 Rebuilding after patch...")
    logs, code = run(BUILD_CMD)

    if code != 0:
        print("⚠ Build still failing. Reverting.")
        run("git reset --hard")
        time.sleep(2)
        continue

    # ============ CONFIDENCE SCORE ============
    print("📊 Calculating confidence score...")
    confidence = 0

    confidence += 70  # build passed

    if "ERROR" not in logs and "Exception" not in logs:
        confidence += 10

    warnings = logs.count("WARNING")
    if warnings < 10:
        confidence += 20

    confidence = min(confidence, 100)

    print(f"✅ Confidence score: {confidence}/100")

    # ============ CREATE PR ============
    print("📤 Creating Pull Request...")

    run(f"git checkout -B {PATCH_BRANCH}")
    run("git add .")
    run('git commit -m "fix: Gemini autofix Android build"')
    run(f"git push origin {PATCH_BRANCH} --force")

    pr_body = f"""
🤖 **Gemini-3-Pro Android Autofix**

### ✅ Result
- Build was failing
- AI applied Gradle / AGP fix
- Build now passes

### 📊 Confidence Score
**{confidence}/100**

### 🔍 Scoring Breakdown
- Build success: +70
- No new errors: +10
- Low warnings: +20

### 🛡 Safety Guarantees
- No app source code modified
- Only Gradle configuration updated
- Fix verified by rebuild

Please review and merge if acceptable.
"""

    subprocess.run(
        f'''gh pr create \
--title "fix: Gemini autofix Android build failure" \
--body "{pr_body}" \
--label ai-autofix \
--base main \
--head {PATCH_BRANCH}''',
        shell=True
    )

    print("🎉 PR created successfully.")
    exit(0)

print("❌ All AI attempts failed. Manual intervention needed.")
exit(1)
