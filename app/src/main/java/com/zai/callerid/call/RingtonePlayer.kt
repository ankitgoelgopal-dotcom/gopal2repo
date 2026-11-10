package com.zai.callerid.call

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import com.zai.callerid.data.NumberUtils
import com.zai.callerid.data.Prefs
import com.zai.callerid.data.SpamDb

/**
 * Plays custom ringtones during incoming calls (v1.3).
 *
 * Priority (highest first):
 *  1. Spam (warn-only, blocked=false) → spam beep
 *  2. Per-number custom ringtone       → that sound
 *  3. Note's category custom ringtone  → that sound
 *  4. No custom sound                   → let system ringtone play
 *
 * Rejected calls (spam blocked, prefix, silent hours) never reach this code
 * because the system doesn't fire a RINGING event for them.
 */
object RingtonePlayer {

    @Volatile
    private var player: MediaPlayer? = null

    /** Plays the appropriate sound for the incoming number, if any. */
    fun playForIncoming(context: Context, rawNumber: String) {
        stop()
        val key = NumberUtils.matchKey(rawNumber)
        val db = SpamDb.get(context)
        val spam = if (key.isNotBlank()) db.dao().getByKeySync(key) else null
        val note = if (key.isNotBlank()) db.notesDao().getByKeySync(key) else null

        val uriStr: String? = when {
            spam != null && !spam.blocked && Prefs.spamBeepEnabled(context) ->
                Prefs.spamBeepUri(context)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION).toString()
            key.isNotBlank() -> {
                Prefs.numberRingtone(context, key)
                    ?: note?.category?.takeIf { it.isNotBlank() }
                        ?.let { Prefs.categoryRingtone(context, it) }
            }
            else -> null
        }

        if (uriStr.isNullOrBlank()) return
        val uri = Uri.parse(uriStr)

        try {
            val mp = MediaPlayer().apply {
                setDataSource(context, uri)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                isLooping = false
                setOnCompletionListener { stop() }
                setOnErrorListener { _, _, _ -> stop(); true }
                prepare()
                start()
            }
            player = mp
        } catch (e: Exception) {
            // bad URI / permission revoked — fall back to system ringtone silently
        }
    }

    fun stop() {
        player?.let { mp ->
            try { if (mp.isPlaying) mp.stop() } catch (_: Exception) {}
            try { mp.release() } catch (_: Exception) {}
        }
        player = null
    }
}
