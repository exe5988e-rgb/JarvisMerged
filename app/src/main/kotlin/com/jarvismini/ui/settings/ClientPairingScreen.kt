package com.jarvismini.ui.settings

import android.content.Context
import android.util.Log
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvismini.ui.theme.JarvisBackground
import com.jarvismini.ui.theme.JarvisBlack
import com.jarvismini.ui.theme.JarvisCyan
import com.jarvismini.ui.theme.JarvisMotion
import com.jarvismini.ui.theme.JarvisOffline
import com.jarvismini.ui.theme.JarvisOnline
import com.jarvismini.ui.theme.JarvisRadius
import com.jarvismini.ui.theme.JarvisTextPrimary
import com.jarvismini.ui.theme.JarvisTextSecondary
import com.jarvismini.ui.theme.pressScale
import com.jarvismini.ui.theme.rememberMotionEnabled
import com.jarvismini.ui.theme.staggerEnter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL

private val InfoSurface = JarvisCyan.copy(alpha = 0.06f).compositeOver(JarvisBackground)
private val InfoEdge = JarvisCyan.copy(alpha = 0.18f)

@Composable
fun ClientPairingScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var serverIp by remember { mutableStateOf("") }
    var serverPort by remember { mutableStateOf("8081") }
    var deviceName by remember { mutableStateOf("JARVIS-Client-${(1000..9999).random()}") }
    var isPairing by remember { mutableStateOf(false) }
    var pairingResult by remember { mutableStateOf<PairingResult?>(null) }

    val scope = rememberCoroutineScope()
    val motionOn = rememberMotionEnabled()
    val pairSource = remember { MutableInteractionSource() }
    val canPair = !isPairing && serverIp.isNotBlank() && serverPort.isNotBlank() && deviceName.isNotBlank()

    fun startPairing() {
        scope.launch {
            isPairing = true
            pairingResult = null
            try {
                val result = pairWithServer(context, serverIp, serverPort, deviceName)
                pairingResult = result

                // Save pairing data to SharedPreferences
                if (result.success && result.deviceId != null && result.token != null) {
                    val prefs = context.getSharedPreferences("jarvis_lan", Context.MODE_PRIVATE)
                    prefs.edit().apply {
                        putString("server_ip", serverIp)
                        putString("server_port", serverPort)
                        putString("device_id", result.deviceId)
                        putString("auth_token", result.token)
                        putBoolean("is_paired", true)
                        apply()
                    }

                    Log.d("ClientPairing", "Saved pairing data: $serverIp:$serverPort")
                    Log.d("ClientPairing", "Device ID: ${result.deviceId}")
                }
            } catch (e: Exception) {
                pairingResult = PairingResult(
                    success = false,
                    error = "Connection failed: ${e.message}"
                )
            } finally {
                isPairing = false
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.radialGradient(listOf(JarvisBlack, JarvisBackground, JarvisBlack)))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
                .padding(top = 20.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint = JarvisCyan
                    )
                }
                Text(
                    text = "PAIR WITH ANOTHER JARVIS",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Light,
                    letterSpacing = 3.sp,
                    color = JarvisCyan,
                    modifier = Modifier.semantics { heading() }
                )
            }

            Spacer(Modifier.height(12.dp))

            // Scrolls so three fields + result stay reachable with the keyboard open.
            Column(
                modifier = Modifier
                    .weight(1f)
                    .imePadding()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Instructions
                InstructionsCard(Modifier.staggerEnter(0))

                // Input fields
                Column(
                    modifier = Modifier.staggerEnter(1),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = serverIp,
                        onValueChange = { serverIp = it.trim() },
                        label = { Text("Server IP address") },
                        placeholder = { Text("192.168.x.x") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        colors = pairingFieldColors(),
                        leadingIcon = { Icon(Icons.Default.Wifi, null, tint = JarvisCyan) }
                    )

                    OutlinedTextField(
                        value = serverPort,
                        onValueChange = { serverPort = it.filter(Char::isDigit).take(5) },
                        label = { Text("Port") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number, imeAction = ImeAction.Next
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        colors = pairingFieldColors(),
                        leadingIcon = { Icon(Icons.Default.Settings, null, tint = JarvisCyan) }
                    )

                    OutlinedTextField(
                        value = deviceName,
                        onValueChange = { deviceName = it },
                        label = { Text("Device name") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Text, imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(onDone = { if (canPair) startPairing() }),
                        modifier = Modifier.fillMaxWidth(),
                        colors = pairingFieldColors(),
                        leadingIcon = { Icon(Icons.Default.Phone, null, tint = JarvisCyan) }
                    )
                }

                // Pair button
                Button(
                    onClick = { startPairing() },
                    interactionSource = pairSource,
                    modifier = Modifier
                        .staggerEnter(2)
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .pressScale(pairSource),
                    enabled = canPair,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = JarvisCyan,
                        contentColor = JarvisBlack,
                        disabledContainerColor = InfoEdge,
                        disabledContentColor = JarvisTextSecondary
                    ),
                    shape = RoundedCornerShape(JarvisRadius.Card)
                ) {
                    if (isPairing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = JarvisBlack,
                            strokeWidth = 3.dp
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            "PAIRING...",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp
                        )
                    } else {
                        Icon(Icons.Default.Link, null)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            "PAIR DEVICE",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp
                        )
                    }
                }

                // Result (announced by screen readers when it appears)
                Column(
                    Modifier.then(
                        if (motionOn) Modifier.animateContentSize(JarvisMotion.snappy()) else Modifier
                    )
                ) {
                    pairingResult?.let { result -> PairingResultCard(result) }
                }

                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun InstructionsCard(modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(JarvisRadius.Card)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(InfoSurface)
            .border(1.dp, InfoEdge, shape)
            .padding(16.dp)
    ) {
        Text(
            text = "INSTRUCTIONS",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp,
            color = JarvisCyan,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.semantics { heading() }
        )
        Spacer(Modifier.height(10.dp))
        InstructionStep("1", "On the MAIN JARVIS device:")
        Text(
            text = "•  Go to Settings\n•  Start LAN Server\n•  Enable Pairing Mode",
            fontSize = 13.sp,
            lineHeight = 20.sp,
            color = JarvisTextSecondary,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(start = 34.dp, top = 2.dp, bottom = 8.dp)
        )
        InstructionStep("2", "Enter the IP address shown")
        Spacer(Modifier.height(8.dp))
        InstructionStep("3", "Tap PAIR DEVICE")
    }
}

@Composable
private fun InstructionStep(number: String, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = number,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = JarvisCyan,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.width(34.dp)
        )
        Text(text, fontSize = 14.sp, color = JarvisTextPrimary)
    }
}

@Composable
private fun PairingResultCard(result: PairingResult) {
    val accent = if (result.success) JarvisOnline else JarvisOffline
    val shape = RoundedCornerShape(JarvisRadius.Card)
    Column(
        Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }
            .clip(shape)
            .background(accent.copy(alpha = 0.10f).compositeOver(JarvisBackground))
            .border(1.dp, accent.copy(alpha = 0.5f), shape)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (result.success) Icons.Default.CheckCircle else Icons.Default.Error,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(32.dp)
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = if (result.success) "PAIRING SUCCESSFUL" else "PAIRING FAILED",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
                color = accent
            )
        }

        if (result.success) {
            Spacer(Modifier.height(16.dp))
            Text(
                text = "Device ID",
                fontSize = 12.sp,
                color = JarvisTextSecondary,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = result.deviceId ?: "Unknown",
                fontSize = 14.sp,
                color = JarvisCyan,
                fontFamily = FontFamily.Monospace
            )

            Spacer(Modifier.height(12.dp))
            Text(
                text = "Token (stored on this device)",
                fontSize = 12.sp,
                color = JarvisTextSecondary,
                fontFamily = FontFamily.Monospace
            )
            // Never put the full bearer token on screen (screenshots, shoulder surfing, screen readers).
            Text(
                text = maskToken(result.token),
                fontSize = 13.sp,
                color = JarvisOnline,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(JarvisRadius.Small))
                    .background(JarvisBlack)
                    .padding(10.dp)
            )

            Spacer(Modifier.height(12.dp))
            Text(
                text = "This device can now communicate with the main JARVIS device.",
                fontSize = 13.sp,
                color = JarvisTextPrimary
            )
        } else {
            Spacer(Modifier.height(12.dp))
            Text(
                text = result.error ?: "Unknown error",
                fontSize = 13.sp,
                color = accent,
                fontFamily = FontFamily.Monospace
            )

            Spacer(Modifier.height(12.dp))
            Text(
                text = "Troubleshooting:\n•  Ensure pairing mode is enabled on the main device\n•  Check the IP address is correct\n•  Verify both devices are on the same WiFi",
                fontSize = 12.sp,
                color = JarvisTextSecondary,
                fontFamily = FontFamily.Monospace,
                lineHeight = 18.sp
            )
        }
    }
}

private fun maskToken(token: String?): String = when {
    token.isNullOrEmpty() -> "No token"
    token.length <= 10 -> "••••••••"
    else -> token.take(4) + "••••••••" + token.takeLast(4)
}

@Composable
private fun pairingFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = JarvisTextPrimary,
    unfocusedTextColor = JarvisTextPrimary,
    focusedBorderColor = JarvisCyan,
    unfocusedBorderColor = JarvisCyan.copy(alpha = 0.5f),   // >= 3:1 non-text contrast
    cursorColor = JarvisCyan,
    focusedLabelColor = JarvisCyan,
    unfocusedLabelColor = JarvisTextSecondary,
    focusedPlaceholderColor = JarvisTextSecondary.copy(alpha = 0.7f),
    unfocusedPlaceholderColor = JarvisTextSecondary.copy(alpha = 0.7f),
)

data class PairingResult(
    val success: Boolean,
    val deviceId: String? = null,
    val token: String? = null,
    val error: String? = null
)

suspend fun pairWithServer(context: Context, ip: String, port: String, deviceName: String): PairingResult {
    return withContext(Dispatchers.IO) {
        try {
            val url = URL("http://$ip:$port/api/v1/pair")
            val connection = url.openConnection() as HttpURLConnection
            
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            
            val jsonBody = """{"device_name":"$deviceName"}"""
            connection.outputStream.use { it.write(jsonBody.toByteArray()) }
            
            val responseCode = connection.responseCode
            val responseBody = if (responseCode == 200) {
                connection.inputStream.bufferedReader().use { it.readText() }
            } else {
                connection.errorStream?.bufferedReader()?.use { it.readText() } ?: "No response"
            }
            
            if (responseCode == 200) {
                val json = Json { ignoreUnknownKeys = true }
                val response = json.decodeFromString<PairResponse>(responseBody)
                
                if (response.success) {
                    PairingResult(
                        success = true,
                        deviceId = response.data?.device_id,
                        token = response.data?.token
                    )
                } else {
                    PairingResult(success = false, error = response.error ?: "Unknown error")
                }
            } else {
                PairingResult(success = false, error = "HTTP $responseCode: $responseBody")
            }
        } catch (e: Exception) {
            Log.e("ClientPairing", "Pairing failed", e)
            PairingResult(success = false, error = e.message ?: "Connection failed")
        }
    }
}

@kotlinx.serialization.Serializable
data class PairResponse(
    val success: Boolean,
    val data: PairData? = null,
    val error: String? = null
)

@kotlinx.serialization.Serializable
data class PairData(
    val device_id: String,
    val token: String
)
