package com.zai.callerid.call

import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.telecom.Call
import android.telecom.CallScreeningService
import androidx.core.app.NotificationCompat
import com.zai.callerid.data.ContactsRepo
import com.zai.callerid.data.NumberUtils
import com.zai.callerid.data.Prefs
import com.zai.callerid.data.SpamDb

/**
 * The core Truecaller feature: every incoming call passes through here
 * (after the user grants the "Caller ID & spam app" role).
 *
 * Rejection rules, in order:
 *  1. Number in spam list (blocked=true)          -> reject
 *  2. Number matches a prefix rule (e.g. "+92")   -> reject
 *  3. Silent hours + unknown caller (not in contacts) -> reject
 *  4. Otherwise                                   -> allow
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
        val db = SpamDb.get(this)
        val spam = if (key.isNotBlank()) db.dao().getByKeySync(key) else null

        val rejectSpam = spam != null && spam.blocked

        val prefixHit = !rejectSpam && key.isNotBlank() && db.prefixDao().getAllSync().any {
            it.prefix.isNotBlank() && rawNumber.startsWith(it.prefix)
        }

        val silentHit = !rejectSpam && !prefixHit &&
            Prefs.silentEnabled(this) &&
            Prefs.inSilentWindow(Prefs.silentStart(this), Prefs.silentEnd(this)) &&
            rawNumber.isNotBlank() &&
            ContactsRepo.nameFor(this, rawNumber) == null

        val autoHit = !rejectSpam && !prefixHit && !silentHit && key.isNotBlank() &&
            Prefs.spamDetectOn(this) && Prefs.autoBlockLikely(this) &&
            kotlinx.coroutines.runBlocking {
                com.zai.callerid.data.SpamScore.evaluate(this@SpamScreeningService, rawNumber, useNetwork = true, timeoutMs = 1500)
            }.level == com.zai.callerid.data.SpamLevel.LIKELY

        val reject = rejectSpam || prefixHit || silentHit || autoHit

        val response = CallResponse.Builder()
            .setDisallowCall(reject)
            .setRejectCall(reject)
            .setSkipCallLog(false)
            .setSkipNotification(reject)
            .build()
        respondToCall(details, response)

        if (reject) {
            val reason = when {
                rejectSpam -> spam?.label ?: "spam"
                prefixHit -> "prefix rule"
                autoHit -> "likely spam"
                else -> "silent hours"
            }
            notifyBlocked(rawNumber, reason)
        }
    }

    private fun notifyBlocked(number: String, reason: String) {
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
            .setContentTitle("Call rejected")
            .setContentText("$number was rejected automatically ($reason)")
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
