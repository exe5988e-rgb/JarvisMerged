package com.jarvismini.security

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.jarvismini.api.AuthContext
import com.jarvismini.api.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap

class SecurityManager private constructor(private val context: Context) {

    companion object {
        @Volatile
        private var INSTANCE: SecurityManager? = null

        fun getInstance(context: Context): SecurityManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SecurityManager(context.applicationContext).also { 
                    INSTANCE = it
                    Log.i("SecurityManager", "✅ Singleton instance created")
                }
            }
        }
    }

    private val tag = "SecurityManager"
    private val prefs: SharedPreferences =
        context.getSharedPreferences("jarvis_security", Context.MODE_PRIVATE)

    @Volatile
    private var pairingEnabled = false

    @Volatile
    private var pairingExpiry = 0L

    @Volatile
    private var allowlistEnabled = true  // 🔥 NEW TOGGLE

    fun setAllowlistEnabled(enabled: Boolean) {
        allowlistEnabled = enabled
        Log.i(tag, "Allowlist enabled: $enabled")
    }

    fun isAllowlistEnabled(): Boolean = allowlistEnabled

    private val deviceRegistry = ConcurrentHashMap<String, TrustedDevice>()
    private val tokenIndex = ConcurrentHashMap<String, String>()
    private val rateLimitMap =
        ConcurrentHashMap<String, ConcurrentHashMap<String, MutableList<Long>>>()

    private val allowedCommands = setOf(
        "date", "uptime", "whoami", "pwd", "ls", "echo",
        "termux-notification", "termux-toast", "termux-vibrate",
        "termux-battery-status", "termux-wifi-connectioninfo",
        "termux-wifi-scaninfo", "termux-clipboard-get"
    )

    init {
        loadDevices()
        Log.d(tag, "SecurityManager initialized - Instance: ${this.hashCode()}")
    }

    fun enablePairingMode(durationSeconds: Int = 60) {
        pairingEnabled = true
        pairingExpiry = System.currentTimeMillis() + (durationSeconds * 1000)
        Log.i(tag, "🔓 Pairing mode ENABLED for $durationSeconds seconds (Instance: ${this.hashCode()})")
        Log.i(tag, "   Expiry timestamp: $pairingExpiry")
    }

    fun disablePairingMode() {
        pairingEnabled = false
        pairingExpiry = 0L
        Log.i(tag, "🔒 Pairing mode DISABLED (Instance: ${this.hashCode()})")
    }

    fun isPairingEnabled(): Boolean {
        val now = System.currentTimeMillis()
        val remaining = (pairingExpiry - now) / 1000
        
        Log.d(tag, "🔍 Pairing check (Instance: ${this.hashCode()}):")
        Log.d(tag, "   pairingEnabled: $pairingEnabled")
        Log.d(tag, "   Expiry: $pairingExpiry")
        Log.d(tag, "   Now: $now")
        Log.d(tag, "   Remaining: ${remaining}s")
        
        if (!pairingEnabled) {
            Log.w(tag, "❌ Pairing DENIED: pairingEnabled is false")
            return false
        }
        
        if (now > pairingExpiry) {
            Log.w(tag, "❌ Pairing DENIED: Expired ${-remaining}s ago")
            disablePairingMode()
            return false
        }
        
        Log.i(tag, "✅ Pairing ALLOWED: ${remaining}s remaining")
        return true
    }
    
    fun getPairingExpiry(): Long = pairingExpiry

    fun pairDevice(deviceName: String, ipAddress: String): PairResult {
        Log.i(tag, "📱 pairDevice called (Instance: ${this.hashCode()}):")
        Log.i(tag, "   Device name: '$deviceName'")
        Log.i(tag, "   IP address: '$ipAddress'")
        
        if (!isPairingEnabled()) {
            Log.e(tag, "❌ Pairing REJECTED: Pairing mode is disabled")
            return PairResult(false, error = "Pairing mode disabled.")
        }

        val token = generateSecureToken()
        val tokenHash = hashToken(token)
        val deviceId = generateDeviceId()

        val device = TrustedDevice(
            id = deviceId,
            name = deviceName.take(100),
            tokenHash = tokenHash,
            ipAddress = ipAddress,
            pairedAt = System.currentTimeMillis(),
            lastSeen = System.currentTimeMillis()
        )

        deviceRegistry[deviceId] = device
        tokenIndex[tokenHash] = deviceId
        saveDevices()

        Log.i(tag, "✅ Device PAIRED successfully:")
        Log.i(tag, "   Device ID: $deviceId")
        Log.i(tag, "   Name: $deviceName")
        Log.i(tag, "   Token (first 16 chars): ${token.take(16)}...")
        
        return PairResult(true, deviceId, token)
    }

    fun unpairDevice(deviceId: String): Boolean {
        val device = deviceRegistry.remove(deviceId) ?: return false
        tokenIndex.remove(device.tokenHash)
        rateLimitMap.remove(deviceId)
        saveDevices()
        Log.i(tag, "Device unpaired: $deviceId")
        return true
    }

    fun getPairedDevices(): List<TrustedDevice> =
        deviceRegistry.values.sortedByDescending { it.lastSeen }

    fun authenticateToken(auth: AuthContext): AuthResult {
        val token = auth.deviceToken ?: return AuthResult.failure("Missing token")
        val tokenHash = hashToken(token)
        val deviceId = tokenIndex[tokenHash] ?: return AuthResult.failure("Invalid token")
        val device = deviceRegistry[deviceId] ?: return AuthResult.failure("Device not found")

        device.lastSeen = System.currentTimeMillis()
        return AuthResult.success(device)
    }

    fun checkRateLimit(deviceId: String, endpoint: String): Boolean {
        val now = System.currentTimeMillis()
        val deviceLimits = rateLimitMap.getOrPut(deviceId) { ConcurrentHashMap() }
        val timestamps = deviceLimits.getOrPut(endpoint) { mutableListOf() }

        synchronized(timestamps) {
            timestamps.removeAll { it < now - 60000 }

            val limit = when (endpoint) {
                "/generate", "/chat" -> 20
                "/execute" -> 60
                else -> 100
            }

            return if (timestamps.size < limit) {
                timestamps.add(now)
                true
            } else {
                false
            }
        }
    }

    fun isCommandAllowed(command: String): CommandValidation {
        val trimmed = command.trim()

        if (trimmed.isEmpty()) {
            return CommandValidation(false, "Empty command")
        }

        val firstToken = trimmed.split(" ").firstOrNull() ?: ""

        if (firstToken in allowedCommands) {
            return CommandValidation(true, null)
        }

        val dangerous = listOf("rm", "dd", "mkfs", "format", ">", "curl", "wget")
        if (dangerous.any { trimmed.contains(it) }) {
            return CommandValidation(false, "Dangerous command blocked")
        }

        return CommandValidation(true, null)
    }

    private fun generateSecureToken(): String {
        val random = SecureRandom()
        val bytes = ByteArray(32)
        random.nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun generateDeviceId(): String {
        return "dev_${System.currentTimeMillis()}_${(1000..9999).random()}"
    }

    private fun hashToken(token: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(token.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }

    private fun saveDevices() {
        val jsonString = json.encodeToString(deviceRegistry.values.toList())
        prefs.edit().putString("devices", jsonString).apply()
    }

    private fun loadDevices() {
        val jsonString = prefs.getString("devices", null) ?: return
        try {
            val deviceList = json.decodeFromString<List<TrustedDevice>>(jsonString)
            deviceList.forEach {
                deviceRegistry[it.id] = it
                tokenIndex[it.tokenHash] = it.id
            }
        } catch (_: Exception) {}
    }
}

@Serializable
data class TrustedDevice(
    val id: String,
    val name: String,
    val tokenHash: String,
    var ipAddress: String,
    val pairedAt: Long,
    var lastSeen: Long
)

data class PairResult(
    val success: Boolean,
    val deviceId: String? = null,
    val token: String? = null,
    val error: String? = null
)

data class CommandValidation(val allowed: Boolean, val reason: String?)

sealed class AuthResult {
    data class Success(val device: TrustedDevice) : AuthResult()
    data class Failure(val reason: String) : AuthResult()
    companion object {
        fun success(device: TrustedDevice) = Success(device)
        fun failure(reason: String) = Failure(reason)
    }
}
