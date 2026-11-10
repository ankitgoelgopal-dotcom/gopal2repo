package com.zai.callerid.call

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.telephony.PhoneStateListener
import android.telephony.SmsManager
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import androidx.core.app.NotificationCompat
import com.zai.callerid.data.CallLogRepo
import com.zai.callerid.data.NumberUtils
import com.zai.callerid.data.Prefs

/**
 * Foreground service that watches call state.
 * On RINGING it triggers the Truecaller-style overlay; on IDLE it hides it.
 * Works on API 26-30 (PhoneStateListener) and API 31+ (TelephonyCallback).
 */
class CallMonitorService : Service() {

    companion object {
        private const val CHANNEL_ID = "call_monitor"
        private const val NOTIF_ID = 1001

        fun start(context: Context) {
            context.startForegroundService(Intent(context, CallMonitorService::class.java))
        }
    }

    private var registered = false
    private var lastState = -1
    private var lastRingingNumber: String? = null
    private val telephony by lazy { getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Caller ID protection is on")
            .setContentText("Watching incoming calls for spam")
            .setSmallIcon(android.R.drawable.ic_menu_call)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL)
        } else {
            startForeground(NOTIF_ID, notification)
        }
        registerListener()
    }

    private fun registerListener() {
        if (registered) return
        registered = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            telephony.registerTelephonyCallback(
                mainExecutor,
                object : TelephonyCallback(), TelephonyCallback.CallStateListener {
                    override fun onCallStateChanged(state: Int) {
                        handleState(state, null)
                    }
                }
            )
        } else {
            @Suppress("DEPRECATION")
            telephony.listen(
                object : PhoneStateListener() {
                    override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                        handleState(state, phoneNumber)
                    }
                },
                PhoneStateListener.LISTEN_CALL_STATE
            )
        }
    }

    private fun handleState(state: Int, phoneNumber: String?) {
        when (state) {
            TelephonyManager.CALL_STATE_RINGING -> {
                lastState = state
                lastRingingNumber =
                    phoneNumber?.takeIf { it.isNotBlank() } ?: IncomingHolder.fresh()
                if (Settings.canDrawOverlays(this)) {
                    OverlayService.show(this, lastRingingNumber)
                }
            }
            TelephonyManager.CALL_STATE_OFFHOOK -> {
                lastState = state
                OverlayService.hide(this)
            }
            TelephonyManager.CALL_STATE_IDLE -> {
                // RINGING -> IDLE without OFFHOOK means the call was missed
                if (lastState == TelephonyManager.CALL_STATE_RINGING) {
                    onMissedCall(lastRingingNumber)
                }
                lastState = state
                OverlayService.hide(this)
            }
        }
    }

    /**
     * Missed-call auto-reply (v1.2): if enabled, send the configurable SMS
     * once per number per hour (rate-limited via Prefs).
     */
    private fun onMissedCall(rawNumber: String?) {
        if (!Prefs.smsEnabled(this)) return
        var number = rawNumber?.takeIf { it.isNotBlank() }
        if (number.isNullOrBlank()) {
            number = CallLogRepo.latestMissedNumber(this)
        }
        val target = number?.takeIf { it.isNotBlank() } ?: return
        val key = NumberUtils.matchKey(target)
        val now = System.currentTimeMillis()
        if (now - Prefs.lastSent(this, key) < 3_600_000) return
        try {
            val sms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }
            sms.sendTextMessage(NumberUtils.normalize(target), null, Prefs.smsMessage(this), null, null)
            Prefs.markSent(this, key, now)
        } catch (e: SecurityException) {
            // SEND_SMS not granted
        } catch (e: Exception) {
            // no SIM / radio error — ignore
        }
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Call monitoring",
            NotificationManager.IMPORTANCE_MIN
        )
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(channel)
    }

    override fun onDestroy() {
        registered = false
        super.onDestroy()
    }
}
