package com.zai.callerid.data

import android.content.Context
import com.zai.callerid.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Shared community spam reports (what makes Truecaller accurate), with nothing for users to configure.
 * The backend address is built into the app; numbers are sent only as SHA-256 hashes.
 * Backend setup: see supabase/schema.sql. Disabled automatically if not configured.
 */
object CommunitySpam {

    data class Result(val reports: Int, val label: String)

    fun configured(): Boolean = BuildConfig.SPAM_API_URL.isNotBlank() && BuildConfig.SPAM_API_KEY.isNotBlank()

    private val cache = HashMap<String, Pair<Long, Result?>>()

    private fun hash(key: String): String =
        MessageDigest.getInstance("SHA-256").digest(("tcid1:$key").toByteArray())
            .joinToString("") { "%02x".format(it) }

    suspend fun lookup(context: Context, key: String, timeoutMs: Int): Result? {
        if (!configured() || key.isBlank()) return null
        synchronized(cache) {
            cache[key]?.let { (t, r) -> if (System.currentTimeMillis() - t < 6 * 3600_000L) return r }
        }
        return try {
            val r = call("spam_lookup", JSONObject().put("h", hash(key)), timeoutMs)?.let { body ->
                val arr = JSONArray(body)
                if (arr.length() == 0) null
                else arr.getJSONObject(0).let { Result(it.optInt("reports", 0), it.optString("label", "")) }
            }
            synchronized(cache) { cache[key] = System.currentTimeMillis() to r }
            r
        } catch (_: Exception) { null }
    }

    /** Fire-and-forget: tell the community this number is spam. */
    suspend fun report(context: Context, key: String, label: String) {
        if (!configured() || !Prefs.communityOn(context) || key.isBlank()) return
        try {
            call(
                "spam_report",
                JSONObject().put("h", hash(key)).put("r", Prefs.reporterId(context)).put("lbl", label.take(40)),
                6000
            )
            synchronized(cache) { cache.remove(key) }
        } catch (_: Exception) { }
    }

    private suspend fun call(fn: String, body: JSONObject, timeoutMs: Int): String? = withContext(Dispatchers.IO) {
        val conn = URL("${BuildConfig.SPAM_API_URL.trimEnd('/')}/rest/v1/rpc/$fn").openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = timeoutMs
            conn.readTimeout = timeoutMs
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("apikey", BuildConfig.SPAM_API_KEY)
            // Old-style anon keys are JWTs (eyJ...) and go in Authorization too; new sb_publishable_ keys only in apikey
            if (BuildConfig.SPAM_API_KEY.startsWith("eyJ")) {
                conn.setRequestProperty("Authorization", "Bearer ${BuildConfig.SPAM_API_KEY}")
            }
            conn.outputStream.use { it.write(body.toString().toByteArray()) }
            val code = conn.responseCode
            if (code !in 200..299) null else conn.inputStream.bufferedReader().readText()
        } finally { conn.disconnect() }
    }
}
