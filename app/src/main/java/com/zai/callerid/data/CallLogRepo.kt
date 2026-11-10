package com.zai.callerid.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CallLog
import androidx.core.content.ContextCompat

data class CallLogEntry(
    val number: String,
    val name: String?,
    val type: Int,
    val date: Long,
    val duration: Long
)

object CallLogRepo {

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) ==
            PackageManager.PERMISSION_GRANTED

    fun load(context: Context, limit: Int = 200): List<CallLogEntry> {
        if (!hasPermission(context)) return emptyList()
        val out = mutableListOf<CallLogEntry>()
        val projection = arrayOf(
            CallLog.Calls.NUMBER,
            CallLog.Calls.CACHED_NAME,
            CallLog.Calls.TYPE,
            CallLog.Calls.DATE,
            CallLog.Calls.DURATION
        )
        try {
            context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                projection,
                null,
                null,
                CallLog.Calls.DATE + " DESC"
            )?.use { c ->
                while (c.moveToNext() && out.size < limit) {
                    val number = c.getString(0) ?: ""
                    val name = c.getString(1)
                    val type = c.getInt(2)
                    val date = c.getLong(3)
                    val duration = c.getLong(4)
                    out += CallLogEntry(number, name, type, date, duration)
                }
            }
        } catch (e: SecurityException) {
            return emptyList()
        }
        return out
    }

    /** Most recent missed-call number (fallback for auto-reply on API 31+). */
    fun latestMissedNumber(context: Context): String? {
        if (!hasPermission(context)) return null
        return try {
            context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(CallLog.Calls.NUMBER),
                CallLog.Calls.TYPE + "=?",
                arrayOf(CallLog.Calls.MISSED_TYPE.toString()),
                CallLog.Calls.DATE + " DESC"
            )?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
        } catch (e: Exception) {
            null
        }
    }
}
