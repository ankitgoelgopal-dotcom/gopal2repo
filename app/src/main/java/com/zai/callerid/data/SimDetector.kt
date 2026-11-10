package com.zai.callerid.data

import android.content.Context
import android.telephony.TelephonyManager

/** Detects whether the device has an active SIM card. */
object SimDetector {

    enum class SimState { ACTIVE, ABSENT, UNKNOWN }

    fun state(context: Context): SimState {
        return try {
            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
                ?: return SimState.UNKNOWN
            when (tm.simState) {
                TelephonyManager.SIM_STATE_READY -> SimState.ACTIVE
                TelephonyManager.SIM_STATE_ABSENT -> SimState.ABSENT
                TelephonyManager.SIM_STATE_UNKNOWN,
                TelephonyManager.SIM_STATE_NOT_READY,
                TelephonyManager.SIM_STATE_PIN_REQUIRED,
                TelephonyManager.SIM_STATE_PUK_REQUIRED,
                TelephonyManager.SIM_STATE_NETWORK_LOCKED,
                TelephonyManager.SIM_STATE_CARD_IO_ERROR,
                TelephonyManager.SIM_STATE_CARD_RESTRICTED,
                TelephonyManager.SIM_STATE_PERM_DISABLED -> SimState.UNKNOWN
                else -> SimState.UNKNOWN
            }
        } catch (e: Exception) {
            SimState.UNKNOWN
        }
    }

    fun hasActiveSim(context: Context): Boolean = state(context) == SimState.ACTIVE
}
