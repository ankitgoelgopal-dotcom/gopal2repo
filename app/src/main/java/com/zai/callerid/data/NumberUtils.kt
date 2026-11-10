package com.zai.callerid.data

/**
 * Phone number helpers.
 * Default country code is +91 (India). Matching is done on the last 10 digits
 * so 9876543210, +919876543210 and 0 98765 43210 all match the same entry.
 */
object NumberUtils {

    fun normalize(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return ""
        val plus = trimmed.startsWith("+")
        val digits = trimmed.filter { it.isDigit() }
        return when {
            digits.isEmpty() -> ""
            plus -> "+$digits"
            digits.length == 10 -> "+91$digits"
            digits.startsWith("91") && digits.length == 12 -> "+$digits"
            digits.length == 11 && digits.startsWith("0") -> "+91${digits.drop(1)}"
            else -> "+$digits"
        }
    }

    /** Key used for spam-DB matching: last 10 digits. */
    fun matchKey(raw: String): String {
        val digits = raw.filter { it.isDigit() }
        return if (digits.length >= 10) digits.takeLast(10) else digits
    }
}
