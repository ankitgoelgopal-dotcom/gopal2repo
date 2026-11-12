package com.zai.callerid.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CallLog
import androidx.core.content.ContextCompat

enum class SpamLevel { NONE, SUSPECT, LIKELY }

data class SpamVerdict(val level: SpamLevel, val label: String = "")

/**
 * Spam detection that needs no API key from the user.
 * Signals, strongest first:
 *  1. Saved contact                -> never spam
 *  2. Your own spam list            -> LIKELY
 *  3. Community reports (optional)  -> LIKELY (3+ reporters) / SUSPECT (1-2)
 *  4. Known telemarketer / callback-scam number ranges -> LIKELY / SUSPECT
 *  5. Unknown number that keeps ringing without being answered -> SUSPECT
 */
object SpamScore {

    // Numbers in these ranges are widely used for "one ring and hang up" callback scams
    private val callbackScamPrefixes = listOf(
        "+1809", "+1829", "+1849", "+1284", "+1876", "+1473", "+1649", "+1900",
        "+225", "+232", "+252", "+4470", "+449"
    )

    suspend fun evaluate(context: Context, rawNumber: String, useNetwork: Boolean, timeoutMs: Int = 2500): SpamVerdict {
        val key = NumberUtils.matchKey(rawNumber)
        if (key.isBlank()) return SpamVerdict(SpamLevel.NONE)
        if (ContactsRepo.nameFor(context, rawNumber) != null) return SpamVerdict(SpamLevel.NONE)

        val local = SpamDb.get(context).dao().getByKeySync(key)
        if (local != null) return SpamVerdict(SpamLevel.LIKELY, local.label)

        var best = offlineHeuristics(rawNumber)

        if (useNetwork && Prefs.communityOn(context) && CommunitySpam.configured()) {
            val c = CommunitySpam.lookup(context, key, timeoutMs)
            if (c != null && c.reports > 0) {
                val v = if (c.reports >= 3)
                    SpamVerdict(SpamLevel.LIKELY, "Reported by ${c.reports} people" + if (c.label.isNotBlank()) " — ${c.label}" else "")
                else
                    SpamVerdict(SpamLevel.SUSPECT, "Reported by ${c.reports} ${if (c.reports == 1) "person" else "people"}")
                if (v.level.ordinal > best.level.ordinal) best = v
            }
        }

        if (best.level == SpamLevel.NONE && repeatedUnanswered(context, key)) {
            best = SpamVerdict(SpamLevel.SUSPECT, "Unknown number calling repeatedly")
        }
        return best
    }

    fun offlineHeuristics(rawNumber: String): SpamVerdict {
        val digits = rawNumber.filter { it.isDigit() }
        val local = if (digits.startsWith("91") && digits.length == 12) digits.drop(2) else digits
        // India TRAI: 140xxxxxxx is the registered telemarketing (promotional call) series
        if (local.length == 10 && local.startsWith("140")) {
            return SpamVerdict(SpamLevel.LIKELY, "Telemarketer (140 series)")
        }
        if (rawNumber.trim().startsWith("+")) {
            val plus = "+$digits"
            if (callbackScamPrefixes.any { plus.startsWith(it) }) {
                return SpamVerdict(SpamLevel.SUSPECT, "International callback-scam range")
            }
        }
        return SpamVerdict(SpamLevel.NONE)
    }

    /** 3+ missed/rejected calls from the same unknown number within 2 hours. */
    private fun repeatedUnanswered(context: Context, key: String): Boolean {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG)
            != PackageManager.PERMISSION_GRANTED
        ) return false
        return try {
            val since = System.currentTimeMillis() - 2 * 60 * 60 * 1000
            var count = 0
            context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(CallLog.Calls.NUMBER, CallLog.Calls.TYPE),
                "${CallLog.Calls.DATE} > ?", arrayOf(since.toString()), null
            )?.use { c ->
                while (c.moveToNext()) {
                    val n = c.getString(0).orEmpty()
                    val t = c.getInt(1)
                    if (NumberUtils.matchKey(n) == key &&
                        (t == CallLog.Calls.MISSED_TYPE || t == CallLog.Calls.REJECTED_TYPE)
                    ) count++
                }
            }
            count >= 3
        } catch (_: Exception) { false }
    }
}
