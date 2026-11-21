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

An "ADB test" app appears in the rail with "Test chat" and two unread messages. `cmd notification post` stamps every message with the current time, so running the command again adds two new messages; that is expected and does not show a dedupe bug. Dedupe of reposted notifications is covered by the unit tests (`NotificationParserTest`) and by real chat-app reposts.

## Releases

Every push to `main` runs `.github/workflows/release.yml`, which runs the unit tests, builds a signed release APK and publishes it as a GitHub Release (tag `vX.Y.Z`). Download `one-inbox-vX.Y.Z.apk` from the repository Releases page; each build has a higher versionCode, so it installs over the previous one.

**Versions.** Each release bumps the minor number of the newest `vX.Y.Z` tag (`v1.0.0`, `v1.1.0`, `v1.2.0`, ...); the first release is `v1.0.0`. To force a major bump, tag the commit that is (or will become) the head of `main` with `vN.0.0` and push the tag (for example `git tag v2.0.0 <commit> && git push origin v2.0.0`). If that commit is the head of `main` when the release runs, it releases `vN.0.0` itself; any later commit then releases `vN.1.0`, `vN.2.0`, and so on. `scripts/next-version.sh` prints the version a build would get.

**Secrets.** Set these repository secrets (Settings > Secrets and variables > Actions):

- `RELEASE_KEYSTORE_BASE64`: the release keystore, base64-encoded
- `RELEASE_KEYSTORE_PASSWORD`
- `RELEASE_KEY_ALIAS`
- `RELEASE_KEY_PASSWORD`

The signing key lives outside the repo (`*.jks` and `*.keystore` are git-ignored). Back it up somewhere safe: if it is lost, new builds can no longer update installed copies, and you would have to uninstall and reinstall.

Without these values, `./gradlew :app:assembleRelease` still works locally and produces an unsigned APK.
