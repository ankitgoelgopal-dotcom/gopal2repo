package com.zai.callerid.data

import android.content.Context
import java.util.Calendar

/** Simple SharedPreferences-backed app settings (silent hours, auto-reply SMS). */
object Prefs {

    private fun sp(context: Context) =
        context.getSharedPreferences("tcid_settings", Context.MODE_PRIVATE)

    // ---- silent hours ----
    fun silentEnabled(context: Context): Boolean =
        sp(context).getBoolean("silent_enabled", false)

    fun setSilentEnabled(context: Context, value: Boolean) =
        sp(context).edit().putBoolean("silent_enabled", value).apply()

    fun silentStart(context: Context): Int = sp(context).getInt("silent_start", 23)
    fun silentEnd(context: Context): Int = sp(context).getInt("silent_end", 7)

    fun setSilentHours(context: Context, start: Int, end: Int) =
        sp(context).edit().putInt("silent_start", start).putInt("silent_end", end).apply()

    /** True if [nowHour] falls inside the [start, end) window (wraps midnight). */
    fun inSilentWindow(start: Int, end: Int, nowHour: Int = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)): Boolean =
        if (start == end || start !in 0..23 || end !in 0..23) false
        else if (start < end) nowHour in start until end
        else nowHour >= start || nowHour < end

    // ---- missed-call auto reply ----
    fun smsEnabled(context: Context): Boolean =
        sp(context).getBoolean("sms_enabled", false)

    fun setSmsEnabled(context: Context, value: Boolean) =
        sp(context).edit().putBoolean("sms_enabled", value).apply()

    fun smsMessage(context: Context): String =
        sp(context).getString("sms_message", "Sorry I missed your call. I'll call you back soon.")!!

    fun setSmsMessage(context: Context, value: String) =
        sp(context).edit().putString("sms_message", value).apply()

    /** Rate limit: one auto-reply per number per hour. */
    fun lastSent(context: Context, key: String): Long =
        sp(context).getLong("sent_$key", 0L)

    fun markSent(context: Context, key: String, at: Long = System.currentTimeMillis()) =
        sp(context).edit().putLong("sent_$key", at).apply()
}
