package com.jarvismini.ui.settings

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.jarvismini.core.JarvisPrefs
import com.jarvismini.security.SecurityManager
import com.jarvismini.ui.theme.GlassSurface
import com.jarvismini.ui.theme.JarvisBackground
import com.jarvismini.ui.theme.JarvisBlack
import com.jarvismini.ui.theme.JarvisCyan
import com.jarvismini.ui.theme.JarvisOnline
import com.jarvismini.ui.theme.JarvisRadius
import com.jarvismini.ui.theme.JarvisTextPrimary
import com.jarvismini.ui.theme.JarvisTextSecondary
import com.jarvismini.ui.theme.pressScale
import com.jarvismini.ui.theme.staggerEnter
import com.jarvismini.voice.WakeWordService
import kotlinx.coroutines.delay
import java.net.NetworkInterface

// Voice map: display name -> voice ID
private val VOICE_MAP = linkedMapOf(
    "Jarvis"    to "lNiTyQyEeDoFcsYb4RUT",
    "Jarvis 2"  to "lNiTyQyEeDoFcsYb4RUT",
    "Adam"      to "pNInz6obpgDQGcFmaJgB",
    "Sarah"     to "EXAVITQu4vr4xnSDxMaL",
    "Daniel"    to "onwK4e9ZLuTAKqWW03F9",
    "Charlotte" to "XB0fDUnXU5powFXDhCwa",
    "Callum"    to "N2lVS1w4EtoT3dr4eOWO",
)

// Flat surfaces derived from the theme (settings are text-heavy: solid rows, glass only on the summary).
private val RowSurface = JarvisCyan.copy(alpha = 0.06f).compositeOver(JarvisBackground)
private val DialogSurface = JarvisCyan.copy(alpha = 0.09f).compositeOver(JarvisBackground)
private val Hairline = JarvisCyan.copy(alpha = 0.18f)

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context  = LocalContext.current
    val prefs    = remember { context.getSharedPreferences("jarvis_llm", Context.MODE_PRIVATE) }
    val securityManager = remember { SecurityManager.getInstance(context) }

    // LLM / Model state
    var selectedModel    by remember { mutableStateOf(prefs.getString("selected_model", "gpt-4o-mini") ?: "gpt-4o-mini") }
    var showModelDialog  by remember { mutableStateOf(false) }
    var cloudProvider    by remember { mutableStateOf(prefs.getString("cloud_provider", "OPENAI") ?: "OPENAI") }
    var apiKey           by remember { mutableStateOf(prefs.getString("cloud_api_key", "") ?: "") }
    var showApiKeyDialog by remember { mutableStateOf(false) }
    var showApiKey       by remember { mutableStateOf(false) }

    // LAN server state
    val serverIpAddress         by remember { mutableStateOf(getLocalIpAddress()) }
    var pairedDevices           by remember { mutableStateOf(securityManager.getPairedDevices()) }
    var isPairingMode           by remember { mutableStateOf(false) }
    var pairingTimeLeft         by remember { mutableStateOf(0) }
    var showPairingDialog       by remember { mutableStateOf(false) }
    var showDeviceListDialog    by remember { mutableStateOf(false) }
    var showClientPairingScreen by remember { mutableStateOf(false) }

    // Agent / TTS settings
    var agentHost        by remember { mutableStateOf(JarvisPrefs.getString("agent_host") ?: "192.168.29.48") }
    var elevenApiKeys    by remember { mutableStateOf(prefs.getString("elevenlabs_keys", "") ?: "") }
    var showElevenDialog by remember { mutableStateOf(false) }
    var selectedVoice    by remember { mutableStateOf(prefs.getString("elevenlabs_voice", "Jarvis") ?: "Jarvis") }
    var showVoiceDialog  by remember { mutableStateOf(false) }
    var ttsEnabled       by remember { mutableStateOf(prefs.getBoolean("tts_enabled", true)) }
    var bgVoiceEnabled   by remember { mutableStateOf(WakeWordService.isBackgroundVoiceEnabled()) }

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

    // Back from the pairing sub-screen returns to Settings, not all the way Home.
    BackHandler(enabled = showClientPairingScreen) { showClientPairingScreen = false }

    if (showClientPairingScreen) {
        ClientPairingScreen(onBack = { showClientPairingScreen = false })
        return
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.radialGradient(listOf(JarvisBlack, JarvisBackground, JarvisBlack)))
    ) {
        Column(Modifier.fillMaxSize().padding(horizontal = 20.dp).padding(top = 20.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, "Back", tint = JarvisCyan)
                }
                Text(
                    "SETTINGS", fontSize = 24.sp, fontWeight = FontWeight.Light,
                    letterSpacing = 4.sp, color = JarvisCyan,
                    modifier = Modifier.semantics { heading() }
                )
            }

            Spacer(Modifier.height(12.dp))

            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {

                // ── Summary (the one glass surface) ───────────────────────
                ConfigSummary(
                    model     = getModelDisplayName(selectedModel),
                    ip        = serverIpAddress,
                    pairing   = isPairingMode,
                    voiceOut  = ttsEnabled,
                    modifier  = Modifier.staggerEnter(0)
                )

                // ── LLM ──────────────────────────────────────────────────
                SettingsSection("LLM CONFIGURATION", Modifier.staggerEnter(1)) {
                    SettingsRow("AI Model", getModelDisplayName(selectedModel),
                        Icons.Default.Psychology, onClickLabel = "Change model") { showModelDialog = true }
                    RowDivider()
                    SettingsRow("Cloud API Provider", cloudProvider, Icons.Default.Cloud,
                        onClickLabel = "Switch provider") {
                        cloudProvider = if (cloudProvider == "OPENAI") "ANTHROPIC" else "OPENAI"
                        prefs.edit().putString("cloud_provider", cloudProvider).apply()
                    }
                    RowDivider()
                    SettingsRow("API Key", if (apiKey.isEmpty()) "Not configured" else "••••••••",
                        Icons.Default.Key, onClickLabel = "Edit API key") { showApiKeyDialog = true }
                }

                // ── LAN Server ────────────────────────────────────────────
                SettingsSection("LAN SERVER", Modifier.staggerEnter(2)) {
                    SettingsRow("Server IP", serverIpAddress, Icons.Default.Wifi, monoSubtitle = true)
                    RowDivider()
                    SettingsRow(
                        "Pairing Mode",
                        if (isPairingMode) "Active — $pairingTimeLeft s remaining" else "Disabled — tap to enable",
                        if (isPairingMode) Icons.Default.BluetoothSearching else Icons.Default.BluetoothDisabled,
                        onClickLabel = if (isPairingMode) "Disable pairing mode" else "Enable pairing mode"
                    ) {
                        if (isPairingMode) {
                            securityManager.disablePairingMode()
                            isPairingMode = false
                        } else {
                            securityManager.enablePairingMode(60)
                            isPairingMode = true
                            showPairingDialog = true
                        }
                    }
                    RowDivider()
                    SettingsRow(
                        "Paired Devices",
                        "${pairedDevices.size} device(s) paired",
                        Icons.Default.Devices,
                        onClickLabel = "Show paired devices"
                    ) {
                        pairedDevices = securityManager.getPairedDevices()
                        showDeviceListDialog = true
                    }
                    RowDivider()
                    SettingsRow(
                        "Pair As Client",
                        "Connect to another JARVIS device",
                        Icons.Default.DeviceHub,
                        onClickLabel = "Open client pairing"
                    ) { showClientPairingScreen = true }
                }

                // ── Agent ─────────────────────────────────────────────────
                SettingsSection("AGENT SETTINGS", Modifier.staggerEnter(3)) {
                    EditableRow(
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
                SettingsSection("VOICE — ELEVENLABS", Modifier.staggerEnter(4)) {
                    ToggleRow(
                        title    = "Voice Output",
                        subtitle = "Speak replies aloud",
                        checked  = ttsEnabled,
                        icon     = Icons.Default.VolumeUp,
                        onToggle = { v ->
                            ttsEnabled = v
                            prefs.edit().putBoolean("tts_enabled", v).apply()
                        }
                    )
                    RowDivider()
                    ToggleRow(
                        title    = "Background Voice",
                        subtitle = "Always-on microphone",
                        checked  = bgVoiceEnabled,
                        icon     = Icons.Default.Mic,
                        onToggle = { v ->
                            bgVoiceEnabled = v
                            WakeWordService.setBackgroundVoiceEnabled(context, v)
                        }
                    )
                    RowDivider()
                    SettingsRow(
                        "ElevenLabs API Keys",
                        if (elevenApiKeys.isBlank()) "Not configured"
                        else "${elevenApiKeys.lines().filter { it.isNotBlank() }.size} key(s) — one per line",
                        Icons.Default.Key,
                        onClickLabel = "Edit ElevenLabs keys"
                    ) { showElevenDialog = true }
                    RowDivider()
                    SettingsRow("Voice", selectedVoice, Icons.Default.RecordVoiceOver,
                        onClickLabel = "Change voice") {
                        showVoiceDialog = true
                    }
                }

                Spacer(Modifier.height(24.dp))
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
                VOICE_MAP[v]?.let { id ->
                    prefs.edit().putString("elevenlabs_voice_id", id).apply()
                }
                showVoiceDialog = false
            },
            onDismiss = { showVoiceDialog = false }
        )
    }

    if (showPairingDialog) {
        PairingInstructionsDialog(
            ipAddress = serverIpAddress,
            timeLeft  = pairingTimeLeft,
            onDismiss = { showPairingDialog = false }
        )
    }

    if (showDeviceListDialog) {
        DeviceListDialog(
            devices   = pairedDevices,
            onDismiss = { showDeviceListDialog = false }
        )
    }
}

// ─── Layout building blocks ───────────────────────────────────────────────────

/** Glass summary of the live config: one scannable hero instead of five equal tiles. */
@Composable
private fun ConfigSummary(
    model: String, ip: String, pairing: Boolean, voiceOut: Boolean, modifier: Modifier = Modifier
) {
    GlassSurface(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {},
        shape = RoundedCornerShape(JarvisRadius.Large),
        cornerTicks = true
    ) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
            Text("ACTIVE MODEL", fontSize = 11.sp, letterSpacing = 2.sp,
                color = JarvisTextSecondary, fontFamily = FontFamily.Monospace)
            Spacer(Modifier.height(4.dp))
            Text(model, fontSize = 20.sp, fontWeight = FontWeight.Medium, color = JarvisTextPrimary)
            Spacer(Modifier.height(10.dp))
            Text(
                "LAN $ip  ·  PAIRING ${if (pairing) "ON" else "OFF"}  ·  VOICE ${if (voiceOut) "ON" else "OFF"}",
                fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = JarvisTextSecondary
            )
        }
    }
}

/** Heading + one flat card that groups related rows (fewer boxes, clearer hierarchy). */
@Composable
private fun SettingsSection(
    label: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(JarvisRadius.Card)
    Column(modifier.fillMaxWidth()) {
        Text(
            label, fontSize = 12.sp, letterSpacing = 2.sp,
            color = JarvisTextSecondary, fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp).semantics { heading() }
        )
        Column(
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(RowSurface)
                .border(1.dp, Hairline, shape),
            content = content
        )
    }
}

@Composable
private fun RowDivider() {
    Box(Modifier.fillMaxWidth().padding(start = 54.dp).height(1.dp).background(Hairline))
}

/** Navigation / action row. 56dp minimum, whole row is the target; no onClick = info-only row. */
@Composable
private fun SettingsRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    monoSubtitle: Boolean = false,
    onClickLabel: String? = null,
    onClick: (() -> Unit)? = null
) {
    val base = Modifier.fillMaxWidth().heightIn(min = 56.dp)
    val target = if (onClick != null) {
        base.clickable(onClickLabel = onClickLabel, role = Role.Button, onClick = onClick)
    } else {
        base.semantics(mergeDescendants = true) {}
    }
    Row(
        target.padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = JarvisCyan, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, color = JarvisTextPrimary)
            Text(
                subtitle, fontSize = 12.sp, color = JarvisTextSecondary,
                fontFamily = if (monoSubtitle) FontFamily.Monospace else FontFamily.Default
            )
        }
        if (onClick != null) {
            Icon(Icons.Default.ChevronRight, null,
                tint = JarvisTextSecondary, modifier = Modifier.size(20.dp))
        }
    }
}

/** Whole row toggles; the switch is visual only so TalkBack reads one "Switch, on/off" element. */
@Composable
private fun ToggleRow(
    title: String, subtitle: String, checked: Boolean, icon: ImageVector, onToggle: (Boolean) -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onToggle)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = JarvisCyan, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, color = JarvisTextPrimary)
            Text(subtitle, fontSize = 12.sp, color = JarvisTextSecondary)
        }
        Switch(
            checked         = checked,
            onCheckedChange = null,
            colors          = SwitchDefaults.colors(
                checkedThumbColor    = JarvisBlack,
                checkedTrackColor    = JarvisCyan,
                uncheckedThumbColor  = JarvisTextSecondary,
                uncheckedTrackColor  = Hairline,
                uncheckedBorderColor = JarvisTextSecondary.copy(alpha = 0.6f),
            )
        )
    }
}

@Composable
private fun EditableRow(
    title: String, value: String, icon: ImageVector,
    hint: String = "", onSave: (String) -> Unit
) {
    var editing by remember { mutableStateOf(false) }
    var text    by remember { mutableStateOf(value) }
    val focus   = remember { FocusRequester() }

    LaunchedEffect(editing) { if (editing) focus.requestFocus() }

    val rowModifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
    if (editing) {
        Row(
            rowModifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, tint = JarvisCyan, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(14.dp))
            OutlinedTextField(
                value           = text,
                onValueChange   = { text = it },
                label           = { Text(title) },
                singleLine      = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onSave(text); editing = false }),
                modifier        = Modifier.weight(1f).focusRequester(focus),
                colors          = fieldColors(),
                textStyle       = LocalTextStyle.current.copy(
                    color = JarvisTextPrimary, fontSize = 14.sp, fontFamily = FontFamily.Monospace
                )
            )
            TextButton(onClick = { onSave(text); editing = false }) {
                Text("Save", color = JarvisCyan)
            }
        }
    } else {
        Row(
            rowModifier
                .clickable(onClickLabel = "Edit $title", role = Role.Button) {
                    text = value
                    editing = true
                }
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, tint = JarvisCyan, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 14.sp, color = JarvisTextPrimary)
                Text(
                    value.ifBlank { hint }, fontSize = 12.sp,
                    color = JarvisTextSecondary, fontFamily = FontFamily.Monospace
                )
            }
            Icon(Icons.Default.Edit, null,
                tint = JarvisTextSecondary, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor   = JarvisCyan,
    unfocusedBorderColor = JarvisCyan.copy(alpha = 0.5f),   // >= 3:1 against the dialog surface
    cursorColor          = JarvisCyan,
    focusedLabelColor    = JarvisCyan,
    unfocusedLabelColor  = JarvisTextSecondary,
    focusedTextColor     = JarvisTextPrimary,
    unfocusedTextColor   = JarvisTextPrimary,
)

// ─── Dialog building blocks ───────────────────────────────────────────────────

@Composable
private fun DialogCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(JarvisRadius.Large)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(DialogSurface)
            .border(1.dp, Hairline, shape)
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Text(
            title, fontSize = 16.sp, color = JarvisCyan, fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.semantics { heading() }
        )
        Spacer(Modifier.height(12.dp))
        content()
    }
}

/** Whole row selects; the radio is visual only (one TalkBack element, Role.RadioButton). */
@Composable
private fun RadioOption(selected: Boolean, title: String, detail: String, onSelect: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick  = null,
            colors   = RadioButtonDefaults.colors(
                selectedColor   = JarvisCyan,
                unselectedColor = JarvisTextSecondary
            )
        )
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, fontSize = 14.sp, color = JarvisTextPrimary)
            if (detail.isNotEmpty()) {
                Text(detail, fontSize = 11.sp, color = JarvisTextSecondary,
                    fontFamily = FontFamily.Monospace)
            }
        }
    }
}

@Composable
private fun PrimaryButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    Button(
        onClick           = onClick,
        interactionSource = source,
        modifier          = modifier.heightIn(min = 48.dp).pressScale(source),
        colors            = ButtonDefaults.buttonColors(
            containerColor = JarvisCyan, contentColor = JarvisBlack)
    ) { Text(text) }
}

@Composable
private fun DialogActions(onCancel: () -> Unit, onSave: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onCancel, modifier = Modifier.heightIn(min = 48.dp)) {
            Text("Cancel", color = JarvisTextSecondary)
        }
        Spacer(Modifier.width(8.dp))
        PrimaryButton("Save", onClick = onSave)
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
        DialogCard("Select AI Model") {
            models.forEach { (id, name) ->
                RadioOption(selected == id, name, id) { onSelect(id) }
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
        DialogCard("API Key") {
            OutlinedTextField(
                value                = text,
                onValueChange        = { text = it },
                label                = { Text("API key") },
                singleLine           = true,
                visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon         = {
                    IconButton(onClick = onToggleShow) {
                        Icon(
                            if (showKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            if (showKey) "Hide API key" else "Show API key",
                            tint = JarvisCyan
                        )
                    }
                },
                modifier  = Modifier.fillMaxWidth(),
                colors    = fieldColors(),
                textStyle = LocalTextStyle.current.copy(
                    color = JarvisTextPrimary, fontFamily = FontFamily.Monospace
                )
            )
            Spacer(Modifier.height(12.dp))
            DialogActions(onCancel = onDismiss, onSave = { onSave(text) })
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
        DialogCard(title) {
            Text(hint, fontSize = 12.sp, color = JarvisTextSecondary)
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value         = text,
                onValueChange = { text = it },
                label         = { Text("Keys") },
                minLines      = 4,
                maxLines      = 8,
                modifier      = Modifier.fillMaxWidth(),
                colors        = fieldColors(),
                textStyle     = LocalTextStyle.current.copy(
                    color = JarvisTextPrimary, fontSize = 12.sp, fontFamily = FontFamily.Monospace
                )
            )
            Spacer(Modifier.height(12.dp))
            DialogActions(onCancel = onDismiss, onSave = { onSave(text) })
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
        DialogCard("Select Voice") {
            voices.forEach { v ->
                RadioOption(selected == v, v, VOICE_MAP[v] ?: "") { onSelect(v) }
            }
        }
    }
}

@Composable
private fun PairingInstructionsDialog(
    ipAddress: String,
    timeLeft:  Int,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        DialogCard("PAIRING MODE ACTIVE") {
            Text("Time remaining: $timeLeft sec", fontSize = 13.sp,
                color = JarvisOnline, fontFamily = FontFamily.Monospace)
            Spacer(Modifier.height(12.dp))
            Text("Send a POST request to:", fontSize = 12.sp, color = JarvisTextPrimary)
            Spacer(Modifier.height(6.dp))
            CodeBox("http://$ipAddress:8081/api/v1/pair")
            Spacer(Modifier.height(8.dp))
            Text("With JSON body:", fontSize = 12.sp, color = JarvisTextPrimary)
            Spacer(Modifier.height(6.dp))
            CodeBox("{\"device_name\": \"My Device\"}")
            Spacer(Modifier.height(16.dp))
            PrimaryButton("GOT IT", Modifier.fillMaxWidth(), onDismiss)
        }
    }
}

@Composable
private fun CodeBox(text: String) {
    Surface(
        shape = RoundedCornerShape(JarvisRadius.Small),
        color = JarvisBlack.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(text, fontSize = 12.sp, color = JarvisOnline, fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(10.dp))
    }
}

@Composable
private fun DeviceListDialog(
    devices: List<com.jarvismini.security.TrustedDevice>, onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        DialogCard("Paired Devices") {
            if (devices.isEmpty()) {
                Text("No paired devices", fontSize = 14.sp, color = JarvisTextSecondary)
            } else {
                devices.forEach { d ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .semantics(mergeDescendants = true) {}
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Devices, null,
                            tint = JarvisCyan, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(d.name, fontSize = 14.sp, color = JarvisTextPrimary)
                            Text(d.ipAddress, fontSize = 12.sp,
                                color = JarvisTextSecondary, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.End).heightIn(min = 48.dp)
            ) {
                Text("Close", color = JarvisCyan)
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
