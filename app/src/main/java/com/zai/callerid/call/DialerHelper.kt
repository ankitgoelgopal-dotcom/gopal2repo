package com.zai.callerid.call

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.telecom.TelecomManager
import com.zai.callerid.data.NumberUtils

/** Places real cellular calls through the phone's own SIM/dialer (Option A). */
object DialerHelper {

    fun call(context: Context, rawNumber: String, askPermission: Boolean = true) {
        val number = NumberUtils.normalize(rawNumber)
        if (number.isBlank()) return

        val canDirectDial = context.checkSelfPermission(android.Manifest.permission.CALL_PHONE) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED

        if (canDirectDial) {
            try {
                val tm = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
                tm.placeCall(Uri.parse("tel:$number"), Bundle())
                return
            } catch (e: SecurityException) {
                // fall through to dialer UI
            }
        }
        // Permission missing: ask once, then dial directly from the app
        if (askPermission && !canDirectDial) {
            var ctx: Context = context
            while (ctx is android.content.ContextWrapper && ctx !is com.zai.callerid.MainActivity) {
                ctx = ctx.baseContext
            }
            if (ctx is com.zai.callerid.MainActivity) {
                ctx.requestCallPermissionAndDial(number)
                return
            }
        }
        // Fallback: open the dialer pre-filled (no permission needed)
        context.startActivity(
            Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
