package com.zai.callerid.voip

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import io.livekit.server.AccessToken
import io.livekit.server.RoomJoin
import io.livekit.server.RoomName
import io.livekit.server.RoomCreate
import io.livekit.server.CanPublish
import io.livekit.server.CanSubscribe
import io.livekit.server.CanPublishData
import livekit.org.webrtc.EglBase
import com.zai.callerid.data.Prefs

/**
 * Generates a short-lived LiveKit access token for joining/creating a room.
 * Uses the user's API key + secret pasted in Setup (LiveKit card).
 *
 * The token grants publish + subscribe + data so both sides can send/receive audio.
 */
object VoipToken {

    fun generate(context: Context, roomName: String, identity: String? = null): String? {
        val key = Prefs.livekitApiKey(context)
        val secret = Prefs.livekitSecret(context)
        if (key.isBlank() || secret.isBlank()) return null
        val user = identity?.takeIf { it.isNotBlank() } ?: Prefs.livekitIdentity(context)
        return try {
            AccessToken(key, secret, emptyList()).apply {
                identity = user
                addGrants(
                    RoomName(roomName),
                    RoomCreate(true),
                    RoomJoin(true),
                    CanPublish(true),
                    CanSubscribe(true),
                    CanPublishData(true)
                )
            }.toJwt()
        } catch (e: Exception) {
            null
        }
    }
}
