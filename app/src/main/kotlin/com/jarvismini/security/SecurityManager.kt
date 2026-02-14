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

class SecurityManager(private val context: Context) {

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
    }

    fun enablePairingMode(durationSeconds: Int = 60) {
        pairingEnabled = true
        pairingExpiry = System.currentTimeMillis() + (durationSeconds * 1000)
    }

    fun disablePairingMode() {
        pairingEnabled = false
        pairingExpiry = 0L
    }

    fun isPairingEnabled(): Boolean {
        if (!pairingEnabled) return false
        if (System.currentTimeMillis() > pairingExpiry) {
            disablePairingMode()
            return false
        }
        return true
    }

    fun pairDevice(deviceName: String, ipAddress: String): PairResult {
        if (!isPairingEnabled()) {
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

        return PairResult(true, deviceId, token)
    }

    fun unpairDevice(deviceId: String): Boolean {
        val device = deviceRegistry.remove(deviceId) ?: return false
        tokenIndex.remove(device.tokenHash)
        rateLimitMap.remove(deviceId)
        saveDevices()
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
                "/execute" -> 20
                else -> 60
            }

            if (timestamps.size >= limit) return false
            timestamps.add(now)
            return true
        }
    }

    fun isCommandAllowed(command: String): CommandValidation {
        if (!allowlistEnabled) {
            return CommandValidation(true, null)  // 🔥 BYPASS
        }

        val cmd = command.trim()
        if (cmd.isEmpty()) return CommandValidation(false, "Empty command")
        if (cmd.length > 10000) return CommandValidation(false, "Command too large")

        val baseCmd = cmd.split(" ", "\t").firstOrNull() ?: ""
        if (baseCmd !in allowedCommands) {
            return CommandValidation(false, "Command not in allowlist: $baseCmd")
        }

        val dangerousPatterns = listOf(";", "|", "&", ">", "<", "`", "$(", "\\$", "..", "~")
        for (pattern in dangerousPatterns) {
            if (cmd.contains(pattern)) {
                return CommandValidation(false, "Dangerous pattern: $pattern")
            }
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
        val random = SecureRandom()
        val bytes = ByteArray(16)
        random.nextBytes(bytes)
        return "dev_" + bytes.joinToString("") { "%02x".format(it) }
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
