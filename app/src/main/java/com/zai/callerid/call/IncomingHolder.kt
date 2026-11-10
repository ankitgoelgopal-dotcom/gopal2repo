package com.zai.callerid.call

/** Holds the number of the call currently ringing (set by CallScreeningService). */
object IncomingHolder {
    @Volatile
    var lastIncomingNumber: String? = null

    @Volatile
    var lastIncomingAt: Long = 0L

    fun set(number: String) {
        lastIncomingNumber = number
        lastIncomingAt = System.currentTimeMillis()
    }

    /** Valid for 30 seconds after a ring. */
    fun fresh(): String? =
        lastIncomingNumber?.takeIf { System.currentTimeMillis() - lastIncomingAt < 30_000 }
}
