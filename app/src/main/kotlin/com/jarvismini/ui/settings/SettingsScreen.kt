package com.jarvismini.ui.settings

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.jarvismini.core.JarvisPrefs
import com.jarvismini.security.SecurityManager
import com.jarvismini.server.LanServerService
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.net.NetworkInterface
import java.text.SimpleDateFormat
import java.util.*

// Voice map: display name -> voice ID
private val VOICE_MAP = linkedMapOf(
    "Jarvis"    to "lNiTyQyEeDoFcsYb4RUT",
    "Jarvis 2"  to "lNiTyQyEeDoFcsYb4RUT",  // user can update second ID later
    "Adam"      to "pNInz6obpgDQGcFmaJgB",
    "Sarah"     to "EXAVITQu4vr4xnSDxMaL",
    "Daniel"    to "onwK4e9ZLuTAKqWW03F9",
    "Charlotte" to "XB0fDUnXU5powFXDhCwa",
    "Callum"    to "N2lVS1w4EtoT3dr4eOWO",
)

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context  = LocalContext.current
    val prefs    = remember { context.getSharedPreferences("jarvis_llm", Context.MODE_PRIVATE) }
    val securityManager = remember { SecurityManager.getInstance(context) }
    val scope    = rememberCoroutineScope()

    // LLM / Model state
    var selectedModel    by remember { mutableStateOf(prefs.getString("selected_model", "gpt-4o-mini") ?: "gpt-4o-mini") }
    var showModelDialog  by remember { mutableStateOf(false) }
    var cloudProvider    by remember { mutableStateOf(prefs.getString("cloud_provider", "OPENAI") ?: "OPENAI") }
    var apiKey           by remember { mutableStateOf(prefs.getString("cloud_api_key", "") ?: "") }
    var showApiKeyDialog by remember { mutableStateOf(false) }
    var showApiKey       by remember { mutableStateOf(false) }

    // LAN server state
    var isServerRunning       by remember { mutableStateOf(false) }
    var serverIpAddress       by remember { mutableStateOf(getLocalIpAddress()) }
    var pairedDevices         by remember { mutableStateOf(securityManager.getPairedDevices()) }
    var isPairingMode         by remember { mutableStateOf(false) }
    var pairingTimeLeft       by remember { mutableStateOf(0) }
    var showPairingDialog     by remember { mutableStateOf(false) }
    var showDeviceListDialog  by remember { mutableStateOf(false) }
    var showClientPairingScreen by remember { mutableStateOf(false) }

    // Agent / TTS settings
    var agentHost        by remember { mutableStateOf(JarvisPrefs.getString("agent_host") ?: "192.168.29.48") }
    var elevenApiKeys    by remember { mutableStateOf(prefs.getString("elevenlabs_keys", "") ?: "") }
    var showElevenDialog by remember { mutableStateOf(false) }
    // Default voice is Jarvis
    var selectedVoice    by remember { mutableStateOf(prefs.getString("elevenlabs_voice", "Jarvis") ?: "Jarvis") }
    var showVoiceDialog  by remember { mutableStateOf(false) }
    var ttsEnabled       by remember { mutableStateOf(prefs.getBoolean("tts_enabled", true)) }

    LaunchedEffect(isPairingMode) {
        while (isPairingMode) {
            if (securityManager.isPairingEnabled()) {
                pairingTimeLeft = ((securityManager.getPairingExpiry() - System.currentTimeMillis()) / 1000).toInt()
                if (pairingTimeLeft <= 0) isPairingMode = false
            } else {
                isPairingMode = false
            }
            delay(1000)
        }
    }

    if (showClientPairingScreen) {
        ClientPairingScreen(onBack = { showClientPairingScreen = false })
        return
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.radialGradient(listOf(Color.Black, Color(0xFF001520), Color.Black)))
    ) {
        Column(Modifier.fillMaxSize().padding(20.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, "Back", tint = Color(0xFF00E0FF))
                }
                Text("SETTINGS", fontSize = 24.sp, fontWeight = FontWeight.Light,
                    letterSpacing = 4.sp, color = Color(0xFF00E0FF))
            }

            Spacer(Modifier.height(20.dp))

            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {

                // ── LLM ──────────────────────────────────────────────────
                item { SectionLabel("LLM CONFIGURATION") }
                item {
                    SettingsTile("AI Model", getModelDisplayName(selectedModel),
                        Icons.Default.Psychology) { showModelDialog = true }
                }
                item {
                    SettingsTile("Cloud API Provider", cloudProvider, Icons.Default.Cloud) {
                        cloudProvider = if (cloudProvider == "OPENAI") "ANTHROPIC" else "OPENAI"
                        prefs.edit().putString("cloud_provider", cloudProvider).apply()
                    }
                }
                item {
                    SettingsTile("API Key", if (apiKey.isEmpty()) "Not Configured" else "••••••••",
                        Icons.Default.Key) { showApiKeyDialog = true }
                }

                // ── LAN Server ────────────────────────────────────────────
                item { SectionLabel("LAN SERVER") }
                item {
                    SettingsTile("Server IP", serverIpAddress, Icons.Default.Wifi) {}
                }
                item {
                    SettingsTile(
                        "Paired Devices",
                        "${pairedDevices.size} device(s) paired",
                        Icons.Default.Devices
                    ) { showDeviceListDialog = true }
                }

                // ── Agent ─────────────────────────────────────────────────
                item { SectionLabel("AGENT SETTINGS") }
                item {
                    SettingsTileEditable(
                        title  = "Phone A Host",
                        value  = agentHost,
                        icon   = Icons.Default.PhoneAndroid,
                        hint   = "192.168.29.48",
                        onSave = { v ->
                            agentHost = v
                            JarvisPrefs.putString("agent_host", v)
                        }
                    )
                }

                // ── Voice / TTS ────────────────────────────────────────────
                item { SectionLabel("VOICE — ELEVENLABS") }
                item {
                    SettingsTileToggle(
                        title    = "Voice Output",
                        checked  = ttsEnabled,
                        icon     = Icons.Default.VolumeUp,
                        onToggle = { v ->
                            ttsEnabled = v
                            prefs.edit().putBoolean("tts_enabled", v).apply()
                        }
                    )
                }
                item {
                    SettingsTile(
                        "ElevenLabs API Keys",
                        if (elevenApiKeys.isBlank()) "Not configured"
                        else "${elevenApiKeys.lines().filter { it.isNotBlank() }.size} key(s) — one per line",
                        Icons.Default.Key
                    ) { showElevenDialog = true }
                }
                item {
                    SettingsTile("Voice", selectedVoice, Icons.Default.RecordVoiceOver) {
                        showVoiceDialog = true
                    }
                }
            }
        }
    }

    // ── Dialogs ───────────────────────────────────────────────────────────────

    if (showModelDialog) {
        ModelSelectorDialog(
            selected  = selectedModel,
            onSelect  = { m ->
                selectedModel = m
                prefs.edit().putString("selected_model", m).apply()
                showModelDialog = false
            },
            onDismiss = { showModelDialog = false }
        )
    }

    if (showApiKeyDialog) {
        ApiKeyDialog(
            current      = apiKey,
            showKey      = showApiKey,
            onToggleShow = { showApiKey = !showApiKey },
            onSave       = { k ->
                apiKey = k
                prefs.edit().putString("cloud_api_key", k).apply()
                showApiKeyDialog = false
            },
            onDismiss = { showApiKeyDialog = false }
        )
    }

    if (showElevenDialog) {
        MultiLineDialog(
            title     = "ElevenLabs API Keys",
            hint      = "One key per line — all keys rotate on quota exhaustion",
            current   = elevenApiKeys,
            onSave    = { v ->
                elevenApiKeys = v
                prefs.edit().putString("elevenlabs_keys", v).apply()
                showElevenDialog = false
            },
            onDismiss = { showElevenDialog = false }
        )
    }

    if (showVoiceDialog) {
        VoiceSelectorDialog(
            selected  = selectedVoice,
            voices    = VOICE_MAP.keys.toList(),
            onSelect  = { v ->
                selectedVoice = v
                prefs.edit().putString("elevenlabs_voice", v).apply()
                // Also persist the voice ID so the server can use it directly
                VOICE_MAP[v]?.let { id ->
                    prefs.edit().putString("elevenlabs_voice_id", id).apply()
                }
                showVoiceDialog = false
            },
            onDismiss = { showVoiceDialog = false }
        )
    }

    if (showDeviceListDialog) {
        DeviceListDialog(
            devices   = pairedDevices,
            onDismiss = { showDeviceListDialog = false }
        )
    }
}

// ─── Composable helpers ───────────────────────────────────────────────────────

@Composable
private fun SectionLabel(text: String) {
    Text(text, fontSize = 12.sp, letterSpacing = 2.sp,
        color = Color(0xFF00E0FF), fontFamily = FontFamily.Monospace,
        modifier = Modifier.padding(bottom = 4.dp, top = 8.dp))
}

@Composable
private fun SettingsTile(
    title: String, subtitle: String, icon: ImageVector, onClick: () -> Unit
) {
    Surface(
        onClick  = onClick,
        shape    = RoundedCornerShape(10.dp),
        color    = Color(0xFF0A1A22),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = Color(0xFF00E0FF), modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 13.sp, color = Color.White)
                Text(subtitle, fontSize = 11.sp, color = Color.White.copy(alpha = 0.5f))
            }
            Icon(Icons.Default.ChevronRight, null,
                tint = Color.White.copy(alpha = 0.3f), modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun SettingsTileToggle(
    title: String, checked: Boolean, icon: ImageVector, onToggle: (Boolean) -> Unit
) {
    Surface(
        shape    = RoundedCornerShape(10.dp),
        color    = Color(0xFF0A1A22),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = Color(0xFF00E0FF), modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Text(title, fontSize = 13.sp, color = Color.White, modifier = Modifier.weight(1f))
            Switch(
                checked         = checked,
                onCheckedChange = onToggle,
                colors          = SwitchDefaults.colors(
                    checkedThumbColor = Color(0xFF00E0FF),
                    checkedTrackColor = Color(0xFF00E0FF).copy(alpha = 0.3f),
                )
            )
        }
    }
}

@Composable
private fun SettingsTileEditable(
    title: String, value: String, icon: ImageVector,
    hint: String = "", onSave: (String) -> Unit
) {
    var editing by remember { mutableStateOf(false) }
    var text    by remember { mutableStateOf(value) }

    Surface(
        onClick  = { editing = true },
        shape    = RoundedCornerShape(10.dp),
        color    = Color(0xFF0A1A22),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = Color(0xFF00E0FF), modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            if (editing) {
                OutlinedTextField(
                    value         = text,
                    onValueChange = { text = it },
                    singleLine    = true,
                    modifier      = Modifier.weight(1f),
                    colors        = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor   = Color(0xFF00E0FF),
                        unfocusedBorderColor = Color(0xFF005566),
                        cursorColor          = Color(0xFF00E0FF),
                    ),
                    textStyle = LocalTextStyle.current.copy(
                        color      = Color.White,
                        fontSize   = 13.sp,
                        fontFamily = FontFamily.Monospace
                    )
                )
                TextButton(onClick = { onSave(text); editing = false }) {
                    Text("Save", color = Color(0xFF00E0FF))
                }
            } else {
                Column(Modifier.weight(1f)) {
                    Text(title, fontSize = 13.sp, color = Color.White)
                    Text(value.ifBlank { hint }, fontSize = 11.sp,
                        color = if (value.isBlank()) Color.White.copy(0.3f) else Color.White.copy(0.5f))
                }
                Icon(Icons.Default.Edit, null,
                    tint = Color.White.copy(0.3f), modifier = Modifier.size(16.dp))
            }
        }
    }
}

// ─── Dialogs ─────────────────────────────────────────────────────────────────

@Composable
private fun ModelSelectorDialog(selected: String, onSelect: (String) -> Unit, onDismiss: () -> Unit) {
    val models = listOf(
        "gpt-4o-mini"                       to "GPT-4.1 Mini",
        "gpt-4o"                            to "GPT-4.1",
        "o1-mini"                           to "GPT-O1 Mini",
        "deepseek/deepseek-r1:free"         to "DeepSeek R1 Free",
        "google/gemini-2.0-flash-lite:free" to "Gemini 2.0 Flash Lite",
        "local"                             to "Local LLM (Termux)",
    )
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .background(Color(0xFF0A1A22), RoundedCornerShape(14.dp))
                .padding(20.dp)
        ) {
            Text("Select AI Model", fontSize = 16.sp, color = Color(0xFF00E0FF),
                fontFamily = FontFamily.Monospace)
            Spacer(Modifier.height(12.dp))
            models.forEach { (id, name) ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(id) }
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = selected == id,
                        onClick  = { onSelect(id) },
                        colors   = RadioButtonDefaults.colors(selectedColor = Color(0xFF00E0FF))
                    )
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(name, fontSize = 13.sp, color = Color.White)
                        Text(id, fontSize = 10.sp, color = Color.White.copy(0.4f),
                            fontFamily = FontFamily.Monospace)
                    }
                }
            }
        }
    }
}

@Composable
private fun ApiKeyDialog(
    current: String, showKey: Boolean,
    onToggleShow: () -> Unit, onSave: (String) -> Unit, onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(current) }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .background(Color(0xFF0A1A22), RoundedCornerShape(14.dp))
                .padding(20.dp)
        ) {
            Text("API Key", fontSize = 16.sp, color = Color(0xFF00E0FF))
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value                = text,
                onValueChange        = { text = it },
                singleLine           = true,
                visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon         = {
                    IconButton(onClick = onToggleShow) {
                        Icon(
                            if (showKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            null, tint = Color(0xFF00E0FF)
                        )
                    }
                },
                modifier  = Modifier.fillMaxWidth(),
                colors    = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor   = Color(0xFF00E0FF),
                    unfocusedBorderColor = Color(0xFF005566),
                    cursorColor          = Color(0xFF00E0FF),
                ),
                textStyle = LocalTextStyle.current.copy(
                    color = Color.White, fontFamily = FontFamily.Monospace
                )
            )
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("Cancel", color = Color.White.copy(0.5f)) }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = { onSave(text) },
                    colors  = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF00E0FF), contentColor = Color.Black)
                ) { Text("Save") }
            }
        }
    }
}

@Composable
private fun MultiLineDialog(
    title: String, hint: String, current: String,
    onSave: (String) -> Unit, onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(current) }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .background(Color(0xFF0A1A22), RoundedCornerShape(14.dp))
                .padding(20.dp)
        ) {
            Text(title, fontSize = 16.sp, color = Color(0xFF00E0FF))
            Spacer(Modifier.height(8.dp))
            Text(hint, fontSize = 11.sp, color = Color.White.copy(0.4f),
                fontFamily = FontFamily.Monospace)
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value         = text,
                onValueChange = { text = it },
                minLines      = 4,
                maxLines      = 8,
                modifier      = Modifier.fillMaxWidth(),
                colors        = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor   = Color(0xFF00E0FF),
                    unfocusedBorderColor = Color(0xFF005566),
                    cursorColor          = Color(0xFF00E0FF),
                ),
                textStyle = LocalTextStyle.current.copy(
                    color = Color.White, fontSize = 12.sp, fontFamily = FontFamily.Monospace
                )
            )
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("Cancel", color = Color.White.copy(0.5f)) }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = { onSave(text) },
                    colors  = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF00E0FF), contentColor = Color.Black)
                ) { Text("Save") }
            }
        }
    }
}

@Composable
private fun VoiceSelectorDialog(
    selected:  String,
    voices:    List<String>,
    onSelect:  (String) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .background(Color(0xFF0A1A22), RoundedCornerShape(14.dp))
                .padding(20.dp)
        ) {
            Text("Select Voice", fontSize = 16.sp, color = Color(0xFF00E0FF))
            Spacer(Modifier.height(12.dp))
            voices.forEach { v ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(v) }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = selected == v,
                        onClick  = { onSelect(v) },
                        colors   = RadioButtonDefaults.colors(selectedColor = Color(0xFF00E0FF))
                    )
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(v, fontSize = 13.sp, color = Color.White)
                        Text(
                            VOICE_MAP[v] ?: "",
                            fontSize   = 10.sp,
                            color      = Color.White.copy(0.35f),
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DeviceListDialog(
    devices: List<com.jarvismini.security.TrustedDevice>, onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .background(Color(0xFF0A1A22), RoundedCornerShape(14.dp))
                .padding(20.dp)
        ) {
            Text("Paired Devices", fontSize = 16.sp, color = Color(0xFF00E0FF))
            Spacer(Modifier.height(12.dp))
            if (devices.isEmpty()) {
                Text("No paired devices", fontSize = 13.sp, color = Color.White.copy(0.5f))
            } else {
                devices.forEach { d ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Devices, null,
                            tint = Color(0xFF00E0FF), modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(d.name, fontSize = 13.sp, color = Color.White)
                            Text(d.ipAddress, fontSize = 11.sp,
                                color = Color.White.copy(0.5f), fontFamily = FontFamily.Monospace)
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                Text("Close", color = Color(0xFF00E0FF))
            }
        }
    }
}

private fun getModelDisplayName(model: String) = when (model) {
    "gpt-4o-mini"                       -> "GPT-4.1 Mini"
    "gpt-4o"                            -> "GPT-4.1"
    "o1-mini"                           -> "GPT-O1 Mini"
    "deepseek/deepseek-r1:free"         -> "DeepSeek R1 Free"
    "google/gemini-2.0-flash-lite:free" -> "Gemini 2.0 Flash"
    "local"                             -> "Local LLM"
    else                                -> model
}

private fun getLocalIpAddress(): String {
    return try {
        NetworkInterface.getNetworkInterfaces()?.toList()
            ?.flatMap { it.inetAddresses.toList() }
            ?.firstOrNull { !it.isLoopbackAddress && it.hostAddress?.contains(':') == false }
            ?.hostAddress ?: "Unknown"
    } catch (e: Exception) {
        "Unknown"
    }
}


//===== TERMUXFILE: elevenlabs_tts.py =====
#!/usr/bin/env python3
"""
elevenlabs_tts.py  —  ElevenLabs TTS with multi-key rotation
=============================================================
Runs on Phone A (Termux).
Called by Android app via HTTP (port 8892) OR directly by agent_server
when Jarvis wants to speak a result.

Multiple API keys rotate on quota exhaustion (429 / 401).
Audio played via termux-media-player or mpv.

Endpoint:
  POST /speak  { "text": "...", "voice_id": "..." }
  GET  /voices List available voices
  GET  /health
"""

import os
import json
import queue
import shutil
import tempfile
import threading
import subprocess
import urllib.request
import urllib.error
from http.server import HTTPServer, BaseHTTPRequestHandler

PORT = 8892

# ---------------------------------------------------------------------------
# Key pool — supports multiple keys, rotates on quota exhaustion
# ---------------------------------------------------------------------------
# Keys loaded from env or config file at ~/workflows/.elevenlabs_keys
# Format in file: one key per line

_KEY_FILE = os.path.expanduser("~/workflows/.elevenlabs_keys")

def _load_keys() -> list:
    keys = []
    # From env (comma-separated or single)
    env_keys = os.environ.get("ELEVENLABS_API_KEY", "").strip()
    for k in env_keys.split(","):
        k = k.strip()
        if k and k not in keys:
            keys.append(k)
    # From file (one per line)
    if os.path.isfile(_KEY_FILE):
        with open(_KEY_FILE) as f:
            for line in f:
                k = line.strip()
                if k and not k.startswith("#") and k not in keys:
                    keys.append(k)
    return keys

_keys: list = []
_key_index = 0
_key_lock  = threading.Lock()

def _get_key() -> str:
    global _keys
    if not _keys:
        _keys = _load_keys()
    if not _keys:
        raise RuntimeError(
            "No ElevenLabs API keys configured.\n"
            f"Add keys to {_KEY_FILE} (one per line)\n"
            "Or set ELEVENLABS_API_KEY env var (comma-separated for multiple keys)"
        )
    with _key_lock:
        return _keys[_key_index % len(_keys)]

def _rotate_key():
    global _key_index
    with _key_lock:
        _key_index += 1
        idx = _key_index % len(_keys) if _keys else 0
    print(f"[tts] Rotated to key index {idx} of {len(_keys)}")

# ---------------------------------------------------------------------------
# Voice configuration — Jarvis custom voice as default
# ---------------------------------------------------------------------------

# Voice ID map: name -> id
VOICE_MAP = {
    "Jarvis":    "lNiTyQyEeDoFcsYb4RUT",   # Custom Jarvis voice
    "Jarvis 2":  "lNiTyQyEeDoFcsYb4RUT",   # Same until second voice ID is known
    "Adam":      "pNInz6obpgDQGcFmaJgB",
    "Sarah":     "EXAVITQu4vr4xnSDxMaL",
    "Daniel":    "onwK4e9ZLuTAKqWW03F9",
    "Charlotte": "XB0fDUnXU5powFXDhCwa",
    "Callum":    "N2lVS1w4EtoT3dr4eOWO",
}

DEFAULT_VOICE   = "lNiTyQyEeDoFcsYb4RUT"   # Jarvis custom voice
DEFAULT_MODEL   = "eleven_turbo_v2"
ELEVENLABS_BASE = "https://api.elevenlabs.io/v1"

# ---------------------------------------------------------------------------
# TTS core
# ---------------------------------------------------------------------------

def _tts_request(text: str, voice_id: str) -> bytes:
    """Returns raw MP3 bytes. Rotates key on 429/401."""
    global _keys
    if not _keys:
        _keys = _load_keys()
    max_attempts = max(len(_keys), 1) + 1
    for attempt in range(max_attempts):
        key = _get_key()
        url = f"{ELEVENLABS_BASE}/text-to-speech/{voice_id}"
        payload = json.dumps({
            "text": text,
            "model_id": DEFAULT_MODEL,
            "voice_settings": {
                "stability":        0.5,
                "similarity_boost": 0.75,
                "style":            0.2,
                "use_speaker_boost": True
            }
        }).encode()
        req = urllib.request.Request(
            url,
            data    = payload,
            headers = {
                "xi-api-key":   key,
                "Content-Type": "application/json",
                "Accept":       "audio/mpeg",
            },
            method = "POST"
        )
        try:
            with urllib.request.urlopen(req, timeout=30) as resp:
                return resp.read()
        except urllib.error.HTTPError as e:
            if e.code in (429, 401) and len(_keys) > 1:
                print(f"[tts] Key quota/auth error ({e.code}), rotating to next key...")
                _rotate_key()
                continue
            raise
    raise RuntimeError(f"All {len(_keys)} ElevenLabs API key(s) exhausted or failed")


def _play_audio(mp3_bytes: bytes):
    """Play MP3 bytes using available player."""
    with tempfile.NamedTemporaryFile(suffix=".mp3", delete=False) as f:
        f.write(mp3_bytes)
        path = f.name
    try:
        if shutil.which("termux-media-player"):
            subprocess.run(["termux-media-player", "play", path],
                           check=True, timeout=60)
        elif shutil.which("mpv"):
            subprocess.run(["mpv", "--no-video", path],
                           check=True, timeout=60)
        elif shutil.which("ffplay"):
            subprocess.run(["ffplay", "-nodisp", "-autoexit", path],
                           check=True, timeout=60)
        else:
            print(f"[tts] No audio player found. Audio saved: {path}")
            return
    finally:
        try:
            os.unlink(path)
        except Exception:
            pass


# Speech queue — prevents concurrent TTS calls
_speech_queue: "queue.Queue[tuple]" = queue.Queue()

def _speech_worker():
    while True:
        text, voice_id = _speech_queue.get()
        try:
            print(f"[tts] Speaking ({voice_id}): {text[:60]!r}")
            mp3 = _tts_request(text, voice_id)
            _play_audio(mp3)
        except Exception as e:
            print(f"[tts] Error: {e}")
        finally:
            _speech_queue.task_done()

_worker = threading.Thread(target=_speech_worker, daemon=True)
_worker.start()


def speak(text: str, voice_id: str = DEFAULT_VOICE, block: bool = False):
    """Queue text for speaking. Non-blocking by default."""
    # Resolve voice name to ID if a name was passed instead of an ID
    if voice_id in VOICE_MAP:
        voice_id = VOICE_MAP[voice_id]
    _speech_queue.put((text, voice_id))
    if block:
        _speech_queue.join()


# ---------------------------------------------------------------------------
# HTTP handler
# ---------------------------------------------------------------------------

class TTSHandler(BaseHTTPRequestHandler):

    def log_message(self, fmt, *args):
        pass

    def _json(self, code: int, data: dict):
        body = json.dumps(data).encode()
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Access-Control-Allow-Origin", "*")
        self.end_headers()
        self.wfile.write(body)

    def _body(self) -> dict:
        n = int(self.headers.get("Content-Length", 0))
        return json.loads(self.rfile.read(n)) if n else {}

    def do_GET(self):
        if self.path == "/health":
            loaded = _load_keys()
            self._json(200, {"ok": True, "keys": len(loaded),
                              "default_voice": "Jarvis"})
        elif self.path == "/voices":
            self._json(200, {"voices": [
                {"id": v_id, "name": name}
                for name, v_id in VOICE_MAP.items()
            ]})
        else:
            self._json(404, {"error": "not found"})

    def do_POST(self):
        if self.path == "/speak":
            b = self._body()
            text     = b.get("text", "").strip()
            voice_id = b.get("voice_id", DEFAULT_VOICE)
            if not text:
                self._json(400, {"error": "text required"})
                return
            speak(text, voice_id)
            self._json(200, {"ok": True, "queued": text[:60]})
        else:
            self._json(404, {"error": "not found"})

    def do_OPTIONS(self):
        self.send_response(200)
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
        self.send_header("Access-Control-Allow-Headers", "Content-Type")
        self.end_headers()


if __name__ == "__main__":
    _keys = _load_keys()
    print(f"[tts] Loaded {len(_keys)} API key(s)")
    if not _keys:
        print(f"[tts] WARNING: No keys found. Add to {_KEY_FILE} (one per line)")
    print(f"[tts] Default voice: Jarvis ({DEFAULT_VOICE})")
    server = HTTPServer(("0.0.0.0", PORT), TTSHandler)
    print(f"[tts] Listening on 0.0.0.0:{PORT}")
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("[tts] Stopped")
