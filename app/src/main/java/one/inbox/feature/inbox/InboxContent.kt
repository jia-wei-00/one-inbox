package one.inbox.feature.inbox

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuOpen
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import one.inbox.capture.CaptureHealth
import one.inbox.data.db.ConversationRow
import one.inbox.ui.components.Avatar
import one.inbox.ui.components.CountPill
import one.inbox.ui.theme.ExpressiveMotion
import one.inbox.ui.theme.OneInboxTheme

private val SheetCorner = 28.dp

/** "7 unread across 3 apps", singular for one app, or "All caught up" when nothing is unread. */
fun inboxSubtitle(totalUnread: Int, appsWithUnread: Int): String =
    if (totalUnread == 0) {
        "All caught up"
    } else {
        "$totalUnread unread across $appsWithUnread ${if (appsWithUnread == 1) "app" else "apps"}"
    }

/** The catch-up FAB text (after the ✨): the unread total when there is any. */
fun catchUpLabel(totalUnread: Int): String =
    if (totalUnread > 0) "Catch up · $totalUnread" else "Catch up"

/** The filter chip shown while the rail is hidden: the selected app's label, or "All apps". */
fun filterChipLabel(selected: String?, label: (String) -> String): String =
    selected?.let(label) ?: "All apps"

/**
 * The inbox: a large title with the unread summary, the collapsible app rail, the conversation
 * list on a rounded sheet, the capture-health card and the extended catch-up FAB.
 */
@Composable
fun InboxContent(
    state: InboxUiState,
    icon: (String) -> ImageBitmap?,
    label: (String) -> String,
    onSelect: (String?) -> Unit,
    onToggleSidebar: () -> Unit,
    onOpenConversation: (String) -> Unit,
    onCatchUp: () -> Unit,
    onSettings: () -> Unit,
    onFixHealth: () -> Unit,
    motionEnabled: Boolean = true,
    onDelete: (String) -> Unit = {},
    onUndoDelete: () -> Unit = {},
    onUndoDismissed: () -> Unit = {},
) {
    // App icons are cached app-wide, per package and at display size, by AppInfoProvider.
    val iconOf = icon

    val listState = rememberLazyListState()
    var collapsedByScroll by remember { mutableStateOf(false) }
    val fabScroll = remember {
        object : NestedScrollConnection {
            // Decide from what the list actually consumed: a drag on a list that can't scroll
            // (e.g. two conversations) consumes nothing and leaves the FAB alone.
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (consumed.y < -1f) collapsedByScroll = true else if (consumed.y > 1f) collapsedByScroll = false
                return Offset.Zero
            }
        }
    }
    val atTop by remember {
        derivedStateOf { listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0 }
    }
    // Always expanded at the top of the list, and whenever the list doesn't scroll at all.
    val fabExpanded = !collapsedByScroll || atTop || !listState.canScrollForward && !listState.canScrollBackward
    LaunchedEffect(atTop) { if (atTop) collapsedByScroll = false }

    // One Undo at a time: a newer delete replaces (and dismisses) the previous snackbar.
    val snackbars = remember { SnackbarHostState() }
    val deleted = state.deleted
    LaunchedEffect(deleted) {
        if (deleted == null) return@LaunchedEffect
        val result = snackbars.showSnackbar(
            message = "Deleted ${deleted.conversation.title}",
            actionLabel = "Undo",
            duration = SnackbarDuration.Short,
        )
        if (result == SnackbarResult.ActionPerformed) onUndoDelete() else onUndoDismissed()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0),
        topBar = {
            InboxTopBar(
                state = state,
                chipLabel = filterChipLabel(state.selected, label),
                chipIcon = state.selected?.let(iconOf),
                onToggleSidebar = onToggleSidebar,
                onSettings = onSettings,
                motionEnabled = motionEnabled,
            )
        },
        snackbarHost = { SnackbarHost(snackbars, Modifier.navigationBarsPadding()) },
        floatingActionButton = {
            CatchUpFab(if (state.loaded) state.totalUnread else 0, fabExpanded, onCatchUp, Modifier.navigationBarsPadding())
        },
    ) { padding ->
        Row(Modifier.padding(padding).padding(top = 12.dp).fillMaxSize()) {
            // Recreated once when the first real state arrives, so the rail and sheet corner start at
            // the stored value instead of animating away from the default; later toggles animate.
            key(state.loaded) {
                RailAndSheet(state, listState, iconOf, fabScroll, onSelect, onOpenConversation, onFixHealth, onDelete, motionEnabled)
            }
        }
    }
}

@Composable
private fun RowScope.RailAndSheet(
    state: InboxUiState,
    listState: LazyListState,
    iconOf: (String) -> ImageBitmap?,
    fabScroll: NestedScrollConnection,
    onSelect: (String?) -> Unit,
    onOpenConversation: (String) -> Unit,
    onFixHealth: () -> Unit,
    onDelete: (String) -> Unit,
    motionEnabled: Boolean,
) {
    val railOpen = state.loaded && state.sidebarOpen
    AnimatedVisibility(
        visible = railOpen,
        enter = expandHorizontally(ExpressiveMotion.defaultSpatialSpec(motionEnabled), Alignment.Start) +
            slideInHorizontally(ExpressiveMotion.defaultSpatialSpec(motionEnabled)) { -it } +
            fadeIn(ExpressiveMotion.defaultEffectsSpec(motionEnabled)),
        exit = shrinkHorizontally(ExpressiveMotion.defaultSpatialSpec(motionEnabled), Alignment.Start) +
            slideOutHorizontally(ExpressiveMotion.defaultSpatialSpec(motionEnabled)) { -it } +
            fadeOut(ExpressiveMotion.fastEffectsSpec(motionEnabled)),
    ) {
        AppRail(state.rail, state.selected, state.totalUnread, iconOf, onSelect)
    }
    val topEnd by animateDpAsState(
        if (railOpen) 0.dp else SheetCorner,
        ExpressiveMotion.defaultEffectsSpec(motionEnabled),
        label = "sheetCorner",
    )
    Surface(
        Modifier.weight(1f).fillMaxHeight().nestedScroll(fabScroll),
        shape = RoundedCornerShape(topStart = SheetCorner, topEnd = topEnd.coerceAtLeast(0.dp)),
        color = MaterialTheme.colorScheme.surface,
    ) {
        InboxList(state, listState, iconOf, onOpenConversation, onFixHealth, onDelete)
    }
}

@Composable
private fun InboxTopBar(
    state: InboxUiState,
    chipLabel: String,
    chipIcon: ImageBitmap?,
    onToggleSidebar: () -> Unit,
    onSettings: () -> Unit,
    motionEnabled: Boolean,
) {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 8.dp, end = 4.dp, top = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FilledTonalIconButton(
                onClick = onToggleSidebar,
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = colors.surfaceContainerHighest,
                    contentColor = colors.onSurface,
                ),
            ) {
                Icon(
                    if (state.sidebarOpen) Icons.AutoMirrored.Rounded.MenuOpen else Icons.Rounded.Menu,
                    contentDescription = if (state.sidebarOpen) "Hide apps" else "Show apps",
                )
            }
            Spacer(Modifier.width(12.dp))
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Inbox",
                    Modifier.semantics { heading() },
                    style = MaterialTheme.typography.headlineLarge,
                    color = colors.onBackground,
                    maxLines = 1,
                )
                // As with the rail: no animation on the switch from the default state to the loaded one.
                key(state.loaded) {
                    AnimatedVisibility(
                        visible = state.loaded && !state.sidebarOpen,
                        modifier = Modifier.weight(1f, fill = false),
                        enter = fadeIn(ExpressiveMotion.defaultEffectsSpec(motionEnabled)) +
                            expandHorizontally(ExpressiveMotion.defaultSpatialSpec(motionEnabled), Alignment.Start),
                        exit = fadeOut(ExpressiveMotion.fastEffectsSpec(motionEnabled)) +
                            shrinkHorizontally(ExpressiveMotion.defaultSpatialSpec(motionEnabled), Alignment.Start),
                    ) {
                        FilterChip(chipLabel, chipIcon, onToggleSidebar, Modifier.padding(start = 12.dp))
                    }
                }
            }
            IconButton(onClick = onSettings) {
                Icon(Icons.Outlined.Settings, contentDescription = "Settings", tint = colors.onSurfaceVariant)
            }
        }
        Text(
            // Before the first real state an empty line holds the height, so nothing jumps.
            if (state.loaded) inboxSubtitle(state.totalUnread, state.appsWithUnread) else "",
            // Lines up with the "Inbox" title: 48dp menu button + 12dp gap.
            Modifier.padding(start = 60.dp, end = 12.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** The current filter, shown while the rail is hidden; tapping it brings the rail back. */
@Composable
private fun FilterChip(text: String, appIcon: ImageBitmap?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = colors.primaryContainer,
        contentColor = colors.onPrimaryContainer,
    ) {
        Row(
            Modifier.padding(start = 8.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (appIcon != null) {
                Image(
                    appIcon,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(18.dp).clip(RoundedCornerShape(5.dp)),
                )
            } else {
                Icon(Icons.Rounded.FilterList, contentDescription = null, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(6.dp))
            Text(
                text,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun CatchUpFab(totalUnread: Int, expanded: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val label = catchUpLabel(totalUnread)
    // material3 clears the semantics of the FAB's (animated) text slot, so the button describes itself.
    val description = if (totalUnread > 0) "Catch up, $totalUnread unread" else "Catch up"
    ExtendedFloatingActionButton(
        text = { Text(label, fontWeight = FontWeight.Bold, maxLines = 1) },
        icon = { Text("✨", fontSize = 20.sp, modifier = Modifier.clearAndSetSemantics {}) },
        expanded = expanded,
        onClick = onClick,
        modifier = modifier.semantics { contentDescription = description },
        shape = RoundedCornerShape(20.dp),
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    )
}

@Composable
private fun InboxList(
    state: InboxUiState,
    listState: LazyListState,
    iconOf: (String) -> ImageBitmap?,
    onOpenConversation: (String) -> Unit,
    onFixHealth: () -> Unit,
    onDelete: (String) -> Unit,
) {
    // Nothing is known yet: an empty sheet rather than a fake "No messages yet".
    if (!state.loaded) return
    val showHealth = healthText(state.health) != null
    if (state.conversations.isEmpty()) {
        Column(Modifier.fillMaxSize()) {
            if (showHealth) HealthBanner(state.health, onFixHealth, Modifier.padding(12.dp))
            EmptyInbox(Modifier.weight(1f).fillMaxWidth())
        }
        return
    }
    val navBar = with(LocalDensity.current) { WindowInsets.navigationBars.getBottom(this).toDp() }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().testTag("inbox-list"),
        // Room under the last row so the FAB never covers it.
        contentPadding = PaddingValues(top = 10.dp, bottom = 96.dp + navBar),
    ) {
        if (showHealth) {
            item(key = "health") {
                HealthBanner(state.health, onFixHealth, Modifier.padding(start = 12.dp, end = 12.dp, bottom = 8.dp))
            }
        }
        items(state.conversations, key = { it.key }) { row ->
            SwipeToDelete(onDelete = { onDelete(row.key) }) {
                ConversationListItem(row, iconOf(row.packageName), onDelete = { onDelete(row.key) }) {
                    onOpenConversation(row.key)
                }
            }
        }
    }
}

/** Swiping a row either way deletes the chat; the red background shows behind the moving row. */
@Composable
private fun SwipeToDelete(onDelete: () -> Unit, content: @Composable () -> Unit) {
    val swipe = rememberSwipeToDismissBoxState()
    SwipeToDismissBox(
        state = swipe,
        backgroundContent = {
            val colors = MaterialTheme.colorScheme
            val fromStart = swipe.dismissDirection == SwipeToDismissBoxValue.StartToEnd
            Row(
                Modifier.fillMaxSize().background(colors.errorContainer).padding(horizontal = 24.dp),
                horizontalArrangement = if (fromStart) Arrangement.Start else Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Delete, contentDescription = null, tint = colors.onErrorContainer)
            }
        },
        onDismiss = { onDelete() },
    ) {
        content()
    }
}

@Composable
private fun ConversationListItem(
    row: ConversationRow,
    appIcon: ImageBitmap?,
    onDelete: () -> Unit,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val unread = row.unreadCount > 0
    Row(
        Modifier
            .fillMaxWidth()
            // Opaque, so the swipe background only shows where the row has moved away.
            .background(colors.surface)
            // Swiping isn't reachable with TalkBack; the same delete is offered as an action.
            .semantics {
                customActions = listOf(CustomAccessibilityAction("Delete chat") { onDelete(); true })
            }
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(title = row.title, colorKey = row.key, appIcon = appIcon)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row {
                Text(
                    row.title,
                    Modifier.weight(1f).alignByBaseline(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (unread) FontWeight.Bold else FontWeight.Medium,
                    color = colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    formatListTime(row.lastMessageAt),
                    Modifier.alignByBaseline(),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (unread) FontWeight.Bold else FontWeight.Medium,
                    color = if (unread) colors.primary else colors.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    row.preview,
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (unread) colors.onSurface else colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (unread) {
                    Spacer(Modifier.width(8.dp))
                    CountPill(row.unreadCount)
                }
            }
        }
    }
}

@Preview(uiMode = UI_MODE_NIGHT_YES, widthDp = 360, heightDp = 780)
@Composable
private fun InboxContentPreview() {
    val now = System.currentTimeMillis()
    val rail = listOf(
        RailApp("com.whatsapp", "WhatsApp", 4),
        RailApp("com.facebook.orca", "Messenger", 2),
        RailApp("org.telegram.messenger", "Telegram", 1),
        RailApp("com.discord", "Discord", 0),
    )
    val rows = listOf(
        ConversationRow("wa:mom", "com.whatsapp", "Mom", false, now, "Sunday dinner? Bring the cake 🎂", 3),
        ConversationRow("wa:fam", "com.whatsapp", "Family group", true, now - 3_600_000, "Dad: Photos from the trip", 1),
        ConversationRow("ms:alex", "com.facebook.orca", "Alex Tan", false, now - 7_200_000, "Free for a call this week?", 2),
        ConversationRow("dc:chen", "com.discord", "陈明", false, now - 90_000_000, "明天见 😊", 0),
    )
    OneInboxTheme(darkTheme = true) {
        InboxContent(
            state = InboxUiState(
                rail = rail,
                conversations = rows,
                health = CaptureHealth.Ok,
                totalUnread = 7,
                appsWithUnread = 3,
                loaded = true,
            ),
            icon = { null },
            label = { pkg -> rail.first { it.packageName == pkg }.label },
            onSelect = {},
            onToggleSidebar = {},
            onOpenConversation = {},
            onCatchUp = {},
            onSettings = {},
            onFixHealth = {},
        )
    }
}
