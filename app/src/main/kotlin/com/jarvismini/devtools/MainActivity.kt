package com.jarvismini.devtools

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import com.jarvismini.devtools.autobuild.AutoBuildService
import com.jarvismini.devtools.autobuild.ModeStore
import com.jarvismini.devtools.autobuild.models.AutoBuildState
import com.jarvismini.devtools.autobuild.models.ExtractionMode
import com.jarvismini.devtools.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val logBuilder = SpannableStringBuilder()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupModeSpinner()
        setupButtons()
        registerCallbacks()
        refreshServiceState()
    }

    override fun onResume() { super.onResume(); refreshServiceState() }

    override fun onDestroy() {
        super.onDestroy()
        AutoBuildService.onStatusUpdate = null
        AutoBuildService.onLogLine      = null
    }

    private fun setupModeSpinner() {
        val labels = listOf(
            "Code block in chat",
            "Downloaded file(s) → /sdcard",
            "Plain text in chat"
        )
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, labels).also {
            it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        binding.spinnerMode.adapter = adapter
        binding.spinnerMode.setSelection(ModeStore.load(this).ordinal)
        binding.spinnerMode.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, pos: Int, id: Long) {
                val mode = ExtractionMode.entries[pos]
                ModeStore.save(this@MainActivity, mode)
                appendLog("Mode: ${mode.name}", Color.CYAN)
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
    }

    private fun setupButtons() {
        binding.btnStart.setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        binding.btnStop.setOnClickListener {
            AutoBuildService.requestStop()
            appendLog("Stop requested by user.", Color.YELLOW)
            binding.btnStop.isEnabled = false
        }
        binding.btnAccessibility.setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
    }

    private fun registerCallbacks() {
        AutoBuildService.onStatusUpdate = { iteration, state -> runOnUiThread { updateStatus(iteration, state) } }
        AutoBuildService.onLogLine      = { line, isError -> runOnUiThread { appendLog(line, if (isError) Color.RED else Color.WHITE) } }
    }

    private fun refreshServiceState() {
        val enabled = isAccessibilityServiceEnabled()
        if (enabled) {
            binding.tvAccessibilityHint.visibility = View.GONE
            binding.btnStart.isEnabled  = false
            binding.btnStop.isEnabled   = true
            if (AutoBuildService.isLoopRunning) updateStatus(AutoBuildService.currentIteration, AutoBuildService.currentState)
        } else {
            binding.tvAccessibilityHint.visibility = View.VISIBLE
            binding.btnStart.isEnabled  = true
            binding.btnStop.isEnabled   = false
            setStatusDot(Color.GRAY)
            binding.tvStatus.text = getString(R.string.status_idle)
        }
    }

    private fun updateStatus(iteration: Int, state: AutoBuildState) {
        binding.tvIteration.text = "Iter: $iteration"
        binding.tvStatus.text    = state.displayName()
        setStatusDot(state.dotColor())
    }

    private fun setStatusDot(color: Int) {
        binding.statusDot.backgroundTintList = android.content.res.ColorStateList.valueOf(color)
    }

    private fun appendLog(line: String, color: Int) {
        val start = logBuilder.length
        logBuilder.append(line).append("\n")
        logBuilder.setSpan(ForegroundColorSpan(color), start, logBuilder.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        binding.tvLog.text = logBuilder
        binding.scrollLog.post { binding.scrollLog.fullScroll(View.FOCUS_DOWN) }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val enabledServices = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        val short = "${packageName}/.autobuild.AutoBuildService"
        val full  = "${packageName}/com.jarvismini.devtools.autobuild.AutoBuildService"
        return enabledServices.split(":").any { it.trim().equals(short, ignoreCase = true) || it.trim().equals(full, ignoreCase = true) }
    }

    private fun AutoBuildState.displayName() = when (this) {
        AutoBuildState.IDLE                       -> "Idle"
        AutoBuildState.WAITING_FOR_RESPONSE       -> "Waiting for Claude response…"
        AutoBuildState.DOWNLOAD_AI_OUTPUT         -> "Downloading ai-output.txt…"
        AutoBuildState.COPY_TO_AUTOMATION_DIR     -> "Copying to automation dir…"
        AutoBuildState.TRIGGER_BUILD              -> "Triggering build…"
        AutoBuildState.WAITING_FOR_BUILD          -> "Waiting for build…"
        AutoBuildState.BUILD_SUCCEEDED            -> "✅ Build succeeded!"
        AutoBuildState.ATTACHING_ERROR_REPORT     -> "Attaching error report…"
        AutoBuildState.TIMEOUT_ERROR              -> "⚠ Timeout — retrying…"
        AutoBuildState.AGENT_IDLE                 -> "Agent: idle"
        AutoBuildState.AGENT_SENDING_DUMP         -> "Agent: sending dump…"
        AutoBuildState.AGENT_WAITING_FOR_RESPONSE -> "Agent: waiting for Claude…"
        AutoBuildState.AGENT_DOWNLOAD_OUTPUT      -> "Agent: downloading output…"
        AutoBuildState.AGENT_STAGING_OUTPUT       -> "Agent: staging for Phone A…"
        AutoBuildState.AGENT_LOOP_DONE            -> "Agent: ✅ done"
    }

    private fun AutoBuildState.dotColor() = when (this) {
        AutoBuildState.BUILD_SUCCEEDED, AutoBuildState.AGENT_LOOP_DONE -> Color.GREEN
        AutoBuildState.TIMEOUT_ERROR                                    -> Color.RED
        AutoBuildState.IDLE, AutoBuildState.AGENT_IDLE                 -> Color.GRAY
        AutoBuildState.WAITING_FOR_BUILD, AutoBuildState.TRIGGER_BUILD -> Color.YELLOW
        AutoBuildState.AGENT_SENDING_DUMP,
        AutoBuildState.AGENT_WAITING_FOR_RESPONSE,
        AutoBuildState.AGENT_DOWNLOAD_OUTPUT,
        AutoBuildState.AGENT_STAGING_OUTPUT                            -> Color.MAGENTA
        else                                                            -> Color.CYAN
    }
}


//===== TERMUXFILE: test/jarvis-agent/agent/self_repair.py =====
#!/usr/bin/env python3
"""
self_repair.py  —  Jarvis Self-Repair Module  (Session 20)
~/workflows/test/jarvis-agent/agent/self_repair.py

Capabilities:
  1.  find_file()          — search codebase by filename or content keyword
  2.  read_file()          — read any file under JARVIS_ROOT safely
  3.  write_file()         — write/patch a file with auto .bak backup
  4.  push_to_bridge()     — send a file to Phone B over HTTP (/file_push)
  5.  pull_from_bridge()   — pull a file Phone B staged for us (/file_pull)
  6.  request_fix()        — ask Claude via bridge to fix a file, deploy answer
  7.  dump_codebase()      — regenerate new_dump.txt via slash.sh
  8.  pause()              — create pause flag, agent stops after current step
  9.  resume()             — remove pause flag, agent continues
  10. is_paused()          — check if pause flag exists
  11. start_agent_task()   — push dump to Phone B and trigger DevTools agent loop

Session 20: added start_agent_task() — triggers the full autonomous OPT loop:
  1. dump_codebase() → ~/new_dump.txt
  2. push dump to Phone B staged/
  3. ADB copy dump → /sdcard/Download/new_dump.txt on Phone B
  4. POST /agent_task to bridge → writes agent_task.txt on Phone B sdcard
  5. DevTools detects agent_task.txt → attaches dump to Claude → downloads output
  6. Phone A polls pull_from_bridge("ai-output.txt") until available
  7. Saves ai-output.txt to JARVIS_ROOT for extract_ai_output.sh to apply
"""

import os
import re
import json
import shutil
import requests
import subprocess
import time
from pathlib import Path
from datetime import datetime

# ── Paths ──────────────────────────────────────────────────────────────────────
HOME        = Path.home()
JARVIS_ROOT = HOME / "workflows" / "test"
AGENT_ROOT  = JARVIS_ROOT / "jarvis-agent"
SLASH_SH    = HOME / "slash.sh"
DUMP_OUT    = HOME / "new_dump.txt"
PAUSE_FLAG  = HOME / "workflows" / ".jarvis_pause"

# Phone B bridge
BRIDGE_URL  = "http://192.168.29.110:8890"
PHONE_B_ADB = "192.168.29.110:35451"
ADB         = "adb"

# ── ANSI colours ───────────────────────────────────────────────────────────────
G = "\033[1;32m"; Y = "\033[1;33m"; R = "\033[1;31m"
C = "\033[1;36m"; W = "\033[1;37m"; D = "\033[2m";  X = "\033[0m"

def _log(tag: str, msg: str, colour: str = W):
    ts = datetime.now().strftime("%H:%M:%S")
    print(f"{D}[{ts}]{X} {colour}[{tag}]{X} {msg}")

# ── 1. find_file ───────────────────────────────────────────────────────────────

def find_file(name: str = "", keyword: str = "",
              root: Path = JARVIS_ROOT) -> list:
    results = []
    name_lo = name.lower()
    for p in Path(root).rglob("*"):
        if not p.is_file():
            continue
        if p.suffix in (".pyc", ".pyo") or "__pycache__" in p.parts:
            continue
        if name_lo and name_lo not in p.name.lower():
            continue
        entry = {"path": str(p), "size": p.stat().st_size, "match_lines": []}
        if keyword:
            try:
                kw_lo = keyword.lower()
                lines = p.read_text(errors="replace").splitlines()
                entry["match_lines"] = [
                    f"L{i+1}: {ln.rstrip()}"
                    for i, ln in enumerate(lines)
                    if kw_lo in ln.lower()
                ]
                if name_lo == "" and not entry["match_lines"]:
                    continue
            except Exception:
                pass
        results.append(entry)
    _log("find_file", f"{len(results)} result(s) for name={name!r} keyword={keyword!r}", G)
    for r in results:
        ml = f"  ({len(r['match_lines'])} matches)" if r["match_lines"] else ""
        _log("find_file", f"  {r['path']}{ml}", D)
    return results

# ── 2. read_file ───────────────────────────────────────────────────────────────

def read_file(path: str) -> str:
    p = Path(path)
    if not p.is_absolute():
        p = JARVIS_ROOT / path
    if not p.exists():
        msg = f"[ERROR] File not found: {p}"
        _log("read_file", msg, R)
        return msg
    try:
        content = p.read_text(errors="replace")
        _log("read_file", f"Read {len(content)} chars from {p}", G)
        return content
    except Exception as e:
        msg = f"[ERROR] Could not read {p}: {e}"
        _log("read_file", msg, R)
        return msg

# ── 3. write_file ──────────────────────────────────────────────────────────────

def write_file(path: str, content: str, backup: bool = True) -> bool:
    p = Path(path)
    if not p.is_absolute():
        p = JARVIS_ROOT / path
    p.parent.mkdir(parents=True, exist_ok=True)
    if backup and p.exists():
        bak = p.with_suffix(p.suffix + ".bak")
        shutil.copy2(p, bak)
        _log("write_file", f"Backup saved: {bak}", Y)
    try:
        p.write_text(content)
        _log("write_file", f"Written {len(content)} chars to {p}", G)
        return True
    except Exception as e:
        _log("write_file", f"[ERROR] Write failed: {e}", R)
        return False

# ── 4. push_to_bridge ─────────────────────────────────────────────────────────

def push_to_bridge(local_path: str, remote_name: str = "") -> bool:
    p = Path(local_path)
    if not p.is_absolute():
        p = JARVIS_ROOT / local_path
    if not p.exists():
        _log("push_to_bridge", f"[ERROR] File not found: {p}", R)
        return False
    remote_name = remote_name or p.name
    try:
        resp = requests.post(
            f"{BRIDGE_URL}/file_push",
            json={"name": remote_name, "content": p.read_text(errors="replace")},
            timeout=15
        )
        if resp.ok:
            _log("push_to_bridge", f"Pushed {p.name} → Phone B staged/{remote_name}", G)
            return True
        else:
            _log("push_to_bridge", f"[ERROR] Bridge returned {resp.status_code}: {resp.text[:80]}", R)
            return False
    except Exception as e:
        _log("push_to_bridge", f"[ERROR] {e}", R)
        return False

# ── 5. pull_from_bridge ───────────────────────────────────────────────────────

def pull_from_bridge(remote_name: str, save_to: str = "") -> str:
    save_path = Path(save_to) if save_to else JARVIS_ROOT / remote_name
    try:
        resp = requests.get(
            f"{BRIDGE_URL}/file_pull",
            params={"name": remote_name},
            timeout=15
        )
        if resp.ok:
            data    = resp.json()
            content = data.get("content", "")
            save_path.parent.mkdir(parents=True, exist_ok=True)
            save_path.write_text(content)
            _log("pull_from_bridge", f"Pulled {remote_name} → {save_path}", G)
            return str(save_path)
        else:
            _log("pull_from_bridge", f"[ERROR] {resp.status_code}: {resp.text[:80]}", R)
            return ""
    except Exception as e:
        _log("pull_from_bridge", f"[ERROR] {e}", R)
        return ""

# ── 6. request_fix ────────────────────────────────────────────────────────────

def request_fix(file_path: str, problem: str, deploy: bool = True) -> str:
    content = read_file(file_path)
    if content.startswith("[ERROR]"):
        return content
    prompt = (
        f"You are fixing a bug in the Jarvis Android agent codebase.\n\n"
        f"FILE: {file_path}\n"
        f"PROBLEM: {problem}\n\n"
        f"CURRENT CODE:\n```python\n{content}\n```\n\n"
        f"Return ONLY the complete fixed file content with no explanation. "
        f"Use the header format: //===== TERMUXFILE: {file_path} ====="
    )
    _log("request_fix", f"Sending fix request for {file_path} to bridge...", Y)
    try:
        resp = requests.post(
            f"{BRIDGE_URL}/chat",
            json={"prompt": prompt},
            timeout=180
        )
        if not resp.ok:
            _log("request_fix", f"[ERROR] Bridge {resp.status_code}", R)
            return f"[ERROR] Bridge returned {resp.status_code}"
        response_text = resp.json().get("response", "")
        _log("request_fix", f"Got {len(response_text)} chars from Claude", G)
        if deploy and response_text and not response_text.startswith("[ERROR]"):
            pattern = r"//===== TERMUXFILE:[^\n]*=====\n(.*?)(?=//=====|\Z)"
            m = re.search(pattern, response_text, re.DOTALL)
            code = m.group(1).strip() if m else response_text.strip()
            if len(code) > 50:
                write_file(file_path, code)
                _log("request_fix", f"Deployed fix to {file_path}", G)
            else:
                _log("request_fix", "Response too short to deploy — check manually", Y)
        return response_text
    except Exception as e:
        _log("request_fix", f"[ERROR] {e}", R)
        return f"[ERROR] {e}"

# ── 7. dump_codebase ──────────────────────────────────────────────────────────

def dump_codebase(output: str = str(DUMP_OUT)) -> bool:
    if not SLASH_SH.exists():
        _log("dump_codebase", f"[ERROR] slash.sh not found at {SLASH_SH}", R)
        return False
    try:
        _log("dump_codebase", f"Running slash.sh → {output} ...", Y)
        with open(output, "w") as f:
            result = subprocess.run(
                ["bash", str(SLASH_SH), str(JARVIS_ROOT)],
                stdout=f, stderr=subprocess.PIPE, timeout=60
            )
        if result.returncode == 0:
            size = Path(output).stat().st_size
            _log("dump_codebase", f"Dump written: {output} ({size} bytes)", G)
            return True
        else:
            _log("dump_codebase", f"[ERROR] slash.sh exited {result.returncode}: {result.stderr.decode()[:200]}", R)
            return False
    except Exception as e:
        _log("dump_codebase", f"[ERROR] {e}", R)
        return False

# ── 8/9/10. pause / resume / is_paused ───────────────────────────────────────

def pause() -> bool:
    try:
        PAUSE_FLAG.parent.mkdir(parents=True, exist_ok=True)
        PAUSE_FLAG.touch()
        _log("pause", f"Pause flag created: {PAUSE_FLAG}", Y)
        return True
    except Exception as e:
        _log("pause", f"[ERROR] {e}", R)
        return False

def resume() -> bool:
    try:
        PAUSE_FLAG.unlink(missing_ok=True)
        _log("resume", f"Pause flag removed — agent will resume", G)
        return True
    except Exception as e:
        _log("resume", f"[ERROR] {e}", R)
        return False

def is_paused() -> bool:
    return PAUSE_FLAG.exists()

# ── 11. start_agent_task ──────────────────────────────────────────────────────

def start_agent_task(task: str,
                     dump_first: bool = True,
                     poll_timeout: int = 300,
                     poll_interval: int = 5) -> str:
    """
    Session 20 — Full autonomous OPT loop:
      1. Optionally regenerate new_dump.txt via slash.sh
      2. Push dump to Phone B bridge staged/
      3. ADB copy dump → /sdcard/Download/new_dump.txt on Phone B
         (DevTools file picker reads from Downloads)
      4. POST /agent_task → writes agent_task.txt on Phone B sdcard
         (DevTools accessibility service polls for this file)
      5. Poll pull_from_bridge("ai-output.txt") until Phone B stages it
      6. Save ai-output.txt to JARVIS_ROOT ready for extract_ai_output.sh

    Returns path to saved ai-output.txt, or empty string on failure.
    """
    _log("start_agent_task", f"Task: {task!r}", C)

    # Step 1: dump codebase
    if dump_first:
        _log("start_agent_task", "Generating codebase dump...", Y)
        if not dump_codebase():
            _log("start_agent_task", "[ERROR] dump_codebase failed", R)
            return ""

    # Step 2: push dump to Phone B staged/
    _log("start_agent_task", "Pushing dump to Phone B staged/...", Y)
    if not push_to_bridge(str(DUMP_OUT), "new_dump.txt"):
        _log("start_agent_task", "[ERROR] push_to_bridge failed", R)
        return ""

    # Step 3: ADB copy dump → /sdcard/Download/new_dump.txt on Phone B
    # DevTools file picker needs the file in /sdcard/Download/ to find it
    _log("start_agent_task", "ADB copying dump to Phone B Downloads...", Y)
    try:
        adb_result = subprocess.run(
            [ADB, "-s", PHONE_B_ADB, "push",
             str(DUMP_OUT), "/sdcard/Download/new_dump.txt"],
            capture_output=True, text=True, timeout=30
        )
        if adb_result.returncode != 0:
            _log("start_agent_task", f"[ERROR] ADB push failed: {adb_result.stderr[:100]}", R)
            return ""
        _log("start_agent_task", "ADB push to Downloads done", G)
    except Exception as e:
        _log("start_agent_task", f"[ERROR] ADB push exception: {e}", R)
        return ""

    # Step 4: trigger DevTools agent loop via /agent_task
    _log("start_agent_task", "Triggering DevTools agent loop...", Y)
    try:
        resp = requests.post(
            f"{BRIDGE_URL}/agent_task",
            json={"task": task, "dump_name": "new_dump.txt"},
            timeout=15
        )
        if not resp.ok:
            _log("start_agent_task", f"[ERROR] /agent_task returned {resp.status_code}: {resp.text[:80]}", R)
            return ""
        _log("start_agent_task", f"DevTools triggered: {resp.json()}", G)
    except Exception as e:
        _log("start_agent_task", f"[ERROR] /agent_task request failed: {e}", R)
        return ""

    # Step 5: poll for ai-output.txt in Phone B staged/
    _log("start_agent_task", f"Polling for ai-output.txt (timeout={poll_timeout}s)...", Y)
    save_path = JARVIS_ROOT / "ai-output.txt"
    elapsed = 0
    while elapsed < poll_timeout:
        time.sleep(poll_interval)
        elapsed += poll_interval
        _log("start_agent_task", f"Polling... {elapsed}/{poll_timeout}s", D)
        result = pull_from_bridge("ai-output.txt", save_to=str(save_path))
        if result:
            _log("start_agent_task", f"✅ ai-output.txt received → {save_path}", G)
            return str(save_path)

    _log("start_agent_task", f"[ERROR] Timed out after {poll_timeout}s waiting for ai-output.txt", R)
    return ""
