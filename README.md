# One Inbox

A personal Android app that captures chat notifications (WhatsApp, Messenger, Instagram, Telegram, Discord, LinkedIn, X) into a local inbox, grouped by app and conversation, and summarizes unread messages with Claude. Everything stays on the device except the text sent to the Claude API when you tap Summarize. Requires Android 12 (API 31) or later.

## Build and install

```
./gradlew :app:installDebug
```

Run it with a phone connected over adb (or an emulator running). Unit tests: `./gradlew :app:testDebugUnitTest`.

## First run

The app opens a setup checklist. On a Xiaomi / HyperOS phone:

1. **Notification access**: tap Open and enable One Inbox. If the switch is greyed out, open App info, tap the three-dot menu, choose "Allow restricted settings", then try again.
2. **Battery**: set One Inbox to "No restrictions" so the system does not kill the listener.
3. **Autostart**: enable it for One Inbox, then tick the box.
4. **Lock in Recents** (optional): long-press the app card in Recents and tap the lock.

"Done" unlocks once notification access is granted. Add your Claude API key under Settings; you can re-run setup from there too.

## Test without a chat app

Debug builds also capture notifications posted from adb:

```
adb shell cmd notification post -S messaging --conversation "Test chat" --message "Alex:明天见 😊" --message "Sam:see you" one_inbox_test "x"
```

An "ADB test" app appears in the rail with "Test chat" and two unread messages. Posting it again does not duplicate them.
