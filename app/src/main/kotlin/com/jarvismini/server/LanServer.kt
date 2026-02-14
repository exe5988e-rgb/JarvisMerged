package com.jarvismini.server

import android.util.Log
import com.jarvismini.api.*
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoHTTPD.IHTTPSession
import fi.iki.elonen.NanoHTTPD.Method
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.decodeFromString

class LanServer(
    private val apiGateway: ApiGateway,
    port: Int = 8080
) : NanoHTTPD(port) {

    private val tag = "LanServer"

    override fun serve(session: IHTTPSession): Response {
        val method = session.method
        val uri = session.uri
        val clientIp = session.remoteIpAddress ?: "unknown"

        Log.d(tag, "$method $uri from $clientIp")

        return try {
            when (method) {
                Method.GET -> handleGet(uri, clientIp)
                Method.POST -> handlePost(session, uri, clientIp)
                Method.OPTIONS -> handleCors(session)
                else -> errorResponse(405, "Method not allowed")
            }
        } catch (e: Exception) {
            Log.e(tag, "Request failed", e)
            errorResponse(500, "Internal error")
        }
    }

    private fun handleGet(uri: String, clientIp: String): Response {
        val endpoint = if (uri.startsWith("/api/v1/")) {
            uri.removePrefix("/api/v1")
        } else uri

        val response = runBlocking {
            apiGateway.handleRequest(
                ApiRequest(
                    endpoint = endpoint,
                    source = RequestSource.LAN_CLIENT,
                    body = emptyMap(),
                    auth = AuthContext(null, clientIp, System.currentTimeMillis())
                )
            )
        }

        return toNanoResponse(response)
    }

    private fun handlePost(session: IHTTPSession, uri: String, clientIp: String): Response {
        val files = mutableMapOf<String, String>()

        try {
            session.parseBody(files)
        } catch (e: Exception) {
            return errorResponse(400, "Invalid request body")
        }

        val bodyString = files["postData"] ?: ""

        val bodyMap = try {
            val serializer = MapSerializer(String.serializer(), String.serializer())
            json.decodeFromString(serializer, bodyString)
        } catch (e: Exception) {
            return errorResponse(400, "Invalid JSON")
        }

        val authHeader = session.headers["authorization"]
        val deviceToken = authHeader?.removePrefix("Bearer ")?.trim()

        val endpoint = if (uri.startsWith("/api/v1/")) {
            uri.removePrefix("/api/v1")
        } else uri

        val response = runBlocking {
            apiGateway.handleRequest(
                ApiRequest(
                    endpoint = endpoint,
                    source = RequestSource.LAN_CLIENT,
                    body = bodyMap,
                    auth = AuthContext(deviceToken, clientIp, System.currentTimeMillis())
                )
            )
        }

        return toNanoResponse(response)
    }

    private fun handleCors(session: IHTTPSession): Response {
        val origin = session.headers["origin"] ?: "*"
        val response = newFixedLengthResponse(Response.Status.OK, "text/plain", "")
        response.addHeader("Access-Control-Allow-Origin", origin)
        response.addHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
        response.addHeader("Access-Control-Allow-Headers", "Content-Type, Authorization")
        response.addHeader("Access-Control-Allow-Credentials", "true")
        return response
    }

    private fun toNanoResponse(apiResponse: ApiResponse): Response {
        val status = when (apiResponse.statusCode) {
            200 -> Response.Status.OK
            400 -> Response.Status.BAD_REQUEST
            401 -> Response.Status.UNAUTHORIZED
            403 -> Response.Status.FORBIDDEN
            404 -> Response.Status.NOT_FOUND
            405 -> Response.Status.METHOD_NOT_ALLOWED
            429 -> Response.Status.TOO_MANY_REQUESTS
            else -> Response.Status.INTERNAL_ERROR
        }

        val jsonBody = apiResponse.toJson()
        val response = newFixedLengthResponse(status, "application/json; charset=utf-8", jsonBody)
        response.addHeader("Access-Control-Allow-Origin", "*")
        response.addHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
        response.addHeader("Access-Control-Allow-Headers", "Content-Type, Authorization")
        return response
    }

    private fun errorResponse(code: Int, message: String): Response {
        val status = when (code) {
            400 -> Response.Status.BAD_REQUEST
            404 -> Response.Status.NOT_FOUND
            405 -> Response.Status.METHOD_NOT_ALLOWED
            else -> Response.Status.INTERNAL_ERROR
        }

        val json = """{"success":false,"error":"$message"}"""
        val response = newFixedLengthResponse(status, "application/json", json)
        response.addHeader("Access-Control-Allow-Origin", "*")
        return response
    }
}
