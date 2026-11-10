package com.zai.callerid.data

import android.content.Context
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * NumLookup API client (v1.4). Free tier: ~100 lookups/month.
 * Sign up at numlookupapi.com and paste the key in Setup.
 *
 * Docs: GET https://api.numlookupapi.com/v1/validate?number=...&apikey=...
 * Returns: carrier, country, line_type. Caller name needs a paid plan.
 */
object NumLookupApi {

    data class Result(
        val valid: Boolean,
        val number: String,
        val countryName: String,
        val countryCode: String,
        val carrier: String,
        val lineType: String
    ) {
        fun short(): String = buildString {
            if (countryName.isNotBlank()) append(countryName)
            if (carrier.isNotBlank()) {
                append(if (isEmpty()) "" else " • ")
                append(carrier)
            }
            if (lineType.isNotBlank()) {
                append(if (isEmpty()) "" else " • ")
                append(lineType)
            }
            if (isEmpty()) append("Unknown")
        }
    }

    suspend fun lookup(context: Context, rawNumber: String): Result? {
        val key = Prefs.numLookupApiKey(context)
        if (!Prefs.numLookupEnabled(context) || key.isBlank() || rawNumber.isBlank()) return null
        val normalized = NumberUtils.normalize(rawNumber)
        if (normalized.isBlank()) return null
        return try {
            withContext0 {
                val url = URL(
                    "https://api.numlookupapi.com/v1/validate?number=" +
                        URLEncoder.encode(normalized, "UTF-8") +
                        "&apikey=" + URLEncoder.encode(key, "UTF-8")
                )
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 6_000
                conn.readTimeout = 6_000
                conn.requestMethod = "GET"
                try {
                    val code = conn.responseCode
                    val body = (if (code in 200..299) conn.inputStream else conn.errorStream)
                        ?.bufferedReader()?.readText() ?: ""
                    if (code !in 200..299) return@withContext0 null
                    val json = JSONObject(body)
                    if (!json.optBoolean("valid", false)) return@withContext0 null
                    Result(
                        valid = true,
                        number = json.optString("number", normalized),
                        countryName = json.optString("country_name", ""),
                        countryCode = json.optString("country_code", ""),
                        carrier = json.optString("carrier", ""),
                        lineType = json.optString("line_type", "")
                    )
                } finally {
                    conn.disconnect()
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    /** Inline withContext to avoid an extra import in callers. */
    private suspend fun <T> withContext0(block: () -> T): T =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { block() }
}
