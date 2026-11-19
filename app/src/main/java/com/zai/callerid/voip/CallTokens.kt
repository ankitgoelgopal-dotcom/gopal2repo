package com.zai.callerid.voip

import com.zai.callerid.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Gets a call token from LiveKit Cloud's development token server. No API key or secret in the app. */
object CallTokens {
    /** @return Pair(serverUrl, participantToken) or null */
    suspend fun fetch(roomName: String, identity: String, displayName: String): Pair<String, String>? =
        withContext(Dispatchers.IO) {
            try {
                val conn = URL("https://cloud-api.livekit.io/api/v2/sandbox/connection-details")
                    .openConnection() as HttpURLConnection
                try {
                    conn.connectTimeout = 8000
                    conn.readTimeout = 8000
                    conn.requestMethod = "POST"
                    conn.doOutput = true
                    conn.setRequestProperty("Content-Type", "application/json")
                    conn.setRequestProperty("X-Sandbox-ID", BuildConfig.LIVEKIT_SANDBOX_ID.trim())
                    val body = JSONObject()
                        .put("room_name", roomName)
                        .put("participant_identity", identity)
                        .put("participant_name", displayName)
                    conn.outputStream.use { it.write(body.toString().toByteArray()) }
                    if (conn.responseCode !in 200..299) return@withContext null
                    val json = JSONObject(conn.inputStream.bufferedReader().readText())
                    val url = json.optString("server_url", json.optString("serverUrl"))
                    val token = json.optString("participant_token", json.optString("participantToken"))
                    if (url.isBlank() || token.isBlank()) null else url to token
                } finally { conn.disconnect() }
            } catch (_: Exception) { null }
        }
}
