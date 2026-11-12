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

    // ---- ringtones (v1.3) ----
    fun numberRingtone(context: Context, key: String): String? =
        sp(context).getString("ring_number_$key", null)

    fun setNumberRingtone(context: Context, key: String, uri: String?) {
        val e = sp(context).edit()
        if (uri.isNullOrBlank()) e.remove("ring_number_$key") else e.putString("ring_number_$key", uri)
        e.apply()
    }

    fun categoryRingtone(context: Context, category: String): String? =
        sp(context).getString("ring_cat_${category.lowercase()}", null)

    fun setCategoryRingtone(context: Context, category: String, uri: String?) {
        val e = sp(context).edit()
        if (uri.isNullOrBlank() || category.isBlank()) {
            e.remove("ring_cat_${category.lowercase()}")
        } else {
            e.putString("ring_cat_${category.lowercase()}", uri)
        }
        e.apply()
    }

    fun allCategories(context: Context): List<String> =
        sp(context).all.keys
            .filter { it.startsWith("ring_cat_") }
            .map { it.removePrefix("ring_cat_") }

    fun spamBeepEnabled(context: Context): Boolean =
        sp(context).getBoolean("spam_beep_enabled", false)

    fun setSpamBeepEnabled(context: Context, value: Boolean) =
        sp(context).edit().putBoolean("spam_beep_enabled", value).apply()

    fun spamBeepUri(context: Context): String? =
        sp(context).getString("spam_beep_uri", null)

    fun setSpamBeepUri(context: Context, uri: String?) {
        val e = sp(context).edit()
        if (uri.isNullOrBlank()) e.remove("spam_beep_uri") else e.putString("spam_beep_uri", uri)
        e.apply()
    }

    // ---- spam detection (no user keys) ----
    fun spamDetectOn(context: Context): Boolean = sp(context).getBoolean("spam_detect", true)
    fun setSpamDetectOn(context: Context, v: Boolean) = sp(context).edit().putBoolean("spam_detect", v).apply()
    fun autoBlockLikely(context: Context): Boolean = sp(context).getBoolean("auto_block_likely", false)
    fun setAutoBlockLikely(context: Context, v: Boolean) = sp(context).edit().putBoolean("auto_block_likely", v).apply()
    fun communityOn(context: Context): Boolean = sp(context).getBoolean("community_on", true)
    fun setCommunityOn(context: Context, v: Boolean) = sp(context).edit().putBoolean("community_on", v).apply()
    fun reporterId(context: Context): String {
        val s = sp(context)
        return s.getString("reporter_id", null) ?: java.util.UUID.randomUUID().toString().also {
            s.edit().putString("reporter_id", it).apply()
        }
    }

    // ---- NumLookup API (v1.4) ----
    fun numLookupApiKey(context: Context): String =
        sp(context).getString("numlookup_key", "") ?: ""

    fun setNumLookupApiKey(context: Context, value: String) =
        sp(context).edit().putString("numlookup_key", value.trim()).apply()

    fun numLookupEnabled(context: Context): Boolean =
        sp(context).getBoolean("numlookup_enabled", false)

    fun setNumLookupEnabled(context: Context, value: Boolean) =
        sp(context).edit().putBoolean("numlookup_enabled", value).apply()

    // ---- LiveKit internet calling (v1.5) ----
    fun livekitUrl(context: Context): String =
        sp(context).getString("livekit_url", "wss://your-project.livekit.cloud") ?: ""

    fun setLivekitUrl(context: Context, value: String) =
        sp(context).edit().putString("livekit_url", value.trim()).apply()

    fun livekitApiKey(context: Context): String =
        sp(context).getString("livekit_key", "") ?: ""

    fun setLivekitApiKey(context: Context, value: String) =
        sp(context).edit().putString("livekit_key", value.trim()).apply()

    fun livekitSecret(context: Context): String =
        sp(context).getString("livekit_secret", "") ?: ""

    fun setLivekitSecret(context: Context, value: String) =
        sp(context).edit().putString("livekit_secret", value.trim()).apply()

    fun livekitIdentity(context: Context): String =
        sp(context).getString("livekit_identity", "") ?: ""

    fun setLivekitIdentity(context: Context, value: String) =
        sp(context).edit().putString("livekit_identity", value.trim().ifBlank { "user" + (1000..9999).random() }).apply()

    fun livekitReady(context: Context): Boolean =
        livekitUrl(context).startsWith("wss://") &&
            livekitApiKey(context).isNotBlank() &&
            livekitSecret(context).isNotBlank()
}
