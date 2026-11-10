# TrueCall ID — Truecaller-style Caller ID + Dialer for Android

A working Android calling app: real cellular calls, caller ID popup over incoming
calls, spam list with auto-reject, call log and contacts. Built with Kotlin +
Jetpack Compose. Every push automatically builds an installable APK via GitHub Actions.

## Features

| Feature | How it works |
|---|---|
| 📞 Real calls | Dial pad + tap-to-call from recents/contacts — dials through your SIM via `TelecomManager.placeCall` |
| 🪟 Caller ID popup | Floating overlay (Truecaller-style) over the incoming call screen with number, contact name and spam verdict |
| 🚫 Auto-reject spam | Implements Android's `CallScreeningService`; numbers in your spam list are rejected automatically (set app as "Caller ID & spam app", Android 10+) |
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
