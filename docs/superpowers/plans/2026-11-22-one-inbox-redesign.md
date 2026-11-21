# One Inbox UI Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Restyle all five One Inbox screens in Material 3 Expressive with palette A ("Violet night") and a collapsible app sidebar. Behavior, capture and the summary pipeline do not change.

**Architecture:** A shared design layer (`ui/theme` plus `ui/components`) holds the palette, the expressive theme and reusable pieces: avatar, app tile, count pill, section card. Each screen splits into a thin `XxxScreen(viewModel)` wrapper and a stateless `XxxContent(state, callbacks)`. Roborazzi screenshot tests render that stateless content with fake state on the JVM, so every screen can be checked as a PNG without navigating the phone. A few small ViewModel and repository additions supply the new data: sidebar state, totals, group previews, people count and "Open {App}".

**Tech Stack:** Kotlin 2.2, Compose BOM 2026.06.00 (material3 **1.4.0**), `MaterialExpressiveTheme`, `MotionScheme.expressive()`, Hilt, DataStore, Room, JUnit 4, Robolectric 4.18.1, and Roborazzi (latest stable), which is new and test-only.

**Spec:** `docs/superpowers/specs/2026-11-22-one-inbox-redesign-design.md` (read it; the color tables and screen descriptions there are binding).

## Global Constraints

- **No material3 upgrade.** Stay on BOM 2026.06.00 / material3 1.4.0. Do not use components it lacks: `LoadingIndicator`, `ButtonGroup`, `Flexible*AppBar`. Expressive APIs need `@OptIn(ExperimentalMaterial3ExpressiveApi::class)`.
- **Palette A always.** No dynamic color on any Android version. The dark scheme uses exactly the spec's token table; the light scheme is derived from the same violet/coral/blue seeds.
- **Readable colors.** Only paired color roles (`xContainer` with `onXContainer`). Keep `android:forceDarkAllowed=false`. Text contrast must be at least 4.5:1.
- **Phone constraints.** `minSdk 30`, portrait phone only.
- **Accessibility.** At least 48dp touch targets, and a `contentDescription` on every icon-only control.
- **No storage schema change.** Queries and ViewModel mapping may be added.
- **No behavior change** in capture, storage semantics, summary, or the Claude request.
- **Tests.** All existing tests (65) stay green. New logic gets unit tests, and every restyled screen gets a Roborazzi screenshot test.
- **Motion.** Springs come from `MaterialTheme.motionScheme`. Animated transforms use `graphicsLayer`. When `Settings.Global.ANIMATOR_DURATION_SCALE` is 0, motion becomes `snap()`.
- **Previews.** Every `XxxContent` gets an `@Preview(uiMode = UI_MODE_NIGHT_YES)` with fake state.
- **Commits.** Trailer `Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>`. Branch `feature/redesign`.

## Review Focus

1. **Long names and previews** (CJK titles, 60-character previews) must ellipsize on one line and never push the time or count off-screen. → Task 3 screenshot `inbox_longCjkAndPreview`.
2. **More than 7 apps in the rail** must scroll vertically, and the last tile must not be clipped. → Task 3 screenshot `inbox_manyApps` with 10 apps.
3. **Empty inbox on first run** (nothing captured yet) shows a friendly empty state, not a blank sheet. → Task 3 screenshot `inbox_empty`.
4. **An app with no icon** (uninstalled, or hidden by package visibility) shows a letter tile in the rail and Settings, and no badge on the avatar. → Task 1 test `avatarWithoutAppIconHasNoBadge` plus Task 3 screenshot rows using a null icon.
5. **Large font size** (font scale 1.3) must not clip the top bars, cards or buttons. → Tasks 3 and 5 add `fontScale = 1.3f` screenshot variants (`inbox_fontScale130`, `catchup_fontScale130`).

---

## File Structure

```
app/src/main/java/one/inbox/
  ui/theme/Color.kt            palette A dark + derived light ColorScheme, avatar pairs, sender colors, doneGreen
  ui/theme/Theme.kt            OneInboxTheme → MaterialExpressiveTheme (fixed palette, expressive motion, shapes, emphasized hero type)
  ui/theme/Motion.kt           motionEnabled(scale), rememberMotionEnabled()
  ui/components/Avatar.kt      Avatar(title, key, appIcon?, size) + avatarPairIndex(), initialsOf()
  ui/components/AppTile.kt     AppTile(label, icon?, selected, unread, size)
  ui/components/CountPill.kt   CountPill(count)
  ui/components/SectionCard.kt SectionCard(container, content, title?, trailing?)
  feature/inbox/…              InboxContent + collapsible rail, filter chip, FAB, restyled HealthBanner, EmptyInbox
  feature/conversation/…       ConversationContent: bubbles, day chips, open-app bar
  feature/catchup/…            CatchUpContent: hero, bucket cards, actions, empty/error card
  feature/onboarding/…         OnboardingContent: step cards
  feature/settings/…           SettingsContent: grouped cards
app/src/test/java/one/inbox/
  ui/…Test.kt                  pure-function tests for the theme and components
  screenshots/…ScreenshotTest.kt   Roborazzi tests; PNGs saved under app/build/outputs/roborazzi/
```

---

### Task 1: Design system (theme, motion, shared components, screenshot harness)

**Files:**
- Create:
  - `ui/theme/Color.kt`
  - `ui/theme/Motion.kt`
  - `ui/components/Avatar.kt`, `AppTile.kt`, `CountPill.kt`, `SectionCard.kt`
  - `app/src/test/java/one/inbox/ui/DesignSystemTest.kt`
  - `app/src/test/java/one/inbox/screenshots/ComponentsScreenshotTest.kt`
- Modify:
  - `ui/theme/Theme.kt`
  - `gradle/libs.versions.toml` and `app/build.gradle.kts` (Roborazzi plugin, `roborazzi`, `roborazzi-compose`, `roborazzi-junit-rule` as testImplementation; `robolectric.graphicsMode=NATIVE` via `testOptions.unitTests.all { it.systemProperty(...) }`)

**Interfaces — Produces:**
```kotlin
// Color.kt
val VioletNightDark: ColorScheme          // spec token table + outline #948F99, error #FFB4AB, errorContainer #93000A, onErrorContainer #FFDAD6
val VioletNightLight: ColorScheme         // derived from the same seeds
val AvatarPairs: List<Pair<Color, Color>> // the 6 spec pairs (container, content), in spec order
val SenderColors: List<Color>             // the 6 spec sender colors, in spec order
val DoneGreen: Color                      // #9FE0A8
// Theme.kt
@Composable fun OneInboxTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit)
// Motion.kt
fun motionEnabled(animatorScale: Float): Boolean          // false only when scale == 0f
@Composable fun rememberMotionEnabled(): Boolean          // reads Settings.Global.ANIMATOR_DURATION_SCALE (default 1f)
// Avatar.kt
fun avatarPairIndex(key: String): Int                     // Math.floorMod(key.hashCode(), 6)
fun senderColorIndex(sender: String): Int                 // Math.floorMod(sender.hashCode(), 6)
fun initialsOf(title: String): String                     // first grapheme (code point) of the trimmed title, uppercased; "?" when blank
@Composable fun Avatar(title: String, colorKey: String, appIcon: ImageBitmap?, size: Dp = 46.dp, modifier: Modifier = Modifier)
// AppTile.kt
@Composable fun AppTile(label: String, icon: ImageBitmap?, selected: Boolean, unread: Int, onClick: () -> Unit, size: Dp = 48.dp, modifier: Modifier = Modifier)
// CountPill.kt
@Composable fun CountPill(count: Int, modifier: Modifier = Modifier)   // "99+" above 99; primary / onPrimary
// SectionCard.kt
@Composable fun SectionCard(containerColor: Color, contentColor: Color, modifier: Modifier = Modifier, title: String? = null, trailing: (@Composable () -> Unit)? = null, content: @Composable ColumnScope.() -> Unit)   // 24dp corners
```
Theme details:
- `MaterialExpressiveTheme(colorScheme = if (dark) VioletNightDark else VioletNightLight, motionScheme = MotionScheme.expressive(), shapes = Shapes(small 8, medium 16, large 24, extraLarge 32 dp))`.
- Typography is the default, with `headlineLarge` overridden to Bold 32sp / -0.5sp letter spacing for the hero titles.
- Avatar: a circle in `AvatarPairs[avatarPairIndex(colorKey)]`, showing `initialsOf(title)`. When `appIcon != null`, an 18dp rounded (6dp) badge sits at the bottom-right with a 2dp ring in the surface color, scaled proportionally for other sizes.
- AppTile: a 16dp-corner tile showing the icon, or the first letter on `surfaceContainerHighest` when there's no icon. When selected: a 2.5dp `primary` outline offset 2dp, plus a 4×26dp `primary` bar 10dp to the left. When `unread > 0`: a badge at the top-right. The touch target is at least 48dp, and `contentDescription = "$label, $unread unread"`.

- [ ] **Step 1: Add Roborazzi and write the failing tests**
`DesignSystemTest`:
  - `avatarPairIndexIsStableAndInRange`: the same key always gives the same index; 200 random keys all land in 0..5.
  - `initialsOfHandlesLatinCjkEmojiAndBlank`: `"Alex Tan"→"A"`, `"陈明"→"陈"`, `"😀 party"→"😀"`, `"  "→"?"`.
  - `motionEnabledOnlyFalseAtZero`: 0f→false, 0.5f→true, 1f→true.
  - `darkSchemeMatchesSpecTokens`: assert `VioletNightDark.primary == Color(0xFFCDB8FF)`, `.primaryContainer == Color(0xFF4B2F92)`, `.tertiaryContainer == Color(0xFF6D2A1A)`, `.secondaryContainer == Color(0xFF1F4A6B)`, `.background == Color(0xFF141218)`.

`ComponentsScreenshotTest` (Robolectric, `@GraphicsMode(NATIVE)`, `@Config(sdk=[35], qualifiers="w360dp-h780dp-night-xxhdpi")`):
  - `components_gallery`: avatars in all 6 pairs, with and without a badge; AppTile selected and unselected, with icon, without icon, and with unread 3 and 120; CountPill 1/42/120; a SectionCard in each container.
  - `avatarWithoutAppIconHasNoBadge`: use a Compose UI test: `onNodeWithTag("avatar-badge").assertDoesNotExist()` when `appIcon = null`. Avatar tags its badge `"avatar-badge"`.
- [ ] **Step 2: Run them.** `./gradlew :app:testDebugUnitTest --tests "one.inbox.ui.*" --tests "one.inbox.screenshots.ComponentsScreenshotTest"` → FAIL (unresolved references).
- [ ] **Step 3: Implement** `Color.kt`, `Theme.kt`, `Motion.kt` and the four components.
- [ ] **Step 4: Run the tests and record screenshots.** `./gradlew :app:testDebugUnitTest -Proborazzi.test.record=true` → all PASS (65 existing + new). Open `app/build/outputs/roborazzi/components_gallery.png` and check it visually against the spec colors. If native graphics can't render on this Windows machine, report BLOCKED with the error; don't silently drop screenshots.
- [ ] **Step 5: Commit** — `feat(ui): violet night expressive theme and shared components`

---

### Task 2: Data for the new UI (sidebar state, totals, group previews, conversation header, open-app)

**Files:**
- Modify:
  - `data/InboxRepository.kt` (preview text)
  - `data/settings/SettingsStore.kt` (sidebar flag)
  - `feature/inbox/InboxViewModel.kt`
  - `feature/conversation/ConversationViewModel.kt`
- Test:
  - `InboxRepositoryTest.kt`, `SettingsStoreTest.kt`, `InboxViewModelTest.kt`
  - new `feature/conversation/ConversationViewModelTest.kt`

**Interfaces — Produces:**
```kotlin
// InboxRepository.save — preview rule (pure helper, internal for tests)
internal fun previewFor(isGroup: Boolean, sender: String, isFromMe: Boolean, text: String): String
//   group → "$sender: $text" (sender is already "You" for own); 1:1 own → "You: $text"; 1:1 incoming → text
// SettingsStore
val sidebarOpen: Flow<Boolean>                  // default true
suspend fun setSidebarOpen(open: Boolean)
// InboxUiState gains:
val sidebarOpen: Boolean = true, val totalUnread: Int = 0, val appsWithUnread: Int = 0
// InboxViewModel gains:
fun toggleSidebar()                             // flips and persists sidebarOpen
fun icon(packageName: String): ImageBitmap?     // already exists, keep
// ConversationUiState gains:
val packageName: String = "", val peopleCount: Int = 0, val canOpenApp: Boolean = false
// ConversationViewModel gains:
fun openAppIntent(): Intent?                    // context.packageManager.getLaunchIntentForPackage(packageName)
```
`peopleCount` is the number of distinct non-own senders (groups only; 0 for 1:1). `canOpenApp = appInfo.isLaunchable(packageName)`. Get the launch intent through `AppInfoProvider` by adding `fun launchIntent(pkg: String): Intent?` there; update the existing fakes.

- [ ] **Step 1: Write the failing tests**
  - `previewFor` cases: (group, "Dad", false, "photos") → `"Dad: photos"`; (group, "You", true, "ok") → `"You: ok"`; (1:1, "Jason", false, "hi") → `"hi"`; (1:1, "You", true, "see you") → `"You: see you"`.
  - `InboxRepositoryTest.groupPreviewIsPrefixedWithSender`: save a group message from "Dad" → `conversation.preview == "Dad: photos"`.
  - `SettingsStoreTest.sidebarDefaultsOpenAndPersists`.
  - `InboxViewModelTest.totalsAndSidebarToggle`: 2 apps with unread 3 and 1, plus an app with 0 → `totalUnread == 4`, `appsWithUnread == 2`. `toggleSidebar()` → `sidebarOpen == false`, persisted in the store.
  - `ConversationViewModelTest.groupHeaderCountsDistinctSendersAndOpenApp`: messages from Mom, Dad, Mom and You (own) → `peopleCount == 2`. The fake `isLaunchable` returns true → `canOpenApp == true`. For 1:1, `peopleCount == 0`.
- [ ] **Step 2: Run them** → FAIL.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run** `./gradlew :app:testDebugUnitTest :app:assembleDebug` → all green.
- [ ] **Step 5: Commit** — `feat: data for redesigned inbox and conversation header`

---

### Task 3: Inbox screen (collapsible rail, list sheet, rows, FAB, health card, empty state)

**Files:**
- Modify: `feature/inbox/InboxScreen.kt`, `feature/inbox/AppRail.kt`, `feature/inbox/HealthBanner.kt`, `navigation/NavGraph.kt` (the FAB opens CatchUp; remove the ✨ rail entry)
- Create: `feature/inbox/InboxContent.kt`, `feature/inbox/EmptyInbox.kt`
- Test: `app/src/test/java/one/inbox/screenshots/InboxScreenshotTest.kt`

**Interfaces:**
- Consumes: Task 1 components and theme; Task 2 `InboxUiState` (`sidebarOpen`, `totalUnread`, `appsWithUnread`) and `toggleSidebar()`.
- Produces: `@Composable fun InboxContent(state: InboxUiState, icon: (String) -> ImageBitmap?, label: (String) -> String, onSelect: (String?) -> Unit, onToggleSidebar: () -> Unit, onOpenConversation: (String) -> Unit, onCatchUp: () -> Unit, onSettings: () -> Unit, onFixHealth: () -> Unit, motionEnabled: Boolean = true)`. `InboxScreen` wires the ViewModel into it.

Layout (spec "Inbox" and "Sidebar details"):
- **Top bar:** ☰ (contentDescription "Show apps" or "Hide apps"), a Bold 32sp "Inbox", and settings ⚙. When the rail is hidden, a tappable filter chip ("All apps" or the app label, primaryContainer) sits next to the title and opens the rail.
- **Subtitle:** "{totalUnread} unread across {appsWithUnread} apps". It says "All caught up" when totalUnread is 0, and uses singular forms for 1.
- **Rail:** 68dp wide and vertically scrollable. An "All" tile, then app tiles. Shows and hides with `AnimatedVisibility` using an expressive spring slide plus fade, or snaps when motion is disabled.
- **List sheet:** `surface`, with the top-left corner at 28dp when the rail shows and both top corners at 28dp when hidden.
- **Rows:** Avatar (46dp, colorKey = conversation key, app badge from `icon(packageName)`); a name row with the time on the right (`primary` and bold when unread); a preview row with a CountPill. Previews, names and titles ellipsize to one line.
- **FAB:** an extended FAB "✨ Catch up · {totalUnread}", just "✨ Catch up" when 0, in primaryContainer. It collapses to the ✨ icon while the list scrolls down and expands when scrolling up or at the top.
- **Health card:** shown above the rows, using `errorContainer`, an icon and the existing copy and action.
- **EmptyInbox:** a centered illustration-style icon, "No messages yet", and "Chats from your apps will appear here as notifications arrive."

- [ ] **Step 1: Write the failing screenshot tests** (all at `w360dp-h780dp-night-xxhdpi`, with fake state and fake icons, some null):
  - `inbox_railOpen`: 6 apps, 6 rows, unread mix, one group row with "Dad: …".
  - `inbox_railHidden`: same, with the filter chip "WhatsApp".
  - `inbox_health`: the NoAccess banner.
  - `inbox_longCjkAndPreview`: title "陈明的家庭群组（大家庭）" and a 60-character preview.
  - `inbox_manyApps`: 10 apps.
  - `inbox_empty`.
  - `inbox_fontScale130`: the railOpen state at `fontScale = 1.3f`.

  Also a Compose UI test `filterChipReopensRail`: with sidebar hidden, clicking the chip calls `onToggleSidebar`.
- [ ] **Step 2: Run them** → FAIL (no `InboxContent`).
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Record and run.** `./gradlew :app:testDebugUnitTest -Proborazzi.test.record=true` → all PASS. Then open each `inbox_*.png` and compare it with the spec and the mockup description. Fix any clipping or overlap before committing.
- [ ] **Step 5: Device check.** `./gradlew :app:installDebug` on the Poco (`adb -s bf5381fc`). Then:
  - post `adb shell cmd notification post -S messaging --conversation "'Family group'" --message "'Mom:Sunday dinner?'" --message "'Dad:Photos from the trip'" fam "'x'"`;
  - launch with `am start -n one.inbox/.MainActivity`;
  - `exec-out screencap -p` to a file in the scratchpad, and view it.

  Input injection is blocked on this phone, so only the landing screen is checked here.
- [ ] **Step 6: Commit** — `feat(ui): redesigned inbox with collapsible app rail and catch-up FAB`

---

### Task 4: Conversation screen

**Files:**
- Modify: `feature/conversation/ConversationScreen.kt`
- Create: `feature/conversation/ConversationContent.kt`
- Test: `screenshots/ConversationScreenshotTest.kt`

**Interfaces:**
- Consumes: Task 1 (`Avatar`, `SenderColors`, `senderColorIndex`), and Task 2 `ConversationUiState` (`packageName`, `peopleCount`, `canOpenApp`) and `openAppIntent()`.
- Produces: `@Composable fun ConversationContent(state: ConversationUiState, appIcon: ImageBitmap?, onBack: () -> Unit, onOpenApp: () -> Unit)`.

Layout (spec "Conversation screen"):
- **Top bar:** ← (contentDescription "Back"), a 38dp Avatar, the title, and the subtitle. The subtitle is a 10dp app-colored dot (the app icon, or a primary dot) plus "{appName} · {peopleCount} people" for groups, or "{appName}" for 1:1.
- **Day chips:** "Today", "Yesterday", or "MMM d".
- **Incoming bubbles:** `surfaceContainerHigh` with 20dp corners and a 6dp bottom-left corner.
- **Own bubbles:** `primaryContainer` / `onPrimaryContainer` with a 6dp bottom-right corner, aligned right.
- **Bubble details:** max width 76%. The time is in 10sp at 60% alpha, inside the bubble at the bottom-right. In groups, the sender name sits above incoming bubbles in `SenderColors[senderColorIndex(sender)]`. Consecutive messages from the same sender within 2 minutes are spaced 2dp apart and show the name only once; otherwise the gap is 6dp.
- **Bottom bar:** shown only when `canOpenApp`. An outlined full-width button "Open {appName} ↗" that calls `onOpenApp`. The screen launches `openAppIntent()`, catching `ActivityNotFoundException`.
- **Scrolling:** auto-scrolls to the bottom on open, and on new messages only if already at the bottom. This replaces the old always-scroll behavior.

- [ ] **Step 1: Write the failing screenshot tests**:
  - `conversation_group`: Mom, Dad, You and Lily messages over two days, including CJK and emoji.
  - `conversation_direct`: 1:1, with no "people" in the subtitle.
  - `conversation_noOpenApp`: `canOpenApp = false`, so there's no bottom bar.
- [ ] **Step 2: Run them** → FAIL.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Record, run all tests, and inspect the PNGs.**
- [ ] **Step 5: Commit** — `feat(ui): redesigned conversation view with bubbles and open-app button`

---

### Task 5: Catch-up screen

**Files:**
- Modify: `feature/catchup/CatchUpScreen.kt`
- Create: `feature/catchup/CatchUpContent.kt`
- Test: `screenshots/CatchUpScreenshotTest.kt`

**Interfaces:**
- Consumes: Task 1 (`SectionCard`, `Avatar`), and the existing `CatchUpUiState(latest, newSince, running, message, labels: Map<String, ItemLabel>)` with `ItemLabel(title, appName)`. App icons come from a new `CatchUpViewModel.icon(packageName)` delegating to `AppInfoProvider`. The package is derived from the key the same way `labelsFor` does.
- Produces: `@Composable fun CatchUpContent(state: CatchUpUiState, icon: (conversationKey: String) -> ImageBitmap?, onBack: () -> Unit, onSummarize: () -> Unit, onMarkAllRead: () -> Unit, onOpenConversation: (String) -> Unit, onOpenSettings: () -> Unit, motionEnabled: Boolean = true)`.

Layout (spec "Catch-up screen"):
- **Hero:** a back arrow, then "Catch-up ✨" in Bold 32sp. The subtitle is "{unreadCount} unread · {appCount} apps · {relative time}", or "Summarize your unread messages" before the first summary. A "{n} new since" chip appears when `newSince > 0`.
- **Cards** (16dp side margins, 10dp gaps):
  - "⚠ Needs your reply": tertiaryContainer.
  - "💬 Worth knowing": secondaryContainer.
  - "🔕 Low priority · N": surfaceContainerHigh, collapsed by default. Its header row is tappable with a chevron that rotates 180° on a spring and contentDescription "Expand" or "Collapse".

  Empty buckets aren't rendered. Each item is a 32dp Avatar with an app badge, plus "**{title}** {summary}" (the title is bold; with no title, show appName; with neither, the summary only). Tapping an item opens the conversation.
- **Running:** a `CircularProgressIndicator` inside the Summarize button, which is disabled.
- **Messages** (no key, nothing unread, refused, failed) appear in a SectionCard. It uses `errorContainer` for failure and refusal, and `surfaceContainerHigh` for no key and nothing unread, each with a leading icon and the existing copy. The no-key card has a "Open Settings" text button.
- **Actions:** pinned at the bottom: a tonal "Mark all read" (only when `latest != null`) and a filled "Summarize again", or "Summarize" before the first summary.

- [ ] **Step 1: Write the failing screenshot tests**:
  - `catchup_full`: 2 needs-reply, 2 worth-knowing and 31 low-priority items, low collapsed.
  - `catchup_lowExpanded`.
  - `catchup_firstRun`: no latest.
  - `catchup_running`.
  - `catchup_noKey`.
  - `catchup_failed`.
  - `catchup_fontScale130`.

  Also a UI test `lowPriorityTogglesOnTap`: the items are hidden, appear after a tap on the header, and hide again after a second tap.
- [ ] **Step 2: Run them** → FAIL.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Record, run all tests, and inspect the PNGs.**
- [ ] **Step 5: Commit** — `feat(ui): redesigned catch-up with bucket cards`

---

### Task 6: Onboarding and Settings screens

**Files:**
- Modify: `feature/onboarding/OnboardingScreen.kt`, `feature/settings/SettingsScreen.kt`
- Create: `feature/onboarding/OnboardingContent.kt`, `feature/settings/SettingsContent.kt`
- Test: `screenshots/SetupScreensScreenshotTest.kt`

**Interfaces:**
- Consumes: Task 1 (`SectionCard`, `AppTile`, `DoneGreen`). Also the existing `SettingsUiState(hasKey, keyMessage, apps: List<AppToggle>)`, `SettingsViewModel.icon/saveKey/clearKey/setAllowed`, and the onboarding status values already computed in `OnboardingScreen` (access, battery, autostart, recents).
- Produces:
  - `@Composable fun OnboardingContent(steps: OnboardingSteps, onOpen: (OnboardingStep) -> Unit, onToggle: (OnboardingStep, Boolean) -> Unit, onDone: () -> Unit)`, where `enum class OnboardingStep { Access, Battery, Autostart, Recents }` and `data class OnboardingSteps(val access: Boolean, val batteryOk: Boolean, val batteryConfirmed: Boolean, val autostart: Boolean, val recents: Boolean)`.
  - `@Composable fun SettingsContent(state: SettingsUiState, icon: (String) -> ImageBitmap?, onBack: () -> Unit, onSaveKey: (String, () -> Unit) -> Unit, onClearKey: () -> Unit, onSetAllowed: (String, Boolean) -> Unit, onRunSetup: () -> Unit)`.

  Keep the existing intent-launch logic (`launchFirst`, XiaomiSettings) in the `OnboardingScreen` wrapper.

Layout (spec "Claude's calls"):
- **Onboarding:**
  - A "Set up One Inbox" hero, Bold 32sp, with the subtitle "4 quick steps so no message slips through".
  - One step card per step, in `surfaceContainerHigh` with 20dp corners. Each card has a 36dp numbered tonal circle (it shows ✓ in `DoneGreen` when done), the title, the status line ("Done ✓" in DoneGreen, or the hint in onSurfaceVariant), its checkbox where applicable, and a tonal "Open" button.
  - Keep the existing restricted-settings hint under the Access step.
  - A full-width filled "Done" button, enabled iff access is granted.
- **Settings:**
  - Top bar with ← and "Settings". Three SectionCards (surfaceContainerHigh):
    - **"Claude API key":** the masked field plus Save/Clear, the status line ("Key saved" or an error in `error`), and a one-line note "Only sent to Claude when you tap Summarize".
    - **"Apps to capture":** rows with a 40dp AppTile (unselected, no badge), the label, and a Switch. The whole row is the touch target.
    - **"Setup":** a "Run setup again" row with a chevron.

- [ ] **Step 1: Write the failing screenshot tests**:
  - `onboarding_fresh`: nothing done.
  - `onboarding_partial`: access and battery done.
  - `settings_withKey`: 6 apps, 2 of them with no icon.
  - `settings_noKey_error`: keyMessage = "Couldn't save the key".
- [ ] **Step 2: Run them** → FAIL.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Record, run the full suite and `:app:assembleDebug`, and inspect the PNGs.**
- [ ] **Step 5: Device check.** Run `installDebug` on the Poco, launch it, and screenshot the landing screen. Do **not** clear app data (the owner's API key may be stored), so onboarding and settings are verified by screenshots only.
- [ ] **Step 6: Commit** — `feat(ui): redesigned onboarding and settings`
