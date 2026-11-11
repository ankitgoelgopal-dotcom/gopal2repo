package com.zai.callerid.call

import android.os.Bundle
import android.telecom.Call
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zai.callerid.data.ContactsRepo
import kotlinx.coroutines.delay

/** Our own in-call screen: incoming (answer / decline), dialing, active call with controls. */
class InCallActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        @Suppress("DEPRECATION")
        window.addFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        )
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                InCallScreen(onFinish = { finish() })
            }
        }
    }
}

@Composable
private fun InCallScreen(onFinish: () -> Unit) {
    val context = LocalContext.current
    val call = CallManager.primary()

    // Close when the last call is gone
    LaunchedEffect(CallManager.calls.size) {
        if (CallManager.calls.isEmpty()) {
            delay(400)
            if (CallManager.calls.isEmpty()) onFinish()
        }
    }

    if (call == null) {
        Box(Modifier.fillMaxSize().background(Color(0xFF101418)), contentAlignment = Alignment.Center) {
            Text("Call ended", color = Color.White, fontSize = 20.sp)
        }
        return
    }

    val state = CallManager.stateOf(call)
    val number = CallManager.numberOf(call)
    val name = remember(number) { ContactsRepo.nameFor(context, number) }
    val ringing = state == Call.STATE_RINGING
    val onHold = state == Call.STATE_HOLDING
    var showKeypad by remember { mutableStateOf(false) }
    var seconds by remember { mutableStateOf(0L) }

    LaunchedEffect(call, state) {
        while (state == Call.STATE_ACTIVE || state == Call.STATE_HOLDING) {
            val start = call.details?.connectTimeMillis ?: 0L
            seconds = if (start > 0) (System.currentTimeMillis() - start) / 1000 else 0
            delay(1000)
        }
    }

    val statusText = when (state) {
        Call.STATE_RINGING -> "Incoming call"
        Call.STATE_DIALING, Call.STATE_CONNECTING -> "Calling…"
        Call.STATE_HOLDING -> "On hold  %02d:%02d".format(seconds / 60, seconds % 60)
        Call.STATE_ACTIVE -> "%02d:%02d".format(seconds / 60, seconds % 60)
        Call.STATE_DISCONNECTING, Call.STATE_DISCONNECTED -> "Call ended"
        else -> ""
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF101418))
            .systemBarsPadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(48.dp))
        Text(
            name ?: number.ifBlank { "Unknown" },
            color = Color.White, fontSize = 32.sp, textAlign = TextAlign.Center
        )
        if (name != null) Text(number, color = Color.LightGray, fontSize = 16.sp)
        Spacer(Modifier.height(8.dp))
        Text(statusText, color = Color(0xFF9FD0FF), fontSize = 18.sp)

        // Second call (call waiting / swap)
        val other = CallManager.calls.firstOrNull { it != call }
        if (other != null) {
            Spacer(Modifier.height(16.dp))
            val otherNumber = CallManager.numberOf(other)
            val otherName = remember(otherNumber) { ContactsRepo.nameFor(context, otherNumber) }
            FilledTonalButton(onClick = {
                if (CallManager.stateOf(other) == Call.STATE_HOLDING) CallManager.unhold(other)
                else if (CallManager.stateOf(other) == Call.STATE_RINGING) CallManager.answer(other)
            }) {
                Text("Other call: ${otherName ?: otherNumber.ifBlank { "Unknown" }} — switch", fontSize = 13.sp)
            }
        }

        Spacer(Modifier.weight(1f))

        if (showKeypad && !ringing) {
            Keypad { CallManager.dtmf(call, it) }
            Spacer(Modifier.height(12.dp))
        }

        if (!ringing) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                val muted = CallManager.isMuted()
                val speaker = CallManager.isSpeaker()
                RoundToggle(Icons.Filled.MicOff, "Mute", muted) { CallManager.setMuted(!muted) }
                RoundToggle(Icons.Filled.Dialpad, "Keypad", showKeypad) { showKeypad = !showKeypad }
                RoundToggle(Icons.Filled.VolumeUp, "Speaker", speaker) { CallManager.setSpeaker(!speaker) }
                RoundToggle(Icons.Filled.Pause, "Hold", onHold) {
                    if (onHold) CallManager.unhold(call) else CallManager.hold(call)
                }
            }
            Spacer(Modifier.height(32.dp))
        }

        if (ringing) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                BigRoundButton(Icons.Filled.CallEnd, "Decline", Color(0xFFD32F2F)) { CallManager.reject(call) }
                BigRoundButton(Icons.Filled.Call, "Answer", Color(0xFF2E7D32)) { CallManager.answer(call) }
            }
        } else {
            BigRoundButton(Icons.Filled.CallEnd, "End", Color(0xFFD32F2F)) { CallManager.hangUp(call) }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun RoundToggle(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, on: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        FilledIconToggleButton(checked = on, onCheckedChange = { onClick() }, modifier = Modifier.size(60.dp)) {
            Icon(icon, contentDescription = label)
        }
        Text(label, color = Color.LightGray, fontSize = 12.sp)
    }
}

@Composable
private fun BigRoundButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, color: Color, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        FilledIconButton(
            onClick = onClick,
            modifier = Modifier.size(72.dp),
            shape = CircleShape,
            colors = IconButtonDefaults.filledIconButtonColors(containerColor = color, contentColor = Color.White)
        ) { Icon(icon, contentDescription = label, modifier = Modifier.size(32.dp)) }
        Spacer(Modifier.height(6.dp))
        Text(label, color = Color.White, fontSize = 13.sp)
    }
}

@Composable
private fun Keypad(onKey: (Char) -> Unit) {
    val rows = listOf("123", "456", "789", "*0#")
    rows.forEach { row ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            row.forEach { k ->
                FilledTonalButton(onClick = { onKey(k) }, modifier = Modifier.size(80.dp, 52.dp)) {
                    Text(k.toString(), fontSize = 22.sp)
                }
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}
