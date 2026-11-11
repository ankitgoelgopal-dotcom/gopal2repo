package com.zai.callerid.call

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import androidx.core.app.NotificationCompat
import com.zai.callerid.data.ContactsRepo

/** Receives calls from Android when this app is the default phone app and shows our own call screen. */
class AppInCallService : InCallService() {

    companion object {
        private const val CHANNEL_ID = "incoming_call_ui"
        private const val NOTIF_ID = 2001
    }

    override fun onCreate() {
        super.onCreate()
        CallManager.service = this
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        CallManager.service = this
        CallManager.add(call)
        @Suppress("DEPRECATION")
        val ringing = call.state == Call.STATE_RINGING
        if (ringing) {
            showIncomingNotification(call)
        } else {
            launchCallScreen()
        }
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        CallManager.remove(call)
        getSystemService(NotificationManager::class.java).cancel(NOTIF_ID)
        if (CallManager.calls.isEmpty()) CallManager.audioState = null
    }

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onCallAudioStateChanged(audioState: CallAudioState?) {
        CallManager.audioState = audioState
    }

    private fun launchCallScreen() {
        try {
            startActivity(
                Intent(this, InCallActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            )
        } catch (_: Exception) { }
    }

    private fun showIncomingNotification(call: Call) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Incoming calls", NotificationManager.IMPORTANCE_HIGH).apply {
                setSound(null, null) // the system already rings
                enableVibration(false)
            }
        )
        val number = CallManager.numberOf(call)
        val title = ContactsRepo.nameFor(this, number) ?: number.ifBlank { "Unknown" }
        val pi = PendingIntent.getActivity(
            this, 0,
            Intent(this, InCallActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.sym_call_incoming)
            .setContentTitle("Incoming call")
            .setContentText(title)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setOngoing(true)
            .setContentIntent(pi)
            .setFullScreenIntent(pi, true)
            .build()
        nm.notify(NOTIF_ID, n)
        launchCallScreen()
    }
}
