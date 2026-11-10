package com.zai.callerid.voip

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.zai.callerid.data.Prefs
import com.zai.callerid.data.SimDetector
import io.livekit.android.LiveKit
import io.livekit.android.Room
import io.livekit.android.events.RoomEvent
import io.livekit.android.events.collect
import io.livekit.android.util.LKLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import livekit.org.webrtc.MediaStreamTrack
/**
 * Full-screen internet call. Started via:
 *   VoipCallActivity.start(context, roomName, asHost=true|false)
 *
 * Joins the LiveKit room, publishes local audio, listens for remote participants
 * and shows call state. End Call disconnects and finishes the activity.
 */
class VoipCallActivity : ComponentActivity() {

    companion object {
        const val EXTRA_ROOM = "extra_room"
        const val EXTRA_AS_HOST = "extra_as_host"
        const val EXTRA_IDENTITY = "extra_identity"

        fun start(context: Context, roomName: String, asHost: Boolean, identity: String? = null) {
            context.startActivity(
                Intent(context, VoipCallActivity::class.java).apply {
                    putExtra(EXTRA_ROOM, roomName)
                    putExtra(EXTRA_AS_HOST, asHost)
                    if (identity != null) putExtra(EXTRA_IDENTITY, identity)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var room: Room? = null

    private val _state = MutableStateFlow(CallState())
    val state: StateFlow<CallState> = _state.asStateFlow()

    private val micPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) connectToRoom()
        else _state.value = _state.value.copy(status = CallStatus.ENDED, error = "Microphone permission denied")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { CallScreen() }

        val roomName = intent.getStringExtra(EXTRA_ROOM).orEmpty()
        val asHost = intent.getBooleanExtra(EXTRA_AS_HOST, true)
        val identity = intent.getStringExtra(EXTRA_IDENTITY)
        if (roomName.isBlank()) {
            _state.value = _state.value.copy(status = CallStatus.ENDED, error = "Missing room name")
            return
        }
        _state.value = _state.value.copy(
            roomName = roomName,
            asHost = asHost,
            identity = identity ?: Prefs.livekitIdentity(this)
        )

        // Pre-flight: LiveKit creds present?
        if (!Prefs.livekitReady(this)) {
            _state.value = _state.value.copy(
                status = CallStatus.ENDED,
                error = "LiveKit not configured. Open Setup → Internet calling to add your key."
            )
            return
        }

        // RECORD_AUDIO permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            connectToRoom()
        } else {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun connectToRoom() {
        val s = _state.value
        _state.value = s.copy(status = CallStatus.CONNECTING)
        scope.launch {
            try {
                val token = VoipToken.generate(this@VoipCallActivity, s.roomName, s.identity)
                if (token.isNullOrBlank()) {
                    _state.value = _state.value.copy(
                        status = CallStatus.ENDED,
                        error = "Could not generate LiveKit token. Check API key/secret in Setup."
                    )
                    return@launch
                }

                val r = LiveKit.create(applicationContext)
                room = r

                // Listen for room events
                scope.launch {
                    r.events.collect { event ->
                        when (event) {
                            is RoomEvent.ParticipantConnected -> {
                                _state.value = _state.value.copy(status = CallStatus.IN_CALL, remoteCount = 1)
                            }
                            is RoomEvent.ParticipantDisconnected -> {
                                _state.value = _state.value.copy(status = CallStatus.REMOTE_LEFT, remoteCount = 0)
                            }
                            is RoomEvent.Connected -> {
                                _state.value = _state.value.copy(status = CallStatus.WAITING_FOR_REMOTE)
                            }
                            is RoomEvent.Disconnected -> {
                                _state.value = _state.value.copy(status = CallStatus.ENDED)
                            }
                            is RoomEvent.TrackSubscribed -> {
                                _state.value = _state.value.copy(status = CallStatus.IN_CALL, remoteCount = 1)
                            }
                            else -> Unit
                        }
                    }
                }

                r.connect(Prefs.livekitUrl(this@VoipCallActivity), token)

                // Publish local mic
                val local = r.localParticipant
                local.setMicrophoneEnabled(true)
                _state.value = _state.value.copy(micEnabled = true)
            } catch (e: Exception) {
                LKLog.e { "VoIP connect failed: ${e.message}" }
                _state.value = _state.value.copy(
                    status = CallStatus.ENDED,
                    error = "Connection failed: ${e.message ?: "unknown"}"
                )
            }
        }
    }

    private fun toggleMic() {
        val r = room ?: return
        val newState = !_state.value.micEnabled
        scope.launch {
            try {
                r.localParticipant.setMicrophoneEnabled(newState)
                _state.value = _state.value.copy(micEnabled = newState)
            } catch (_: Exception) { }
        }
    }

    private fun endCall() {
        scope.launch {
            try {
                room?.disconnect()
            } catch (_: Exception) { }
            _state.value = _state.value.copy(status = CallStatus.ENDED)
            finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try { room?.disconnect() } catch (_: Exception) { }
        try { room?.release() } catch (_: Exception) { }
    }

    // ------------------------------------------------------------- UI

    @Composable
    private fun CallScreen() {
        val s by state.collectAsState()
        MaterialTheme {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = Color(0xFF0B1F1A)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Spacer(Modifier.height(40.dp))

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = if (s.asHost) "Calling…" else "Incoming internet call",
                            color = Color.White,
                            fontSize = 18.sp
                        )
                        Spacer(Modifier.size(8.dp))
                        Text(
                            text = s.identity.ifBlank { "Unknown" },
                            color = Color.White,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.size(4.dp))
                        Text(
                            text = "Room: ${s.roomName}",
                            color = Color(0xFFB0BEC5),
                            fontSize = 13.sp
                        )
                    }

                    // Center: status indicator
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        when (s.status) {
                            CallStatus.CONNECTING -> {
                                CircularProgressIndicator(color = Color(0xFF128C7E))
                                Spacer(Modifier.size(12.dp))
                                Text("Connecting…", color = Color.White, fontSize = 16.sp)
                            }
                            CallStatus.WAITING_FOR_REMOTE -> {
                                Text("📞", fontSize = 56.sp)
                                Spacer(Modifier.size(12.dp))
                                Text("Waiting for the other person to join…", color = Color.White, fontSize = 16.sp)
                                Spacer(Modifier.size(4.dp))
                                Text(
                                    "Share this call's link with them: callerid://call/${s.roomName}",
                                    color = Color(0xFFB0BEC5),
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(horizontal = 8.dp)
                                )
                            }
                            CallStatus.IN_CALL -> {
                                Text("🎙", fontSize = 56.sp)
                                Spacer(Modifier.size(8.dp))
                                Text("Connected", color = Color(0xFF4CAF50), fontSize = 18.sp)
                                Spacer(Modifier.size(4.dp))
                                Text("Talk normally — no SIM needed", color = Color(0xFFB0BEC5), fontSize = 13.sp)
                            }
                            CallStatus.REMOTE_LEFT -> {
                                Text("👋", fontSize = 56.sp)
                                Spacer(Modifier.size(8.dp))
                                Text("Other person left", color = Color(0xFFFFA726), fontSize = 16.sp)
                            }
                            CallStatus.ENDED -> {
                                Text(s.error ?: "Call ended", color = Color.White, fontSize = 16.sp)
                            }
                        }
                    }

                    Spacer(Modifier.height(40.dp))

                    // Bottom controls
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { toggleMic() },
                            modifier = Modifier.size(64.dp),
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = if (s.micEnabled) Color.White.copy(alpha = 0.15f) else Color(0xFFD32F2F)
                            )
                        ) {
                            Icon(
                                if (s.micEnabled) Icons.Filled.Mic else Icons.Filled.MicOff,
                                contentDescription = "Mute",
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        IconButton(
                            onClick = { endCall() },
                            modifier = Modifier.size(72.dp),
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = Color(0xFFD32F2F)
                            )
                        ) {
                            Icon(
                                Icons.Filled.CallEnd,
                                contentDescription = "End call",
                                tint = Color.White,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(40.dp))
                }
            }
        }
    }
}

enum class CallStatus { CONNECTING, WAITING_FOR_REMOTE, IN_CALL, REMOTE_LEFT, ENDED }

data class CallState(
    val status: CallStatus = CallStatus.CONNECTING,
    val roomName: String = "",
    val asHost: Boolean = true,
    val identity: String = "",
    val micEnabled: Boolean = false,
    val remoteCount: Int = 0,
    val error: String? = null
)
