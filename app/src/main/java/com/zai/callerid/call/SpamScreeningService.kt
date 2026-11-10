package com.zai.callerid.call

import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.telecom.Call
import android.telecom.CallScreeningService
import androidx.core.app.NotificationCompat
import com.zai.callerid.data.NumberUtils
import com.zai.callerid.data.SpamDb

/**
 * The core Truecaller feature: every incoming call passes through here
 * (after the user grants the "Caller ID & spam app" role).
 * Spam numbers are rejected automatically; the caller number is cached
 * for the overlay popup.
 */
class SpamScreeningService : CallScreeningService() {

    override fun onScreenCall(details: Call.Details) {
        // respondToCall() is only available on API 29+; role request also needs 29+
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return
        }

        val rawNumber = details.handle?.schemeSpecificPart.orEmpty()

        if (rawNumber.isNotBlank()) {
            IncomingHolder.set(rawNumber)
        }

        val key = NumberUtils.matchKey(rawNumber)
        val spam = if (key.isNotBlank()) SpamDb.get(this).dao().getByKeySync(key) else null

        val response = CallResponse.Builder()
            .setDisallowCall(spam != null && spam.blocked)
            .setRejectCall(spam != null && spam.blocked)
            .setSkipCallLog(false)
            .setSkipNotification(spam != null && spam.blocked)
            .build()
        respondToCall(details, response)

        if (spam != null && spam.blocked) {
            notifyBlocked(rawNumber, spam.label)
        }
    }

    private fun notifyBlocked(number: String, label: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(
                    "spam_blocked",
                    "Blocked calls",
                    NotificationManager.IMPORTANCE_DEFAULT
                )
            )
        }
        val notification = NotificationCompat.Builder(this, "spam_blocked")
            .setSmallIcon(android.R.drawable.ic_delete)
            .setContentTitle("Spam call blocked")
            .setContentText("$number was rejected automatically ($label)")
            .setAutoCancel(true)
            .build()
        try {
            androidx.core.app.NotificationManagerCompat.from(this)
                .notify((System.currentTimeMillis() and 0xFFFF).toInt(), notification)
        } catch (e: SecurityException) {
            // POST_NOTIFICATIONS not granted on Android 13+
        }
    }
}
