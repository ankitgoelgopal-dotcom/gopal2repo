package com.zai.callerid.ui

import android.content.Context
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** A system ringtone (ringtones + notifications + alarms). */
data class PresetTone(val title: String, val uri: Uri, val typeLabel: String)

/** Loads ringtones, alarms, and notifications in one list. */
fun loadPresetTones(context: Context): List<PresetTone> {
    val out = mutableListOf<PresetTone>()
    listOf(
        RingtoneManager.TYPE_RINGTONE to "Ringtone",
        RingtoneManager.TYPE_NOTIFICATION to "Notification",
        RingtoneManager.TYPE_ALARM to "Alarm"
    ).forEach { (type, label) ->
        try {
            RingtoneManager.getCursor(context).use { cursor ->
                while (cursor.moveToNext()) {
                    val title = cursor.getString(RingtoneManager.TITLE_COLUMN_INDEX) ?: continue
                    val uriStr = cursor.getString(RingtoneManager.URI_COLUMN_INDEX) ?: continue
                    val idIdx = cursor.getColumnIndex(RingtoneManager.ID_COLUMN_INDEX)
                    val id = if (idIdx >= 0) cursor.getString(idIdx) else null
                    val uri = if (id != null) Uri.parse("$uriStr/$id") else Uri.parse(uriStr)
                    out += PresetTone(title, uri, label)
                }
            }
        } catch (_: Exception) {
            // skip unavailable types
        }
        RingtoneManager.getCursor(context, type).use { cursor ->
            while (cursor.moveToNext()) {
                val title = cursor.getString(RingtoneManager.TITLE_COLUMN_INDEX) ?: continue
                val uriStr = cursor.getString(RingtoneManager.URI_COLUMN_INDEX) ?: continue
                val idIdx = cursor.getColumnIndex(RingtoneManager.ID_COLUMN_INDEX)
                val id = if (idIdx >= 0) cursor.getString(idIdx) else null
                val uri = if (id != null) Uri.parse("$uriStr/$id") else Uri.parse(uriStr)
                out += PresetTone(title, uri, label)
            }
        }
    }
    // dedupe by URI string
    return out.distinctBy { it.uri.toString() }
}

/**
 * AlertDialog showing all system ringtones with a Play button.
 * On confirm, calls [onPick] with the chosen URI.
 */
@Composable
fun PresetRingtonePicker(
    context: Context,
    title: String = "Pick a preset sound",
    onPick: (Uri) -> Unit,
    onDismiss: () -> Unit
) {
    val tones = remember { loadPresetTones(context) }
    var playingUri by remember { mutableStateOf<Uri?>(null) }
    var currentRingtone by remember { mutableStateOf<Ringtone?>(null) }

    // Stop playing when the dialog closes
    DisposableEffect(Unit) {
        onDispose {
            currentRingtone?.stop()
            currentRingtone = null
        }
    }

    AlertDialog(
        onDismissRequest = {
            currentRingtone?.stop()
            onDismiss()
        },
        title = { Text(title) },
        text = {
            LazyColumn(modifier = Modifier.height(420.dp)) {
                items(tones) { tone ->
                    val isPlaying = playingUri == tone.uri
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                currentRingtone?.stop()
                                currentRingtone = RingtoneManager.getRingtone(context, tone.uri)
                                currentRingtone?.play()
                                playingUri = tone.uri
                            }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                currentRingtone?.stop()
                                if (isPlaying) {
                                    playingUri = null
                                } else {
                                    currentRingtone = RingtoneManager.getRingtone(context, tone.uri)
                                    currentRingtone?.play()
                                    playingUri = tone.uri
                                }
                            }
                        ) {
                            Icon(
                                Icons.Filled.PlayArrow,
                                contentDescription = "Play",
                                tint = if (isPlaying) Color(0xFF128C7E) else Color.Gray
                            )
                        }
                        Column(Modifier.weight(1f)) {
                            Text(tone.title, fontSize = 14.sp)
                            Text(tone.typeLabel, fontSize = 11.sp, color = Color.Gray)
                        }
                        TextButton(onClick = {
                            currentRingtone?.stop()
                            onPick(tone.uri)
                        }) { Text("Use") }
                    }
                }
                if (tones.isEmpty()) {
                    item {
                        Text(
                            "No system ringtones found on this device.",
                            color = Color.Gray, fontSize = 13.sp,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                currentRingtone?.stop()
                onDismiss()
            }) { Text("Cancel") }
        }
    )
}
