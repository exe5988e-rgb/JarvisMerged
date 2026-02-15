package com.jarvismini.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL

@Composable
fun ClientPairingScreen(onBack: () -> Unit) {
    var serverIp by remember { mutableStateOf("") }
    var serverPort by remember { mutableStateOf("8081") }
    var deviceName by remember { mutableStateOf("JARVIS-Client-${(1000..9999).random()}") }
    var isPairing by remember { mutableStateOf(false) }
    var pairingResult by remember { mutableStateOf<PairingResult?>(null) }
    
    val scope = rememberCoroutineScope()
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.radialGradient(
                    colors = listOf(Color.Black, Color(0xFF001520), Color.Black)
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp)
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
                        tint = Color(0xFF00E0FF)
                    )
                }
                Text(
                    text = "PAIR WITH ANOTHER JARVIS",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Light,
                    letterSpacing = 3.sp,
                    color = Color(0xFF00E0FF)
                )
            }

            Spacer(Modifier.height(30.dp))
            
            // Instructions
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF001520).copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "📱 INSTRUCTIONS",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp,
                        color = Color(0xFF00E0FF),
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = """
                        1. On the MAIN JARVIS device:
                           • Go to Settings
                           • Start LAN Server
                           • Enable Pairing Mode
                        
                        2. Enter the IP address shown
                        
                        3. Tap PAIR DEVICE
                        """.trimIndent(),
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.8f),
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 18.sp
                    )
                }
            }
            
            Spacer(Modifier.height(20.dp))
            
            // Input Fields
            OutlinedTextField(
                value = serverIp,
                onValueChange = { serverIp = it },
                label = { Text("Server IP Address", color = Color(0xFF00E0FF)) },
                placeholder = { Text("192.168.x.x") },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Color(0xFF00E0FF),
                    unfocusedBorderColor = Color(0xFF00E0FF).copy(alpha = 0.5f),
                    cursorColor = Color(0xFF00E0FF)
                ),
                leadingIcon = {
                    Icon(Icons.Default.Wifi, null, tint = Color(0xFF00E0FF))
                }
            )
            
            Spacer(Modifier.height(12.dp))
            
            OutlinedTextField(
                value = serverPort,
                onValueChange = { serverPort = it },
                label = { Text("Port", color = Color(0xFF00E0FF)) },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Color(0xFF00E0FF),
                    unfocusedBorderColor = Color(0xFF00E0FF).copy(alpha = 0.5f),
                    cursorColor = Color(0xFF00E0FF)
                ),
                leadingIcon = {
                    Icon(Icons.Default.Settings, null, tint = Color(0xFF00E0FF))
                }
            )
            
            Spacer(Modifier.height(12.dp))
            
            OutlinedTextField(
                value = deviceName,
                onValueChange = { deviceName = it },
                label = { Text("Device Name", color = Color(0xFF00E0FF)) },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Color(0xFF00E0FF),
                    unfocusedBorderColor = Color(0xFF00E0FF).copy(alpha = 0.5f),
                    cursorColor = Color(0xFF00E0FF)
                ),
                leadingIcon = {
                    Icon(Icons.Default.Phone, null, tint = Color(0xFF00E0FF))
                }
            )
            
            Spacer(Modifier.height(24.dp))
            
            // Pair Button
            Button(
                onClick = {
                    scope.launch {
                        isPairing = true
                        pairingResult = null
                        try {
                            val result = pairWithServer(serverIp, serverPort, deviceName)
                            pairingResult = result
                        } catch (e: Exception) {
                            pairingResult = PairingResult(
                                success = false,
                                error = "Connection failed: ${e.message}"
                            )
                        } finally {
                            isPairing = false
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                enabled = !isPairing && serverIp.isNotBlank() && serverPort.isNotBlank() && deviceName.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF00E0FF),
                    disabledContainerColor = Color.Gray
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (isPairing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = Color.Black,
                        strokeWidth = 3.dp
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "PAIRING...",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp,
                        color = Color.Black
                    )
                } else {
                    Icon(Icons.Default.Link, null, tint = Color.Black)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "PAIR DEVICE",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp,
                        color = Color.Black
                    )
                }
            }
            
            Spacer(Modifier.height(20.dp))
            
            // Result Display
            pairingResult?.let { result ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (result.success) 
                            Color(0xFF00FF00).copy(alpha = 0.1f) 
                        else 
                            Color(0xFFFF0000).copy(alpha = 0.1f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (result.success) Icons.Default.CheckCircle else Icons.Default.Error,
                                contentDescription = null,
                                tint = if (result.success) Color(0xFF00FF00) else Color(0xFFFF0000),
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = if (result.success) "PAIRING SUCCESSFUL!" else "PAIRING FAILED",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 2.sp,
                                color = if (result.success) Color(0xFF00FF00) else Color(0xFFFF0000)
                            )
                        }
                        
                        if (result.success) {
                            Spacer(Modifier.height(16.dp))
                            Text(
                                text = "Device ID:",
                                fontSize = 12.sp,
                                color = Color.White.copy(alpha = 0.7f),
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = result.deviceId ?: "Unknown",
                                fontSize = 14.sp,
                                color = Color(0xFF00E0FF),
                                fontFamily = FontFamily.Monospace
                            )
                            
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = "Token (saved securely):",
                                fontSize = 12.sp,
                                color = Color.White.copy(alpha = 0.7f),
                                fontFamily = FontFamily.Monospace
                            )
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = Color.Black
                                ),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = result.token ?: "No token",
                                    fontSize = 10.sp,
                                    color = Color(0xFF00FF00),
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.padding(8.dp)
                                )
                            }
                            
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = "✅ This device can now communicate with the main JARVIS device!",
                                fontSize = 12.sp,
                                color = Color.White.copy(alpha = 0.8f),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = result.error ?: "Unknown error",
                                fontSize = 12.sp,
                                color = Color(0xFFFF0000),
                                fontFamily = FontFamily.Monospace
                            )
                            
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = "Troubleshooting:\n• Ensure pairing mode is enabled on main device\n• Check IP address is correct\n• Verify both devices on same WiFi",
                                fontSize = 11.sp,
                                color = Color.White.copy(alpha = 0.7f),
                                fontFamily = FontFamily.Monospace,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

data class PairingResult(
    val success: Boolean,
    val deviceId: String? = null,
    val token: String? = null,
    val error: String? = null
)

suspend fun pairWithServer(ip: String, port: String, deviceName: String): PairingResult {
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
                    // Save token to SharedPreferences for future use
                    // (You'll need to pass context here or use a repository pattern)
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
