# TrueCall ID — Truecaller-style Caller ID + Dialer for Android

A working Android calling app: real cellular calls, caller ID popup over incoming
calls, spam list with auto-reject, call log and contacts. Built with Kotlin +
Jetpack Compose. Every push automatically builds an installable APK via GitHub Actions.

## Features

| Feature | How it works |
|---|---|
| 📞 Real calls | Dial pad + tap-to-call from recents/contacts — dials through your SIM via `TelecomManager.placeCall` |
| 🪟 Caller ID popup | Floating overlay (Truecaller-style) over the incoming call screen with number, contact name, spam verdict **and your private note** |
| 🚫 Auto-reject spam | Implements Android's `CallScreeningService`; numbers in your spam list are rejected automatically (set app as "Caller ID & spam app", Android 10+) |
| 📝 Personal notes (v1.2) | Per-number private notes ("Landlord", "Car broker") shown in the popup — Truecaller can't do this |
| 👨‍👩‍👧 Shared family blocklist (v1.2) | Sync your spam list with family via a GitHub Gist as a free backend — Push/Pull from the Setup tab |
| 🌙 Silent hours (v1.2) | Auto-reject all unknown numbers during configurable hours (default 23:00–07:00) |
| 🔤 Prefix rules (v1.2) | Reject every number starting with a prefix (e.g. `+92` or `140`) |
| 💬 Missed-call auto-reply (v1.2) | Automatically SMS "I'll call you back" when you miss a call (1 reply/number/hour, configurable message) |
| 🔔 Per-number ringtone (v1.3) | Set any audio file (or system preset) as the ringtone for a specific number |
| 🏷 Per-category ringtone (v1.3) | Set a sound per category (Family, Work, Delivery…) — auto-applies to all notes in that category |
| ⚠️ Spam beep (v1.3) | Short alert tone when a warn-only spam call rings through |
| 🎵 Preset ringtones (v1.4) | Pick from all system ringtones/notifications/alarms with preview — no need to hunt for files |
| 🌐 NumLookup caller ID (v1.4) | Identifies unknown callers automatically (carrier, country, line type) via the NumLookup API — results cached locally to save your free quota |
| 📋 Recents | Reads the native call log, flags spam numbers in red, one-tap "Report spam" |
| 👤 Contacts | Searchable contacts list with one-tap calling |
| 🗄️ Spam list | Local Room database — add / block / unblock / delete numbers manually |

## Install

1. Go to the **Actions** tab → latest run → download artifact **TrueCallID-debug-apk**
2. Copy `app-debug.apk` to your phone and install it (allow "install unknown apps")
3. Open the app → **Setup tab**:
   - Grant phone / call log / contacts / call permissions
   - Enable **"Display over other apps"** (for the popup)
   - Tap **"Set as Caller ID & spam app"** (for auto-reject, Android 10+)

## Project layout

```
app/src/main/java/com/zai/callerid/
├── MainActivity.kt              # Compose UI: Recents / Contacts / Dialer / Spam / Setup
└── call/
    ├── DialerHelper.kt          # Places real calls (TelecomManager)
    ├── CallMonitorService.kt    # Foreground service watching call state (API 26–30 + 31+)
    ├── OverlayService.kt        # Floating caller ID popup window
    ├── SpamScreeningService.kt  # CallScreeningService: auto-reject spam
    └── IncomingHolder.kt        # Shares the ringing number between services
    data/
    ├── SpamDb.kt                # Room DB: spam numbers
    ├── ContactsRepo.kt          # Contact lookup (incl. reverse lookup)
    ├── CallLogRepo.kt           # Call log reader
    └── NumberUtils.kt           # Number normalization (+91 default, last-10-digit keys)
```

## Honest limitations (v1)

- Spam "database" is local — Truecaller's global crowdsourced DB of billions of
  numbers is out of scope. Plug in a reverse-lookup API (e.g. NumLookup) in
  `OverlayService.lookupAndShow()` / `SpamScreeningService` for remote lookups.
- Incoming-call **numbers** on Android 12+ are only guaranteed via the
  CallScreeningService role — without the role the popup may show "Unknown number".
- Debug APK (signed with debug key). For Play Store you'd add a release signing config.

## Build

APK builds run automatically on every push (`.github/workflows/build.yml`).
Locally: `gradle assembleDebug` (JDK 17 + Android SDK required).
