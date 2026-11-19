package com.zai.callerid.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Family / friends shared spam list. Everyone enters the same share code; the list is kept in the
 * app's own database. No GitHub account, token or Gist needed.
 */
object SyncRepo {

    fun newCode(): String {
        val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return (1..10).map { alphabet.random() }.joinToString("")
    }

    private fun validate(code: String): String? = when {
        !CommunitySpam.configured() -> "Not connected to the shared database in this build"
        code.trim().length < 8 -> "Create or enter a share code first (at least 8 characters)"
        else -> null
    }

    /** Upload your blocked numbers to the group. */
    suspend fun push(context: Context, code: String): String {
        validate(code)?.let { return it }
        return try {
            val numbers = SpamDb.get(context).dao().once().filter { it.blocked }
            val arr = JSONArray()
            numbers.forEach { n -> arr.put(JSONObject().put("n", n.number).put("l", n.label)) }
            val r = CommunitySpam.rpc("family_push", JSONObject().put("c", code.trim().uppercase()).put("items", arr), 15000)
            if (r != null) "✅ Shared ${numbers.size} numbers with your group"
            else "❌ Could not share. Check your internet connection"
        } catch (e: Exception) {
            "❌ Could not share: ${e.message}"
        }
    }

    /** Add the group's numbers to your own list. */
    suspend fun pull(context: Context, code: String): String {
        validate(code)?.let { return it }
        return try {
            val body = CommunitySpam.rpc("family_pull", JSONObject().put("c", code.trim().uppercase()), 15000)
                ?: return "❌ Could not load the list. Check your internet connection"
            val arr = JSONArray(body)
            val dao = SpamDb.get(context).dao()
            var added = 0
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val number = o.optString("num").trim()
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
            "✅ Added $added numbers from your group"
        } catch (e: Exception) {
            "❌ Could not load the list: ${e.message}"
        }
    }
}
