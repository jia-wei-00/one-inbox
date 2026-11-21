# One Inbox: UI Redesign

Date: 2026-11-22 · Builds on: `2026-11-21-one-inbox-design.md`

## Intent

The owner finds the current UI "very ugly". It uses default Material colors, a flat grey side strip, generic letter icons, no avatars, and default spacing. The goal is to rebuild the look of every screen so the app feels polished and pleasant to open every day. Features and behavior stay the same.

The owner asked Claude to find a good Kotlin/Compose design skill online and use it. Claude chose and read two skills:
- **m3-expressive** (skills.sh, ahmed3elshaer/everything-claude-code-mobile) for the look: theme, shapes, motion and emphasized type.
- **mobile-android-design** (wshobson/agents) for structure and accessibility: 48dp touch targets, content descriptions, paired color roles.

Both were reviewed. They contain only design and API guidance, with no scripts or install steps. Their files are in the session scratchpad, not the repo.

Mockups the owner reviewed are in `.superpowers/brainstorm/535-1795280160/content/new-look.html` and `sidebar-collapse.html` (git-excluded).

## Goals

1. Restyle all five screens (Inbox, Conversation, Catch-up, Onboarding, Settings) in **Material 3 Expressive** with the owner's chosen **palette A, "Violet night"**.
2. Make the app sidebar **collapsible** (owner's choice, option A): ☰ toggles the icon strip between shown and fully hidden.
3. Look right on the Poco X3 Pro (Android 11, MIUI, night mode on), which is the current test phone. Also look right on the Xiaomi 15T Pro (HyperOS, Android 15+).

## Non-goals

- No new data features: no search, sending, pinning or archiving.
- No change to capture, storage, the summary pipeline or the Claude request.
- No tablet or foldable layouts; this is phone portrait only.
- No light-mode palette work beyond a sensible light counterpart. Both phones run in night mode.

## Anti-goals

- **Anything less readable than today.** Every text/background pair uses Material's paired roles (`primaryContainer` + `onPrimaryContainer` and so on), so MIUI/HyperOS force-dark can't wash it out. `forceDarkAllowed=false` stays.
- **Motion that feels laggy or janky on the Poco.** Springs come from `MaterialTheme.motionScheme`, transforms use `graphicsLayer`, and motion respects reduced-motion settings.
- **Breaking existing tests or behavior.** All 65 unit tests must still pass.

## Constraints

- Compose BOM 2026.06.00 resolves to **material3 1.4.0**. It has `MaterialExpressiveTheme` and `MotionScheme.expressive()`, but **not** `LoadingIndicator`, `ButtonGroup`, `FlexibleTopAppBar` and some other expressive components. **Decision (Claude's): stay on 1.4.0 stable** and build the look with what it has. Do not upgrade to an alpha. Expressive APIs need `@OptIn(ExperimentalMaterial3ExpressiveApi::class)`.
- `minSdk 30`. Dynamic color exists only on Android 12+.
- The palette is fixed: the app always uses palette A, even on Android 12+, so it looks the same on both phones. **(Claude's call: the owner picked a specific look, and wallpaper colors would replace it.)**

## Approaches considered

| Approach | Trade-off | Verdict |
|---|---|---|
| **A. Material 3 Expressive restyle** (m3-expressive + mobile-android-design) | Personality: bold shapes, rich color, spring motion. A few newer components are missing on 1.4.0. | **Chosen** (Claude's pick; the owner agreed) |
| B. Plain Material 3 polish | Safe and fast, but still looks like a stock Google sample, which is the current complaint. | Rejected |
| C. Imitate WhatsApp/Telegram | Familiar, but fights the components and always reads as a copy. | Rejected |

## Decided with the owner

- **Palette A "Violet night"** (dark). Tokens from the mockup:

  | Role | Color | Role | Color |
  |---|---|---|---|
  | background | `#141218` | surface (list sheet) | `#1D1B22` |
  | surfaceContainerHigh | `#26232D` | surfaceContainerHighest | `#2F2B38` |
  | onSurface | `#E8E0F0` | onSurfaceVariant | `#B3AAC2` |
  | primary | `#CDB8FF` | onPrimary | `#331A6B` |
  | primaryContainer | `#4B2F92` | onPrimaryContainer | `#EBDDFF` |
  | tertiary (Needs reply) | `#FFB4A0` | tertiaryContainer | `#6D2A1A` |
  | onTertiaryContainer | `#FFDBD1` | secondary | `#A8D3FF` |
  | secondaryContainer | `#1F4A6B` | onSecondaryContainer | `#D1E6FF` |

  The builder derives a matching light scheme; it is not reviewed.
- **Collapsible sidebar, option A:**
  - ☰ in the inbox top bar toggles the app icon strip between shown and fully hidden. Hiding animates with a spring slide plus fade.
  - When hidden, the chat list goes full width and a chip next to the title shows the current filter ("All apps", "WhatsApp", …).
  - The open/closed state is remembered across launches (DataStore).
- **Inbox** (mockup screen 1):
  - Large bold "Inbox" title, with "N unread across M apps" under it.
  - The chat list sits on a rounded `surface` sheet (top-left corner 28dp when the strip is shown, both top corners when hidden).
  - The strip: 48dp rounded-square tiles (16dp corners) using the **real app icons**. "All" is a tonal tile. The selected tile gets a `primary` outline and a 4dp left indicator bar. Unread badges are `primary`/`onPrimary` with a background-colored ring.
- **Conversation rows:**
  - A 46dp circular avatar with initials, colored from a fixed set of 6 pastel pairs picked by hashing the conversation key.
  - The source app's icon as an 18dp rounded badge on the avatar's bottom-right.
  - Name in bold when unread. Time in `primary` when unread. A one-line preview: `onSurface` when unread, `onSurfaceVariant` otherwise. An unread count pill.
  - In group chats, the preview is prefixed with the sender ("Dad: …").
- **Catch-up entry moves to an extended FAB** "✨ Catch up · N" at the bottom right of the inbox, using primaryContainer colors. It shrinks to an icon-only FAB while scrolling down. The ✨ tile is removed from the strip.
- **Conversation screen** (mockup screen 3):
  - Top bar with back arrow, a 38dp avatar, the title, and a subtitle "● WhatsApp · N people" using the app's color dot. "N people" counts distinct senders.
  - Day separator chips.
  - Incoming bubbles use `surfaceContainerHigh` with 20dp corners, but the bottom-left corner is 6dp. Own bubbles use `primaryContainer` with a 6dp bottom-right corner. Time is shown small inside the bubble.
  - In groups, the sender's name appears in a per-sender pastel color.
  - A bottom bar has an outlined button "Open WhatsApp ↗" (the app's name). It launches the app via `getLaunchIntentForPackage`. **(Claude's call: Android can't deep-link to a specific chat in general, so the label says "Open", not "Reply in".)** It is hidden if the app can't be launched.
- **Catch-up screen** (mockup screen 4):
  - A big emphasized "Catch-up ✨" hero title, with "N unread · M apps · {relative time}" under it.
  - Three rounded (24dp) cards:
    - "⚠ Needs your reply": tertiaryContainer.
    - "💬 Worth knowing": secondaryContainer.
    - "🔕 Low priority · N": surfaceContainerHigh, collapsed, with a rotating chevron. It expands with a spring.
  - Each item has a 32dp avatar (with app badge), the **bold name**, and the summary.
  - Bottom actions: a tonal "Mark all read" and a filled "Summarize again". Before the first summary, a single filled "Summarize" is shown.
  - While running, a progress indicator shows; 1.4.0 has no `LoadingIndicator`.
  - Empty states and errors (no key, nothing unread, refused, failed) appear as a friendly card with an icon, using the existing copy.

## Claude's calls (owner may override)

- **Onboarding** gets the same treatment:
  - A large "Set up One Inbox" title.
  - Each step is a rounded `surfaceContainerHigh` card with a numbered tonal circle, a status line (a green-tinted "Done ✓" / `onSurfaceVariant` "Needed"), its checkbox, and a tonal "Open" button.
  - A full-width filled "Done" button.
- **Settings** is grouped into rounded cards:
  - "Claude API key": a masked field plus Save/Clear.
  - "Apps to capture": a row per app with the real icon, name and a Switch.
  - "Setup": "Run setup again".
- **Typography:** the default font with Expressive emphasized styles for hero titles only (Inbox, Catch-up, Set up), per the skill's "don't overuse emphasis" rule.
- **Health banner:** a rounded `errorContainer` card at the top of the list with an icon and its action button.
- **Real app icons** come from `AppInfoProvider.icon`. The letter tile is the fallback when there is no icon. Brand colors are not hard-coded.
- **Light scheme:** derived from the same seed (violet primary, coral tertiary, blue secondary).
- **Accessibility:** every icon-only control has a contentDescription, every touch target is at least 48dp, and text meets 4.5:1 contrast.
- **Previews:** each screen composable gets a `@Preview` with fake data.

## Settled after review (Claude's calls)

- **Data scope:** no Room schema change. New **queries** or **ViewModel mapping** are allowed: counts, distinct senders, preview formatting. Everything the UI needs already exists:
  - `MessageEntity.isFromMe` drives own bubbles. The owner's own messages are captured from MessagingStyle notifications.
  - `ConversationRow.isGroup` and `MessageEntity.sender` drive group details.
  - `AppSummary.unreadCount` per app gives the inbox subtitle: N = sum, M = apps with unread > 0.
  - `CatchUp` buckets already carry `conversationKey`, and `CatchUpViewModel` resolves title and app.
  - `StoredCatchUp.generatedAt` gives the relative time.
- **Group preview "Dad: …":** produced by `InboxRepository.save` when it builds the conversation preview for a group: `"$sender: $text"`, or `"You: $text"` for own messages. The 1:1 preview is unchanged. This changes the stored preview string, not the schema.
- **Conversation subtitle:**
  - groups: "● {App} · N people" (N = distinct non-own senders);
  - 1:1: "● {App}".
- **FAB count:** total unread, the same number as the inbox subtitle. Hidden when 0, leaving a plain "✨ Catch up".
- **Sidebar details:**
  - The strip is shown on first launch.
  - Hiding it keeps the current filter.
  - The filter chip is tappable and re-opens the strip.
  - The unread total stays visible in the subtitle.
- **Missing app icon:** the strip and Settings rows use the letter tile. The avatar's app badge is omitted.
- **"Open {App}":**
  - Computed in the ViewModel from `getLaunchIntentForPackage`; hidden when it returns null.
  - The `<queries>` launcher entry already exists in the manifest.
  - Multiple launchers: Android picks the default.
- **Reduced motion:** the system animator duration scale (`Settings.Global.ANIMATOR_DURATION_SCALE` == 0) means snap instead of spring.
- **Extra palette values** (from the approved mockups):
  - Avatar pairs (container/content):
    - `#FFD9E2`/`#6E2240`
    - `#D3F5D6`/`#1D5A2A`
    - `#D6E3FF`/`#1B3D7A`
    - `#FFE0B8`/`#6B3D00`
    - `#E9DDFF`/`#47307A`
    - `#C9F1EF`/`#0F4D49`
  - Group sender name colors: `#FFB1C8`, `#9FE0A8`, `#A8C8FF`, `#FFD08A`, `#D7BCFF`, `#8EE6DE`, chosen by hashing the sender.
  - outline `#948F99`, error `#FFB4AB`, errorContainer `#93000A`, onErrorContainer `#FFDAD6`.
  - "Done ✓" green: `#9FE0A8`.

## Left to the builder

- Exact spacing values, animation spring choices within `motionScheme`, and file/package structure for shared components (`ui/components/`: Avatar, AppBadge, SectionCard, …).
- How the FAB shrinks on scroll (e.g. `LazyListState` scroll direction).
- Test approach. ViewModel logic for the sidebar preference (DataStore flag) should be unit-tested; visual work is verified by screenshots on the Poco.
