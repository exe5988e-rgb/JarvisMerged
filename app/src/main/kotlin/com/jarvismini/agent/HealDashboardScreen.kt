package com.jarvismini.agent

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel

private val Magenta    = Color(0xFFE040FB)
private val DimMagenta = Color(0xFF4A0070)
private val BgDark     = Color(0xFF080810)
private val BgCard     = Color(0xFF0F0A1A)
private val Green      = Color(0xFF00FF88)
private val Red        = Color(0xFFFF4466)
private val Yellow     = Color(0xFFFFCC00)
private val White70    = Color(0xB3FFFFFF)
private val White40    = Color(0x66FFFFFF)

@Composable
fun HealDashboardScreen(
    onBack: () -> Unit,
    vm:     HealDashboardViewModel = viewModel()
) {
    val state     by vm.state.collectAsState()
    val listState = rememberLazyListState()

    LaunchedEffect(state.logs.size) {
        if (state.logs.isNotEmpty()) listState.animateScrollToItem(state.logs.lastIndex)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(BgDark, Color(0xFF05020F))))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(12.dp))

            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, "Back", tint = Magenta)
                }
                Text(
                    "SELF-HEAL",
                    fontSize      = 18.sp,
                    fontWeight    = FontWeight.Light,
                    letterSpacing = 4.sp,
                    color         = Magenta,
                    fontFamily    = FontFamily.Monospace
                )
                Spacer(Modifier.weight(1f))
                HealStatusDot(online = state.serverOnline, healing = state.healing)
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = { vm.checkServer() }) {
                    Icon(Icons.Default.Refresh, "Refresh", tint = White70, modifier = Modifier.size(18.dp))
                }
            }

            if (state.healing || state.done || state.error != null) {
                HealStatusBar(state)
                Spacer(Modifier.height(8.dp))
            }

            HealInputSection(
                state   = state,
                onTask  = vm::onTaskInput,
                onStart = vm::startHeal,
            )

            Spacer(Modifier.height(8.dp))

            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "HEAL LOG",
                    fontSize      = 11.sp,
                    letterSpacing = 3.sp,
                    color         = DimMagenta,
                    fontFamily    = FontFamily.Monospace
                )
                Spacer(Modifier.weight(1f))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.VolumeUp, null,
                        tint     = if (state.ttsEnabled) Magenta else White40,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(2.dp))
                    Switch(
                        checked         = state.ttsEnabled,
                        onCheckedChange = vm::onTtsToggle,
                        modifier        = Modifier.height(20.dp),
                        colors          = SwitchDefaults.colors(
                            checkedThumbColor   = Magenta,
                            checkedTrackColor   = DimMagenta,
                            uncheckedThumbColor = White40,
                            uncheckedTrackColor = BgCard
                        )
                    )
                }
                if (state.logs.isNotEmpty()) {
                    Spacer(Modifier.width(8.dp))
                    Text("${state.logs.size} lines", fontSize = 10.sp, color = White40, fontFamily = FontFamily.Monospace)
                    Spacer(Modifier.width(8.dp))
                    TextButton(
                        onClick        = vm::clearLogs,
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                    ) {
                        Text("Clear", fontSize = 11.sp, color = White40)
                    }
                }
            }

            Spacer(Modifier.height(4.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(BgCard)
                    .border(0.5.dp, DimMagenta.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                    .padding(8.dp)
            ) {
                if (state.logs.isEmpty()) {
                    Text(
                        "No logs yet. Describe the fix and tap Heal.",
                        color      = White40,
                        fontSize   = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier   = Modifier.align(Alignment.Center)
                    )
                } else {
                    LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        items(state.logs, key = { it.id }) { log ->
                            LogLineRow(log)
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun HealStatusBar(state: HealDashboardState) {
    val bg = when {
        state.error != null -> Red.copy(alpha = 0.15f)
        state.done          -> Green.copy(alpha = 0.12f)
        state.healing       -> Magenta.copy(alpha = 0.08f)
        else                -> BgCard
    }
    val text = when {
        state.error != null -> "✗ Error: ${state.error}"
        state.done          -> "✓ Patches applied"
        state.healing       -> "Healing in progress — DevTools is working…"
        else                -> ""
    }
    val textColor = when {
        state.error != null -> Red
        state.done          -> Green
        else                -> Magenta
    }
    if (text.isBlank()) return
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (state.healing) {
            CircularProgressIndicator(modifier = Modifier.size(12.dp), color = Magenta, strokeWidth = 1.5.dp)
            Spacer(Modifier.width(8.dp))
        }
        Text(text, color = textColor, fontSize = 12.sp, fontFamily = FontFamily.Monospace, maxLines = 1)
    }
}

@Composable
private fun HealInputSection(
    state:   HealDashboardState,
    onTask:  (String) -> Unit,
    onStart: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(BgCard)
            .border(0.5.dp, DimMagenta.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
            .padding(12.dp)
    ) {
        OutlinedTextField(
            value           = state.taskInput,
            onValueChange   = onTask,
            label           = { Text("Describe the fix", color = White40, fontSize = 12.sp) },
            placeholder     = { Text("Fix Gemini Send button missing from screen elements…", color = White40, fontSize = 12.sp, fontFamily = FontFamily.Monospace) },
            modifier        = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            minLines        = 2,
            maxLines        = 5,
            colors          = OutlinedTextFieldDefaults.colors(
                focusedBorderColor   = Magenta,
                unfocusedBorderColor = DimMagenta.copy(alpha = 0.5f),
                cursorColor          = Magenta,
                focusedLabelColor    = Magenta,
            ),
            textStyle = LocalTextStyle.current.copy(
                color      = White70,
                fontSize   = 13.sp,
                fontFamily = FontFamily.Monospace
            )
        )

        Spacer(Modifier.height(10.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            Button(
                onClick  = onStart,
                enabled  = state.taskInput.trim().isNotEmpty() && state.serverOnline && !state.healing,
                colors   = ButtonDefaults.buttonColors(
                    containerColor         = Magenta,
                    contentColor           = BgDark,
                    disabledContainerColor = DimMagenta.copy(alpha = 0.3f),
                    disabledContentColor   = White40
                ),
                modifier = Modifier.height(38.dp)
            ) {
                Icon(Icons.Default.Build, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(if (state.healing) "Healing…" else "Heal", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }

        if (!state.serverOnline) {
            Spacer(Modifier.height(6.dp))
            Text(
                "⚠ Agent server offline — run start_jarvis_services.sh on Phone A",
                color = Yellow, fontSize = 10.sp, fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
private fun HealStatusDot(online: Boolean, healing: Boolean) {
    val pulse = rememberInfiniteTransition(label = "pulse")
    val alpha by pulse.animateFloat(
        initialValue  = 0.4f, targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(900), repeatMode = RepeatMode.Reverse),
        label = "dotAlpha"
    )
    val color = when {
        !online -> Red
        healing -> Magenta.copy(alpha = alpha)
        else    -> Green
    }
    Box(Modifier.size(10.dp).clip(CircleShape).background(color))
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
ROOT       = Path(__file__).parent / "workflows" / "test" / "jarvis-agent"
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
