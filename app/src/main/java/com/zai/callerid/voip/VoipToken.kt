package com.zai.callerid.voip

import android.content.Context
import android.util.Base64
import com.zai.callerid.data.Prefs
import org.json.JSONObject
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Generates a short-lived LiveKit access token (JWT) for joining a room.
 *
 * Self-contained — no livekit-server-sdk dependency (which conflicts with the
 * Android client SDK's protobuf-javalite). Implements the LiveKit JWT format:
 *   header = { alg: HS256, typ: JWT, kid: <api_key> }
 *   payload = { iss, sub, nbf, exp, video: {room, roomJoin, canPublish, ...} }
 * Signed with the API secret using HMAC-SHA256.
 */
object VoipToken {

    fun generate(context: Context, roomName: String, identity: String? = null): String? {
        val key = Prefs.livekitApiKey(context)
        val secret = Prefs.livekitSecret(context)
        if (key.isBlank() || secret.isBlank()) return null
        val user = identity?.takeIf { it.isNotBlank() } ?: Prefs.livekitIdentity(context)
        return try {
            val nowSec = System.currentTimeMillis() / 1000
            val header = JSONObject()
                .put("alg", "HS256")
                .put("typ", "JWT")
                .put("kid", key)

            val video = JSONObject()
                .put("room", roomName)
                .put("roomJoin", true)
                .put("roomCreate", true)
                .put("canPublish", true)
                .put("canSubscribe", true)
                .put("canPublishData", true)

            val payload = JSONObject()
                .put("iss", key)
                .put("sub", user)
                .put("nbf", nowSec - 30)
                .put("exp", nowSec + 3600) // 1-hour token
                .put("video", video)
                .put("jti", UUID.randomUUID().toString())
                .put("sid", UUID.randomUUID().toString())

            val headerB64 = base64Url(header.toString().toByteArray())
            val payloadB64 = base64Url(payload.toString().toByteArray())
            val signingInput = "$headerB64.$payloadB64"
            val sig = base64Url(hmacSha256(signingInput, secret))
            "$signingInput.$sig"
        } catch (e: Exception) {
            null
        }
    }

    private fun base64Url(bytes: ByteArray): String =
        Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)

    private fun hmacSha256(message: String, secret: String): ByteArray {
        val key = SecretKeySpec(secret.toByteArray(), "HmacSHA256")
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(key)
        return mac.doFinal(message.toByteArray())
    }
}
