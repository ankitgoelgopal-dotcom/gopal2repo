package com.zai.callerid

import android.Manifest
import android.app.role.RoleManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.zai.callerid.call.CallMonitorService
import com.zai.callerid.call.DialerHelper
import com.zai.callerid.data.CallLogEntry
import com.zai.callerid.data.CallLogRepo
import com.zai.callerid.data.ContactEntry
import com.zai.callerid.data.ContactsRepo
import com.zai.callerid.data.NumberUtils
import com.zai.callerid.data.SpamDb
import com.zai.callerid.data.SpamNumber
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants[Manifest.permission.READ_PHONE_STATE] == true) {
            CallMonitorService.start(this)
        }
    }

    private val settingsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { CallerIdApp() }
        requestCorePermissions()
    }

    private fun requestCorePermissions() {
        val wanted = mutableListOf(
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.ANSWER_PHONE_CALLS,
            Manifest.permission.POST_NOTIFICATIONS
        ).filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (wanted.isNotEmpty()) {
            permissionLauncher.launch(wanted.toTypedArray())
        }
    }

    fun requestCorePermissionsFromUi() = requestCorePermissions()

    fun openOverlaySettings() {
        settingsLauncher.launch(
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
        )
    }

    fun requestCallScreeningRole() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val rm = getSystemService(RoleManager::class.java) ?: return
            if (rm.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING) &&
                !rm.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)
            ) {
                settingsLauncher.launch(
                    rm.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING)
                )
            }
        }
    }

    fun isCallScreeningRoleHeld(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        val rm = getSystemService(RoleManager::class.java) ?: return false
        return rm.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)
    }
}

@Composable
fun CallerIdApp() {
    var tab by remember { mutableStateOf(0) }
    val tabIcons = listOf(
        Icons.Filled.Call, Icons.Filled.Person, Icons.Filled.Dialpad,
        Icons.Filled.Warning, Icons.Filled.Settings
    )
    val tabLabels = listOf("Recents", "Contacts", "Dialer", "Spam", "Setup")
    MaterialTheme {
        Scaffold(
            bottomBar = {
                NavigationBar {
                    tabLabels.forEachIndexed { i, label ->
                        NavigationBarItem(
                            selected = tab == i,
                            onClick = { tab = i },
                            icon = { Icon(tabIcons[i], contentDescription = label) },
                            label = { Text(label) }
                        )
                    }
                }
            }
        ) { innerPadding ->
            val mod = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 12.dp)
            when (tab) {
                0 -> RecentsScreen(mod)
                1 -> ContactsScreen(mod)
                2 -> DialPadScreen(mod)
                3 -> SpamScreen(mod)
                else -> SetupScreen(mod)
            }
        }
    }
}

// ---------------------------------------------------------------- Recents

@Composable
fun RecentsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val dao = remember { SpamDb.get(context).dao() }
    val spamList by dao.flowAll().collectAsState(initial = emptyList())
    val spamKeys = remember(spamList) { spamList.map { it.number }.toSet() }

    var entries by remember { mutableStateOf(listOf<CallLogEntry>()) }
    var hasCallLogPermission by remember { mutableStateOf(false) }
    var refresh by remember { mutableStateOf(0) }

    LaunchedEffect(refresh) {
        delay(300)
        hasCallLogPermission = CallLogRepo.hasPermission(context)
        entries = CallLogRepo.load(context)
    }

    Column(modifier) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Recent calls", fontSize = 22.sp)
            TextButton(onClick = { refresh++ }) { Text("Refresh") }
        }
        if (!hasCallLogPermission) {
            Card(colors = CardDefaults.cardColors()) {
                Text(
                    "Call log permission needed. Open Setup tab to grant it.",
                    Modifier.padding(14.dp),
                    fontSize = 14.sp
                )
            }
        } else if (entries.isEmpty()) {
            Text("No recent calls", Modifier.padding(14.dp), color = Color.Gray)
        } else {
            LazyColumn {
                items(entries) { e ->
                    val key = NumberUtils.matchKey(e.number)
                    val isSpam = key in spamKeys
                    CallRow(
                        title = e.name ?: NumberUtils.normalize(e.number),
                        subtitle = callTypeLabel(e.type) + if (isSpam) "  •  ⚠ SPAM" else "",
                        isSpam = isSpam,
                        onCall = { DialerHelper.call(context, e.number) },
                        onReport = {
                            if (key.isNotBlank()) {
                                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                                    dao.upsert(
                                        SpamNumber(number = key, label = "Reported from Recents", blocked = true)
                                    )
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

private fun callTypeLabel(type: Int): String = when (type) {
    android.provider.CallLog.Calls.INCOMING_TYPE -> "Incoming"
    android.provider.CallLog.Calls.OUTGOING_TYPE -> "Outgoing"
    android.provider.CallLog.Calls.MISSED_TYPE -> "Missed"
    android.provider.CallLog.Calls.REJECTED_TYPE -> "Rejected"
    else -> "Call"
}

// ---------------------------------------------------------------- Contacts

@Composable
fun ContactsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var contacts by remember { mutableStateOf(listOf<ContactEntry>()) }
    var query by remember { mutableStateOf("") }
    var hasPermission by remember { mutableStateOf(false) }
    var refresh by remember { mutableStateOf(0) }

    LaunchedEffect(refresh) {
        delay(300)
        hasPermission = ContactsRepo.hasPermission(context)
        contacts = ContactsRepo.load(context)
    }

    val filtered = remember(contacts, query) {
        if (query.isBlank()) contacts
        else contacts.filter {
            it.name.contains(query, ignoreCase = true) || it.number.contains(query)
        }
    }

    Column(modifier) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Contacts", fontSize = 22.sp)
            TextButton(onClick = { refresh++ }) { Text("Refresh") }
        }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Search contacts") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(6.dp))
        if (!hasPermission) {
            Card {
                Text(
                    "Contacts permission needed. Open Setup tab to grant it.",
                    Modifier.padding(14.dp),
                    fontSize = 14.sp
                )
            }
        } else {
            LazyColumn {
                items(filtered) { c ->
                    CallRow(
                        title = c.name,
                        subtitle = c.number,
                        isSpam = false,
                        onCall = { DialerHelper.call(context, c.number) },
                        onReport = null
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Dialer

@Composable
fun DialPadScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var number by remember { mutableStateOf("") }
    val keys = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("*", "0", "#")
    )

    Column(
        modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Spacer(Modifier.weight(1f))
        Text(
            number.ifBlank { "Enter number" },
            fontSize = 28.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(vertical = 16.dp)
        )
        keys.forEach { row ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                row.forEach { k ->
                    FilledTonalButton(
                        onClick = { number += k },
                        modifier = Modifier.size(78.dp, 62.dp)
                    ) {
                        Text(k, fontSize = 24.sp)
                    }
                }
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { if (number.isNotEmpty()) number = number.dropLast(1) }) {
                Icon(Icons.Filled.Clear, contentDescription = "Backspace")
            }
            FilledTonalButton(
                onClick = { DialerHelper.call(context, number) },
                modifier = Modifier.size(140.dp, 56.dp)
            ) {
                Icon(Icons.Filled.Call, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Call", fontSize = 18.sp)
            }
            IconButton(onClick = { number = "" }) {
                Icon(Icons.Filled.Delete, contentDescription = "Clear")
            }
        }
        Spacer(Modifier.weight(1f))
    }
}

// ---------------------------------------------------------------- Spam

@Composable
fun SpamScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val dao = remember { SpamDb.get(context).dao() }
    val scope = rememberCoroutineScope()
    val spamList by dao.flowAll().collectAsState(initial = emptyList())
    var newNumber by remember { mutableStateOf("") }

    Column(modifier) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Spam list", fontSize = 22.sp, modifier = Modifier.weight(1f))
            Text("${spamList.size} numbers", color = Color.Gray, fontSize = 13.sp)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = newNumber,
                onValueChange = { newNumber = it },
                placeholder = { Text("Add number e.g. 9876543210") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            FilledTonalButton(
                onClick = {
                    val key = NumberUtils.matchKey(newNumber)
                    if (key.isNotBlank()) {
                        scope.launch {
                            dao.upsert(SpamNumber(number = key, label = "Added manually", blocked = true))
                        }
                        newNumber = ""
                    }
                }
            ) { Text("Add") }
        }
        Spacer(Modifier.height(8.dp))
        if (spamList.isEmpty()) {
            Text(
                "No spam numbers yet. Report numbers from Recents or add them here.",
                color = Color.Gray, fontSize = 14.sp
            )
        }
        LazyColumn {
            items(spamList) { s ->
                Card(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Row(
                        Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(NumberUtils.normalize(s.number), fontSize = 16.sp)
                            Text(
                                s.label + if (s.blocked) "  •  auto-rejected" else "  •  warn only",
                                fontSize = 12.sp,
                                color = if (s.blocked) Color(0xFFD32F2F) else Color.Gray
                            )
                        }
                        Text(
                            "Block",
                            fontSize = 13.sp,
                            color = if (s.blocked) Color(0xFF2E7D32) else Color.Gray
                        )
                        Switch(
                            checked = s.blocked,
                            onCheckedChange = { checked ->
                                scope.launch {
                                    dao.upsert(s.copy(blocked = checked))
                                }
                            }
                        )
                        IconButton(onClick = { scope.launch { dao.delete(s.number) } }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete")
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Setup

@Composable
fun SetupScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val activity = context as? MainActivity
    var tick by remember { mutableStateOf(0) }
    var permissions by remember { mutableStateOf(mapOf<String, Boolean>()) }

    LaunchedEffect(tick) {
        delay(200)
        val wanted = listOf(
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.ANSWER_PHONE_CALLS
        )
        permissions = wanted.associateWith {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun refresh() { tick++ }

    Column(modifier.verticalScroll(rememberScrollState())) {
        Text("Setup & Permissions", fontSize = 22.sp, modifier = Modifier.padding(vertical = 8.dp))

        Card(Modifier.padding(vertical = 6.dp)) {
            Column(Modifier.padding(14.dp)) {
                Text("Core permissions", fontSize = 16.sp)
                permissions.forEach { (perm, granted) ->
                    Text(
                        (if (granted) "✅ " else "❌ ") + perm.substringAfterLast('.'),
                        fontSize = 13.sp,
                        color = if (granted) Color(0xFF2E7D32) else Color(0xFFD32F2F)
                    )
                }
                TextButton(onClick = {
                    activity?.requestCorePermissionsFromUi()
                    refresh()
                }) { Text("Grant / re-check permissions") }
            }
        }

        Card(Modifier.padding(vertical = 6.dp)) {
            Column(Modifier.padding(14.dp)) {
                Text("Floating caller ID popup", fontSize = 16.sp)
                Text(
                    "Show a Truecaller-style popup over incoming calls.",
                    fontSize = 13.sp, color = Color.Gray
                )
                val canOverlay = Settings.canDrawOverlays(context)
                Text(
                    if (canOverlay) "✅ Overlay permission granted" else "❌ Overlay permission missing",
                    fontSize = 13.sp,
                    color = if (canOverlay) Color(0xFF2E7D32) else Color(0xFFD32F2F)
                )
                TextButton(onClick = {
                    activity?.openOverlaySettings()
                    refresh()
                }) { Text("Enable \"Display over other apps\"") }
            }
        }

        Card(Modifier.padding(vertical = 6.dp)) {
            Column(Modifier.padding(14.dp)) {
                Text("Auto-reject spam calls", fontSize = 16.sp)
                Text(
                    "Set this app as the Caller ID & spam app so spam numbers are rejected automatically.",
                    fontSize = 13.sp, color = Color.Gray
                )
                val roleHeld = activity?.isCallScreeningRoleHeld() ?: false
                Text(
                    when {
                        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ->
                            "Needs Android 10+ (your device: API ${Build.VERSION.SDK_INT})"
                        roleHeld -> "✅ Caller ID & spam role active"
                        else -> "❌ Role not set yet"
                    },
                    fontSize = 13.sp,
                    color = if (roleHeld) Color(0xFF2E7D32) else Color(0xFFD32F2F)
                )
                TextButton(onClick = {
                    activity?.requestCallScreeningRole()
                    refresh()
                }) { Text("Set as Caller ID & spam app") }
            }
        }

        Card(Modifier.padding(vertical = 6.dp)) {
            Column(Modifier.padding(14.dp)) {
                Text("How it works", fontSize = 16.sp)
                Text(
                    "1. Tap Call anywhere in the app to dial a real number through your SIM.\n" +
                        "2. When someone calls, a popup shows their name / spam verdict.\n" +
                        "3. Numbers in your Spam list are rejected automatically.\n" +
                        "4. Report spam from the Recents tab.",
                    fontSize = 13.sp, color = Color.Gray
                )
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

// ---------------------------------------------------------------- shared row

@Composable
fun CallRow(
    title: String,
    subtitle: String,
    isSpam: Boolean,
    onCall: () -> Unit,
    onReport: (() -> Unit)?
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                fontSize = 16.sp,
                color = if (isSpam) Color(0xFFD32F2F) else Color.Unspecified
            )
            Text(subtitle, fontSize = 12.sp, color = Color.Gray, maxLines = 1)
        }
        onReport?.let {
            TextButton(onClick = it) {
                Text(if (isSpam) "Reported" else "Report spam", fontSize = 12.sp)
            }
        }
        IconButton(onClick = onCall) {
            Icon(Icons.Filled.Call, contentDescription = "Call", tint = Color(0xFF128C7E))
        }
    }
}
