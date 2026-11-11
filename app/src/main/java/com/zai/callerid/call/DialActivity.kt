package com.zai.callerid.call

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.zai.callerid.MainActivity

/** Entry point Android uses to hand a tapped phone number (tel: link) to the default phone app. */
class DialActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val number = intent?.data?.schemeSpecificPart
        if (!number.isNullOrBlank()) DialPrefill.set(number)
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        )
        finish()
    }
}

/** Number passed in from outside (tel: link) waiting to be shown on the dial pad. */
object DialPrefill {
    @Volatile private var pending: String? = null
    @Volatile var version: Int = 0
        private set

    fun set(number: String) { pending = number; version++ }
    fun take(): String? = pending.also { pending = null }
}
