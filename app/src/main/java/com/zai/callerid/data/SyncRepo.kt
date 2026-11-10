package com.zai.callerid.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Family/friends shared spam list backed by a GitHub Gist (free "tiny backend").
 *
 * Owner:   create a gist at gist.github.com (secret is fine), paste its ID in Setup.
 * Family:  install the app, enter the SAME gist ID. "Pull" merges everyone's
 *          blocked numbers into your local list; "Push" uploads yours.
 * Write (Push) needs a GitHub token with gist scope; Pull of public gists works
 * without one.
 */
object SyncRepo {

    private const val FILE_NAME = "truecallid-spam.json"
    private const val API = "https://api.github.com"

    /** Upload local blocked list to the gist. Returns a human status line. */
    suspend fun push(context: Context, gistId: String, token: String): String {
        if (gistId.isBlank()) return "Enter a Gist ID first"
        if (token.isBlank()) return "Push needs a GitHub token (gist scope)"
        return try {
            val numbers = SpamDb.get(context).dao().once().filter { it.blocked }
            val arr = JSONArray()
            numbers.forEach { n ->
                arr.put(JSONObject().put("number", n.number).put("label", n.label))
            }
            val payload = JSONObject()
                .put("files", JSONObject().put(
                    FILE_NAME,
                    JSONObject().put("content", JSONObject().put("numbers", arr).toString())
                ))
            val (code, _) = http("$API/gists/$gistId", "PATCH", token, payload.toString())
            if (code in 200..299) "✅ Pushed ${numbers.size} numbers to the shared list"
            else "❌ Push failed (HTTP $code) — check Gist ID and token"
        } catch (e: Exception) {
            "❌ Push failed: ${e.message}"
        }
    }

    /** Merge the gist's blocked numbers into the local DB. Returns a human status line. */
    suspend fun pull(context: Context, gistId: String, token: String): String {
        if (gistId.isBlank()) return "Enter a Gist ID first"
        return try {
            val (code, body) = http("$API/gists/$gistId", "GET", token.takeIf { it.isNotBlank() }, null)
            if (code !in 200..299) return "❌ Pull failed (HTTP $code) — check Gist ID"
            val content = JSONObject(body)
                .getJSONObject("files")
                .getJSONObject(FILE_NAME)
                .getString("content")
            val arr = JSONObject(content).optJSONArray("numbers") ?: JSONArray()
            val dao = SpamDb.get(context).dao()
            var added = 0
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val number = o.optString("number").trim()
                if (number.isBlank()) continue
                dao.upsert(
                    SpamNumber(
                        number = number,
                        label = o.optString("label").ifBlank { "Shared list" },
                        blocked = true
                    )
                )
                added++
            }
            "✅ Pulled $added numbers from the shared list"
        } catch (e: Exception) {
            "❌ Pull failed: ${e.message}"
        }
    }

    private fun http(url: String, method: String, token: String?, body: String?): Pair<Int, String> {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.requestMethod = method
        conn.connectTimeout = 10_000
        conn.readTimeout = 10_000
        conn.setRequestProperty("Accept", "application/vnd.github+json")
        if (!token.isNullOrBlank()) conn.setRequestProperty("Authorization", "Bearer $token")
        if (body != null) {
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.outputStream.use { it.write(body.toByteArray()) }
        }
        val code = conn.responseCode
        val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
            ?.bufferedReader()?.readText() ?: ""
        conn.disconnect()
        return code to text
    }
}
