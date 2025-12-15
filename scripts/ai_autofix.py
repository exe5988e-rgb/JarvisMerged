import subprocess
import os
import openai
import time

MAX_RETRIES = 3
AI_MODEL = "gpt-5-mini"
PATCH_BRANCH = "ai-autofix"
BUILD_CMD = "./gradlew build"

def run(cmd):
    result = subprocess.run(cmd, shell=True, capture_output=True, text=True)
    return result.stdout, result.stderr, result.returncode

def get_build_logs():
    stdout, stderr, code = run(BUILD_CMD)
    with open("build.log", "w") as f:
        f.write(stdout + stderr)
    return stdout + stderr, code

def get_repo_diff():
    diff, _, _ = run("git diff")
    return diff

def send_to_ai(prompt):
    openai.api_key = os.getenv("AI_API_KEY")
    response = openai.ChatCompletion.create(
        model=AI_MODEL,
        messages=[{"role": "user", "content": prompt}]
    )
    return response.choices[0].message.content

def apply_patch(patch_text):
    result = subprocess.run(["git", "apply"], input=patch_text, text=True)
    return result.returncode == 0

def commit_fix():
    run(f"git checkout -b {PATCH_BRANCH}")
    run("git add .")
    run('git commit -m "fix: AI autofix Gradle/AGP issues"')

for attempt in range(1, MAX_RETRIES + 1):
    print(f"\n=== AI Autofix Attempt {attempt} ===")
    logs, code = get_build_logs()
    if code == 0:
        print("Build already passes. No fix needed.")
        break

    diff = get_repo_diff()

    prompt = f"""
You are an expert Android developer with deep knowledge of Gradle, AGP 8/9+, and Kotlin DSL.

Gradle build logs:
{logs}

Current repo diff:
{diff}

Rules:
- Only fix build.gradle or build.gradle.kts files.
- Update deprecated Gradle/AGP settings to modern syntax.
- Fix SDK, Java version, or plugin issues safely.
- Ensure build passes after fix.
- Do NOT touch app source code.
- Output ONLY a git patch (diff).
- If unsure, respond with NO_FIX_POSSIBLE.
"""
    patch = send_to_ai(prompt)

    if "NO_FIX_POSSIBLE" in patch:
        print("AI could not find a fix. Stopping.")
        break

    if apply_patch(patch):
        print("Patch applied. Testing build...")
        logs, code = get_build_logs()
        if code == 0:
            print("✅ Build passed! Committing fix...")
            commit_fix()
            break
        else:
            print("⚠ Build still failing. Reverting patch and retrying...")
            run("git reset --hard")
            time.sleep(2)
    else:
        print("❌ Patch failed to apply. Retrying...")
        run("git reset --hard")
        time.sleep(2)
else:
    print("❌ All AI attempts failed. Manual intervention needed.")
