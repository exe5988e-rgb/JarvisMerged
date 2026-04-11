package com.jarvismini.ui.main

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.jarvismini.ProgressInitializer
import com.jarvismini.core.progress.*
import com.jarvismini.core.routine.RoutineProvider
import com.jarvismini.ui.boot.BootScreen
import com.jarvismini.ui.home.EnhancedHomeScreen
import com.jarvismini.ui.chat.JarvisChatScreen
import com.jarvismini.ui.calendar.CalendarViewModel
import com.jarvismini.ui.calendar.DayCalendarScreen
import com.jarvismini.ui.settings.SettingsScreen
import com.jarvismini.ui.debug.DebugScreen
import com.jarvismini.ui.checklist.JarvisChecklistScreen
import com.jarvismini.ui.llm.TermuxCommandScreen
import com.jarvismini.agent.AgentDashboardScreen
import com.jarvismini.agent.HealDashboardScreen

enum class MainTab {
    Home,
    Chat,
    Calendar,
    Checklist,
    Settings,
    Debug,
    TermuxCommand,
    AgentDashboard,
    HealDashboard,
}

@Composable
fun MainScreen() {
    val context = LocalContext.current

    var showBoot    by remember { mutableStateOf(true) }
    var selectedTab by remember { mutableStateOf(MainTab.Home) }
    var voiceTask   by remember { mutableStateOf<String?>(null) }

    var blocks   by remember { mutableStateOf(emptyList<ProgressBlock>()) }
    var routines by remember { mutableStateOf(emptyList<com.jarvismini.core.routine.model.Routine>()) }

    if (showBoot) {
        BootScreen(onBootComplete = { showBoot = false })
        return
    }

    LaunchedEffect(Unit) {
        ProgressInitializer.registerAllBlocks(context)
        blocks   = ProgressRepository.getTodayBlocks()
        routines = RoutineProvider.getAllRoutines(context)
    }

    LaunchedEffect(selectedTab) {
        if (selectedTab == MainTab.Checklist) {
            ProgressInitializer.registerAllBlocks(context)
            blocks   = ProgressRepository.getTodayBlocks()
            routines = RoutineProvider.getAllRoutines(context)
        }
    }

    when (selectedTab) {
        MainTab.Home -> EnhancedHomeScreen(
            onNavigateToChat          = { selectedTab = MainTab.Chat },
            onNavigateToCalendar      = { selectedTab = MainTab.Calendar },
            onNavigateToChecklist     = { selectedTab = MainTab.Checklist },
            onNavigateToSettings      = { selectedTab = MainTab.Settings },
            onNavigateToDebug         = { selectedTab = MainTab.Debug },
            onNavigateToTermuxCommand = { selectedTab = MainTab.TermuxCommand },
            onNavigateToAgent         = { selectedTab = MainTab.AgentDashboard },
            onVoiceTask               = { task ->
                voiceTask   = task
                selectedTab = MainTab.AgentDashboard
            },
        )
        MainTab.Chat          -> JarvisChatScreen(onBack = { selectedTab = MainTab.Home })
        MainTab.Calendar      -> {
            val vm = remember { CalendarViewModel(context) }
            DayCalendarScreen(viewModel = vm, onBack = { selectedTab = MainTab.Home })
        }
        MainTab.Checklist     -> JarvisChecklistScreen(onBack = { selectedTab = MainTab.Home })
        MainTab.Settings      -> SettingsScreen(onBack = { selectedTab = MainTab.Home })
        MainTab.Debug         -> DebugScreen(onBack = { selectedTab = MainTab.Home })
        MainTab.TermuxCommand -> TermuxCommandScreen(onNavigateBack = { selectedTab = MainTab.Home })
        MainTab.AgentDashboard -> AgentDashboardScreen(
            onBack      = { selectedTab = MainTab.Home },
            initialTask = voiceTask.also { voiceTask = null },
        )
        MainTab.HealDashboard -> HealDashboardScreen(
            onBack = { selectedTab = MainTab.Home },
        )
    }
}


//===== TERMUXFILE: agent_http_bridge.py =====
#!/usr/bin/env python3
"""
agent_http_bridge.py  —  Port 8891

Endpoints:
  GET  /health
  POST /agent_task  {"task": "..."}        <- normal agent loop
  POST /heal        {"task": "..."}        <- self-improving pipeline (fire-and-forget)
"""

import json
import os
import sys
import time
import threading
from http.server import HTTPServer, BaseHTTPRequestHandler
from pathlib import Path

# ── Paths ─────────────────────────────────────────────────────────────────────
# __file__ is ~/workflows/agent_http_bridge.py  →  parent is ~/workflows/
ROOT       = Path(__file__).parent / "test" / "jarvis-agent"
JARVIS_DIR = Path("/sdcard/jarvis")
TASK_FILE  = JARVIS_DIR / "agent_task.txt"
RESULT_FILE= JARVIS_DIR / "agent_result.txt"
STATUS_FILE= JARVIS_DIR / "agent_status.txt"

PORT       = 8891
ADB_DEVICE = "192.168.29.48:37981"

sys.path.insert(0, str(ROOT))

# ── Normal agent runner ───────────────────────────────────────────────────────

def run_agent_task(task: str) -> str:
    try:
        from agent.state               import AgentState
        from perception.screen_capture import capture_screen
        from perception.ui_mock_parser  import parse_ui
        from reasoning.prompt_builder   import build_prompt
        from reasoning.llm_client       import generate
        from reasoning.action_parser    import parse_action
        from executor.adb_executor      import execute

        STATUS_FILE.write_text("running")
        state = AgentState(task=task, max_steps=20)
        last_action_label = "none"

        while not state.is_finished():
            try:
                img    = capture_screen(adb_device=ADB_DEVICE)
                ui     = parse_ui(img)
                prompt = build_prompt(task, ui, history=state.history)
                out    = generate(prompt)
                action = parse_action(out)
                execute(action, ui, adb_device=ADB_DEVICE)
                state.record(action, ui)

                if action.get("action") == "done":
                    state.done = True
                    break

                act = action.get("action", "?")
                if act == "tap" and action.get("target"):
                    tid   = action.get("target")
                    label = next((e["text"] for e in ui if e["id"] == tid), f"item {tid}")
                    last_action_label = f"tapped {label}"
                elif act == "type":
                    last_action_label = f"typed '{action.get('text','')}'"
                else:
                    last_action_label = act

                time.sleep(1.5)

            except Exception as e:
                print(f"[bridge] step error: {e}")
                time.sleep(3)
                state.step += 1
                continue

        if state.done:
            result = f"Done. {task} completed in {state.step} steps."
        elif state.step >= state.max_steps:
            result = f"Reached maximum steps. Last action: {last_action_label}."
        else:
            result = f"Task stopped. Last action: {last_action_label}."

        STATUS_FILE.write_text("ready")
        return result

    except Exception as e:
        STATUS_FILE.write_text("error")
        return f"Agent error: {str(e)}"


# ── Self-heal runner ──────────────────────────────────────────────────────────

def run_heal_task(task: str):
    """Fire-and-forget — runs start_agent_task() in background thread."""
    def _run():
        try:
            from agent.self_repair import start_agent_task
            print(f"[heal] Starting self-repair pipeline: {task[:80]}")
            result = start_agent_task(task, dump_first=True)
            if result.get("success"):
                print(f"[heal] ✅ Pipeline complete — patches applied")
            else:
                print(f"[heal] ✗ Pipeline failed: {result.get('error')}")
        except Exception as e:
            print(f"[heal] ✗ Exception: {e}")

    threading.Thread(target=_run, daemon=True).start()


# ── File IPC watcher ──────────────────────────────────────────────────────────

def file_ipc_watcher():
    print("[bridge] File IPC watcher started")
    last_task = ""
    JARVIS_DIR.mkdir(exist_ok=True)
    while True:
        try:
            if TASK_FILE.exists() and TASK_FILE.stat().st_size > 0:
                task = TASK_FILE.read_text().strip()
                if task and task != last_task:
                    last_task = task
                    print(f"[bridge] File IPC task: {task}")
                    TASK_FILE.write_text("")
                    result = run_agent_task(task)
                    RESULT_FILE.write_text(result)
                    print(f"[bridge] File IPC result: {result}")
        except Exception as e:
            print(f"[bridge] File IPC error: {e}")
        time.sleep(0.5)


# ── HTTP server ───────────────────────────────────────────────────────────────

class BridgeHandler(BaseHTTPRequestHandler):

    def log_message(self, fmt, *args):
        print(f"[bridge] {fmt % args}")

    def send_json(self, code, data):
        body = json.dumps(data).encode()
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Access-Control-Allow-Origin", "*")
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self):
        if self.path == "/health":
            self.send_json(200, {
                "success": True,
                "status":  STATUS_FILE.read_text().strip() if STATUS_FILE.exists() else "ready"
            })
        else:
            self.send_json(404, {"success": False, "error": "not found"})

    def do_POST(self):
        length = int(self.headers.get("Content-Length", 0))
        body   = self.rfile.read(length)
        data   = json.loads(body.decode())

        if self.path == "/agent_task":
            task = data.get("task", "").strip()
            if not task:
                self.send_json(400, {"success": False, "error": "missing task"})
                return
            print(f"\n[bridge] ══ TASK: {task} ══")
            result = run_agent_task(task)
            print(f"[bridge] ══ RESULT: {result} ══\n")
            self.send_json(200, {"success": True, "result": result})

        elif self.path == "/heal":
            task = data.get("task", "").strip()
            if not task:
                self.send_json(400, {"success": False, "error": "missing task"})
                return
            print(f"\n[bridge] ══ HEAL: {task} ══")
            run_heal_task(task)
            self.send_json(200, {"success": True, "status": "started"})

        else:
            self.send_json(404, {"success": False, "error": "not found"})


# ── Main ──────────────────────────────────────────────────────────────────────

if __name__ == "__main__":
    JARVIS_DIR.mkdir(exist_ok=True)
    STATUS_FILE.write_text("ready")

    threading.Thread(target=file_ipc_watcher, daemon=True).start()

    print(f"╔══════════════════════════════════════════════════════╗")
    print(f"║  JARVIS AGENT HTTP BRIDGE                            ║")
    print(f"╚══════════════════════════════════════════════════════╝")
    print(f"  Port    : {PORT}")
    print(f"  Device  : {ADB_DEVICE}")
    print(f"  Endpoints:")
    print(f"    GET  /health")
    print(f"    POST /agent_task  {{\"task\": \"...\"}}")
    print(f"    POST /heal        {{\"task\": \"...\"}}")
    print(f"")

    server = HTTPServer(("0.0.0.0", PORT), BridgeHandler)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("\n[bridge] Stopped.")
        server.shutdown()
