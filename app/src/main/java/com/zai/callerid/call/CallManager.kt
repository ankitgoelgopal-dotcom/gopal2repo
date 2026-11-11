package com.zai.callerid.call

import android.content.Context
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import android.telecom.TelecomManager
import android.telecom.VideoProfile
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Holds live calls handed to us by Android's Telecom framework (when we are the default phone app). */
object CallManager {

    val calls = mutableStateListOf<Call>()

    /** Bumped on every call change so Compose redraws. */
    var tick by mutableStateOf(0)
        private set

    var audioState: CallAudioState? by mutableStateOf(null)

    @Volatile
    var service: InCallService? = null

    private val callback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) { tick++ }
        override fun onDetailsChanged(call: Call, details: Call.Details) { tick++ }
    }

    fun add(call: Call) {
        call.registerCallback(callback)
        if (!calls.contains(call)) calls.add(call)
        tick++
    }

    fun remove(call: Call) {
        call.unregisterCallback(callback)
        calls.remove(call)
        tick++
    }

    @Suppress("DEPRECATION")
    fun stateOf(call: Call): Int = call.state

    /** Ringing call first, then active, then anything else. */
    fun primary(): Call? {
        tick // read for recomposition
        return calls.firstOrNull { stateOf(it) == Call.STATE_RINGING }
            ?: calls.firstOrNull { stateOf(it) == Call.STATE_ACTIVE }
            ?: calls.firstOrNull { stateOf(it) == Call.STATE_DIALING || stateOf(it) == Call.STATE_CONNECTING }
            ?: calls.firstOrNull()
    }

    fun numberOf(call: Call): String =
        call.details?.handle?.schemeSpecificPart.orEmpty()

    fun answer(call: Call) = call.answer(VideoProfile.STATE_AUDIO_ONLY)
    fun reject(call: Call) = call.reject(false, null)
    fun hangUp(call: Call) = call.disconnect()
    fun hold(call: Call) = call.hold()
    fun unhold(call: Call) = call.unhold()

    fun isMuted(): Boolean { tick; return audioState?.isMuted == true }
    fun isSpeaker(): Boolean { tick; return audioState?.route == CallAudioState.ROUTE_SPEAKER }

    fun setMuted(muted: Boolean) { service?.setMuted(muted) }

    @Suppress("DEPRECATION")
    fun setSpeaker(on: Boolean) {
        service?.setAudioRoute(
            if (on) CallAudioState.ROUTE_SPEAKER else CallAudioState.ROUTE_EARPIECE
        )
    }

    fun dtmf(call: Call, key: Char) {
        call.playDtmfTone(key)
        call.stopDtmfTone()
    }

    fun isDefaultDialer(context: Context): Boolean {
        val tm = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
        return tm.defaultDialerPackage == context.packageName
    }
}
