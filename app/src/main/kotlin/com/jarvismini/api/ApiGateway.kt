package com.jarvismini.api

import android.content.Context
import android.util.Log
import com.jarvismini.executor.FileBasedExecutor
import com.jarvismini.executor.UnifiedExecutor
import com.jarvismini.llm.LlmBackendFactory
import com.jarvismini.security.AuthResult
import com.jarvismini.security.SecurityManager
import com.jarvismini.security.TrustedDevice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

class ApiGateway(
    private val context: Context,
    private val executor: UnifiedExecutor,
    private val securityManager: SecurityManager
) {

    private val tag = "ApiGateway"

    suspend fun handleRequest(request: ApiRequest): ApiResponse = withContext(Dispatchers.IO) {
        try {
            Log.d(tag, "Handling request: ${request.endpoint} from ${request.source}")
            
            var authenticatedDevice: TrustedDevice? = null

            // Skip auth for /pair and /health endpoints
            if (request.source == RequestSource.LAN_CLIENT &&
                request.endpoint != "/pair" &&
                request.endpoint != "/health"
            ) {
                val authCheck = authenticateLanRequest(request)
                if (authCheck is AuthCheckResult.Failure) {
                    return@withContext authCheck.response
                }
                authenticatedDevice = (authCheck as AuthCheckResult.Success).device
            }

            when (request.endpoint) {
                "/pair" -> handlePair(request)
                "/unpair" -> handleUnpair(request, authenticatedDevice!!)
                "/devices" -> handleDevices()
                "/generate" -> handleGenerate(request)
                "/execute" -> handleExecute(request)
                "/chat" -> handleChat(request)
                "/health" -> handleHealth()
                else -> {
                    Log.w(tag, "Unknown endpoint: ${request.endpoint}")
                    errorResponse(404, "Unknown endpoint: ${request.endpoint}")
                }
            }

        } catch (e: Exception) {
            Log.e(tag, "Request failed for ${request.endpoint}", e)
            errorResponse(500, "Internal error: ${e.message}")
        }
    }

    private fun authenticateLanRequest(request: ApiRequest): AuthCheckResult {
        val auth = request.auth ?: return AuthCheckResult.Failure(
            errorResponse(401, "Missing authentication")
        )

        val authResult = securityManager.authenticateToken(auth)
        if (authResult is AuthResult.Failure) {
            return AuthCheckResult.Failure(
                errorResponse(401, authResult.reason)
            )
        }

        val device = (authResult as AuthResult.Success).device

        if (!securityManager.checkRateLimit(device.id, request.endpoint)) {
            return AuthCheckResult.Failure(
                errorResponse(429, "Rate limit exceeded")
            )
        }

        return AuthCheckResult.Success(device)
    }

    private suspend fun handlePair(request: ApiRequest): ApiResponse {
        val deviceName = request.body["device_name"]
        val ipAddress = request.auth?.ipAddress ?: "unknown"

        if (deviceName.isNullOrBlank()) {
            return errorResponse(400, "Missing device_name")
        }

        val result = securityManager.pairDevice(deviceName, ipAddress)

        return if (result.success) {
            ApiResponse(
                success = true,
                data = mapOf(
                    "device_id" to result.deviceId!!,
                    "token" to result.token!!
                ),
                statusCode = 200
            )
        } else {
            errorResponse(403, result.error ?: "Pairing failed")
        }
    }

    private suspend fun handleUnpair(request: ApiRequest, device: TrustedDevice): ApiResponse {
        val deviceId = request.body["device_id"]

        if (deviceId.isNullOrBlank()) {
            return errorResponse(400, "Missing device_id")
        }

        val success = securityManager.unpairDevice(deviceId)

        return if (success) {
            ApiResponse(true, mapOf("message" to "Device unpaired"), null, 200)
        } else {
            errorResponse(404, "Device not found")
        }
    }

    private suspend fun handleDevices(): ApiResponse {
        val devices = securityManager.getPairedDevices()

        val deviceInfoList = devices.map { d ->
            mapOf(
                "id" to d.id,
                "name" to d.name,
                "ip_address" to d.ipAddress,
                "paired_at" to d.pairedAt.toString(),
                "last_seen" to d.lastSeen.toString()
            )
        }

        val dataMap = mutableMapOf<String, String>()
        deviceInfoList.forEachIndexed { index, info ->
            info.forEach { (key, value) ->
                dataMap["device_${index}_$key"] = value
            }
        }

        return ApiResponse(true, dataMap, null, 200)
    }

    private suspend fun handleGenerate(request: ApiRequest): ApiResponse {
        val query = request.body["query"]

        if (query.isNullOrBlank()) {
            return errorResponse(400, "Missing query")
        }

        val backend = LlmBackendFactory.getBackend(request.source, context, executor)

        return try {
            withTimeout(30000L) {
                val result = backend.generate(query)
                ApiResponse(
                    true,
                    mapOf("command" to result, "response" to result),
                    null,
                    200
                )
            }
        } catch (e: Exception) {
            Log.e(tag, "Generate failed", e)
            errorResponse(500, "Generation failed: ${e.message}")
        }
    }

    private suspend fun handleExecute(request: ApiRequest): ApiResponse {
        val command = request.body["command"]

        if (command.isNullOrBlank()) {
            return errorResponse(400, "Missing command")
        }

        val validation = securityManager.isCommandAllowed(command)
        if (!validation.allowed) {
            return errorResponse(403, "Command blocked: ${validation.reason}")
        }

        val execRequest = com.jarvismini.executor.ExecutionRequest(
            command = command,
            source = request.source,
            timeout = 30000L,
            maxOutputSize = 10000
        )

        return try {
            val result = executor.execute(execRequest)
            ApiResponse(
                result.success,
                mapOf(
                    "output" to result.output,
                    "exit_code" to result.exitCode.toString(),
                    "execution_time" to result.executionTime.toString()
                ),
                null,
                if (result.success) 200 else 500
            )
        } catch (e: Exception) {
            Log.e(tag, "Execute failed", e)
            errorResponse(500, "Execution failed: ${e.message}")
        }
    }

    private suspend fun handleChat(request: ApiRequest): ApiResponse {
        val query = request.body["query"]

        if (query.isNullOrBlank()) {
            return errorResponse(400, "Missing query")
        }

        val backend = LlmBackendFactory.getBackend(request.source, context, executor)

        return try {
            withTimeout(30000L) {
                val messages = listOf(ChatMessage("user", query))
                val result = backend.chat(messages)
                ApiResponse(true, mapOf("response" to result), null, 200)
            }
        } catch (e: Exception) {
            Log.e(tag, "Chat failed", e)
            errorResponse(500, "Chat failed: ${e.message}")
        }
    }

    private suspend fun handleHealth(): ApiResponse {
        Log.d(tag, "Health check called")
        return ApiResponse(
            true,
            mapOf(
                "status" to "healthy",
                "backends" to "termux,cloud",
                "executor" to "file-based"
            ),
            null,
            200
        )
    }

    private fun errorResponse(code: Int, message: String): ApiResponse {
        Log.w(tag, "Error response: $code - $message")
        return ApiResponse(false, null, message, code)
    }
}

private sealed class AuthCheckResult {
    data class Success(val device: TrustedDevice) : AuthCheckResult()
    data class Failure(val response: ApiResponse) : AuthCheckResult()
}
