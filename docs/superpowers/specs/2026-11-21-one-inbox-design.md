# One Inbox — Design

Date: 2026-11-21 · Working name: "One Inbox" (rename freely)

## Intent

A personal Android app so the owner only has to open **one app** to check messages from all their
chat apps (WhatsApp, Messenger, Instagram, Telegram, Discord, LinkedIn, X, and others), plus an
**AI catch-up** that summarizes everything unread, sorted by what needs their attention.

It is for one person and one phone: the owner's **Xiaomi 15T Pro** (HyperOS, Android). It is not a
product, will not be published to the Play Store, and will be sideloaded / installed via adb.

## Goals

1. Capture incoming chat messages from all installed chat apps, automatically, without logging in
   to any of those platforms.
2. Show them in one place, organized per app and per conversation, in a familiar chat view.
3. On demand, produce an AI summary of all unread messages, grouped by priority.
4. Never lose messages silently because the phone killed the app.

## Non-goals (v1)

- **Replying** from the app. Read-only for now; replying may come later (see Future).
- iPhone, other users, other devices, a web version, or a backend server.
- Importing message history from before the app was installed.
- Syncing read/unread state back to the original apps (impossible without their APIs).
- Media (photos, voice notes, videos): shown as a text placeholder such as "📷 Photo".
- Removing or hiding the original apps' notifications.

## Anti-goals (would make it a failure even if it works)

- Anything that logs in to the owner's accounts or acts like an unofficial client (bridges,
  scraping, session cookies). Account bans are the risk the owner chose to avoid.
- Sending any message content off the phone **without the owner tapping "Summarize"**.
- Silently missing messages: if capture stops (permission revoked, app killed), the owner must be
  told, visibly.

## Constraints

- **Capture = Android notifications only.** The app is a `NotificationListenerService`. It sees
  only what the chat apps post as notifications. Accepted consequences: muted chats and chats with
  notifications turned off are invisible; nothing from before install; long messages may be
  truncated by the source app; some apps collapse bursts into "5 new messages".
- **Xiaomi / HyperOS kills background apps aggressively.** Capture must survive this: the app needs
  Autostart enabled and battery saver set to "No restrictions", and onboarding must guide the owner
  there.
- **AI runs in the cloud (Claude API), only on tap.** The owner explicitly accepted that unread
  messages are sent to Anthropic when they tap Summarize. Cost should stay at a few cents a day for
  personal use.
- All data lives on the phone. There is no server.

## Approaches considered

| Approach | Trade-off | Verdict |
|---|---|---|
| **A. Native Kotlin + Jetpack Compose** | Notification listener, background reliability and Xiaomi permission flows are all first-class native code. Android-only, which is fine here. Same stack as the owner's `expense-tracker-kotlin`. | **Chosen** |
| B. Expo / React Native | The listener must still be a custom Kotlin native module, with captured messages bridged into JS. More glue, more places to drop messages while JS isn't running. UI would be faster only if React were preferred. | Rejected |
| C. Flutter | Notification-listener plugins are thinly maintained, and the owner doesn't use Flutter elsewhere. | Rejected |

Also rejected earlier, at the "how do messages get in" level:
- **Unofficial bridges (Beeper/Matrix-style)**: real history and replies, but they log in as the
  owner, need an always-on server, break when platforms change, and risk account flags. Not needed
  for a read-first, Android-only, personal tool.
- **On-device AI**: free and private, but weaker summaries and uncertain availability on Xiaomi.

## Decided by the owner

- Personal, single-user, own Android phone only (Xiaomi 15T Pro).
- Read-first; replying possibly later.
- Notification capture as the message source.
- Cloud AI (Claude) for summaries, sent only when Summarize is tapped.
- Native Kotlin + Compose.
- Two main screens, described below.

### Screen 1 — Inbox with an app-icon strip

A slim vertical strip of icons on the left (a full sidebar is too wide for a phone). Tapping an icon
shows that app's conversations on the right. Each icon shows its unread count.

```
┌────┬────────────────────────────┐
│ ✨ │ WhatsApp                   │
│ 📥 │────────────────────────────│
│────│ Mom              2:41 PM 3 │
│ WA │ Sunday dinner? Bring...    │
│ MSG│────────────────────────────│
│ IG │ Family group     1:15 PM 12│
│ TG │ Dad: photos from the...    │
│ DC │────────────────────────────│
│ in │ Jason           11:02 AM   │
│ X  │ ok see you then            │
└────┴────────────────────────────┘
 ✨ = Catch-up summary   📥 = "All": every app mixed, newest first
```

Tapping a conversation opens a read-only chat-bubble view of the captured messages, with sender
names in group chats.

Only apps that have actually sent at least one captured message appear in the strip.

### Screen 2 — Catch-up summary

Generated when the owner taps Summarize. Sorted by **what needs them**, not by app:

```
┌─────────────────────────────────┐
│ Catch-up · 62 unread · 6 apps   │
├─────────────────────────────────┤
│ ⚠ Needs your reply              │
│ • Mom (WhatsApp): asking if     │
│   you're coming Sunday dinner   │
│ • Alex (LinkedIn): wants a call │
│   time this week                │
├─────────────────────────────────┤
│ 💬 Worth knowing                │
│ • Team group (Telegram): launch │
│   moved to Friday               │
├─────────────────────────────────┤
│ 🔕 Low priority (31 messages)   │
│ • Memes, promos, chit-chat      │
└─────────────────────────────────┘
```

Every summary line links to its conversation.

## Proposed design (Claude's calls, owner may override)

### Capture

- `NotificationListenerService` with a **package allowlist** of known chat apps, editable in
  Settings. Default: `com.whatsapp`, `com.whatsapp.w4b`, `com.facebook.orca`,
  `com.instagram.android`, `org.telegram.messenger`, `com.discord`, `com.linkedin.android`,
  `com.twitter.android`. Settings also lists other installed apps that post notifications so the
  owner can add more (e.g. Slack, Signal, LINE, WeChat).
- Parse `Notification.MessagingStyle` first (per-message sender, text, timestamp; conversation
  title; group flag). Fall back to `EXTRA_TITLE` / `EXTRA_TEXT` when absent.
- **Conversation identity:** package + `shortcutId` when present, else package + conversation title
  (or title for 1:1 chats).
- **Dedupe:** chat apps re-post the same notification with an accumulating message list. Store each
  message once, keyed on (package, conversation, sender, timestamp, text hash).
- Ignore group-summary notifications (`FLAG_GROUP_SUMMARY`), ongoing/foreground-service
  notifications (calls, uploads), and "N new messages" placeholders when real messages are present.
- Keep the raw notification extras for a message in a debug column during development, to tune
  per-app parsing.

### Storage

- Room database: `apps`, `conversations`, `messages` (sender, text, timestamp, isGroup, unread flag,
  source package).
- **Unread means "not yet seen in One Inbox".** A message becomes read when its conversation is
  opened, or when the owner taps "Mark all as read" after a catch-up. (The original apps' read state
  is unknowable.)
- Retention: keep everything locally. Add a "delete older than…" setting only if storage becomes an
  issue.

### AI catch-up

- Tap Summarize → send all unread messages, grouped by app and conversation (with sender names and
  times), to the Claude Messages API directly from the phone. Ask for **structured JSON** with three
  buckets (`needs_reply`, `worth_knowing`, `low_priority`). Each item has a one-line summary and the
  conversation id it came from, so the UI can link to it.
- Use a current Claude model. Model choice, prompt wording and token limits are up to the builder:
  check the Claude API reference rather than guessing. If unread volume exceeds a sensible input
  budget, drop the oldest low-signal content first and say so in the summary header.
- Cache the last summary and show it with its timestamp, plus a "N new since" badge if more arrived.
- The API key is entered once in Settings and stored encrypted on the device (Android Keystore). It
  is not baked into the APK, so it can be rotated without a rebuild.

### Reliability (Xiaomi)

- First-launch onboarding, in order: grant notification access → enable Autostart (deep link into
  the MIUI/HyperOS security center, with a manual fallback instruction) → set battery saver to "No
  restrictions" → optionally lock the app in Recents.
- Health check, shown as a banner on the inbox: notification access revoked; listener disconnected
  (call `requestRebind` on `onListenerDisconnected`); or no capture in an unusually long time while
  the chat apps clearly have activity. The owner must never wonder whether capture is still working.

### Stack

Mirror `expense-tracker-kotlin` where it applies: Kotlin 2.2, Jetpack Compose + Material 3,
Navigation Compose, Hilt, kotlinx.serialization, OkHttp, DataStore, KSP. Add Room. No Supabase, no
Firebase, no backend. The owner builds and installs from Android Studio / adb.

### Edge cases (settled after review; Claude's calls)

- **Your own messages** that appear in a notification's message list are stored and shown, but
  never count as unread or go into "needs your reply".
- **Reading a message in the original app doesn't mark it read here.** Unread means "not seen in
  One Inbox". Accepted trade-off: the catch-up can flag "needs your reply" for something already
  answered in WhatsApp.
- **Xiaomi Dual Apps** (e.g. a second WhatsApp): conversation identity also includes the
  notification's user profile, so the two accounts never merge.
- **"Mark all as read"** marks only the messages that went into that summary, not ones that arrived
  after it.
- **Capture-gap check:** on reconnect and on a timer, compare the chat notifications currently
  showing on the phone with what was captured. A visible chat notification that wasn't captured
  raises the health banner.
- **Summary language** follows the language of the messages.

## Future (not v1, but don't block it)

- **Replies:** many chat notifications carry a `RemoteInput` "Reply" action. Storing a handle to
  the live notification's reply action would let v2 reply from inside One Inbox while that
  notification still exists, without any account login. Keep the capture layer able to hold onto
  that action.
- Per-app or per-conversation mute inside One Inbox.
- Scheduled catch-up (e.g. a daily summary notification). This would send content without a tap,
  so it needs the owner's explicit opt-in.

## Left to the builder

- Package name, app icon, theming, exact visual design within the two sketched screens.
- Per-app parsing quirks discovered on the real device.
- Claude model, prompt wording, JSON schema details, token budgets.
- Module/package structure, testing approach.
