package one.inbox.feature.conversation

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.ResolvedTextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import one.inbox.data.db.MessageEntity
import one.inbox.ui.components.Avatar
import one.inbox.ui.components.senderColorIndex
import one.inbox.ui.theme.ExpressiveMotion
import one.inbox.ui.theme.OneInboxTheme
import one.inbox.ui.theme.senderColor
import java.time.LocalDate
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

private val BubbleCorner = 20.dp
private val TailCorner = 6.dp
private val IncomingShape = RoundedCornerShape(BubbleCorner, BubbleCorner, BubbleCorner, TailCorner)
private val OwnShape = RoundedCornerShape(BubbleCorner, BubbleCorner, TailCorner, BubbleCorner)
private const val BUBBLE_MAX_WIDTH = 0.76f
private const val TIME_ALPHA = 0.6f

/**
 * The smallest alpha from [preferred] upward at which [content] composited over [background]
 * still reaches [minContrast] against it. Keeps the faded bubble time readable on every bubble.
 */
fun readableAlpha(content: Color, background: Color, preferred: Float = TIME_ALPHA, minContrast: Float = 4.5f): Float {
    val bg = background.luminance() + 0.05f
    for (step in 0..50) {
        val alpha = (preferred + step * 0.02f).coerceAtMost(1f)
        val fg = content.copy(alpha = alpha).compositeOver(background).luminance() + 0.05f
        if (max(fg, bg) / minOf(fg, bg) >= minContrast || alpha >= 1f) return alpha
    }
    return 1f
}

/**
 * A read-only chat: the header (back, avatar, title, app dot + subtitle), day chips, incoming and
 * own bubbles, and the "Open {App} ↗" bar when the app can be launched. Opens at the newest
 * message and follows new ones only while the reader is at the bottom.
 */
@Composable
fun ConversationContent(
    state: ConversationUiState,
    appIcon: ImageBitmap?,
    onBack: () -> Unit,
    onOpenApp: () -> Unit,
    motionEnabled: Boolean = true,
    today: LocalDate = LocalDate.now(),
) {
    val items = remember(state.messages, state.isGroup, today) { buildChatItems(state.messages, state.isGroup, today) }
    // Newest first, drawn bottom-up: the list starts at the newest message with no initial jump.
    val newestFirst = remember(items) { items.asReversed() }
    val listState = rememberLazyListState()
    FollowNewestMessage(listState, newestFirst, motionEnabled)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0),
        topBar = { ConversationTopBar(state, appIcon, onBack) },
        bottomBar = { if (state.canOpenApp) OpenAppBar(state.appName, onOpenApp) },
    ) { padding ->
        val navBar = if (state.canOpenApp) {
            0.dp
        } else {
            with(LocalDensity.current) { WindowInsets.navigationBars.getBottom(this).toDp() }
        }
        LazyColumn(
            Modifier.padding(padding).fillMaxSize().testTag("conversation-list"),
            state = listState,
            reverseLayout = true,
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 12.dp + navBar),
        ) {
            items(newestFirst, key = { it.key }, contentType = { it is DayChip }) { item ->
                val animated = Modifier.animateItem(
                    fadeInSpec = ExpressiveMotion.defaultEffectsSpec(motionEnabled),
                    placementSpec = ExpressiveMotion.defaultSpatialSpec(motionEnabled),
                    fadeOutSpec = ExpressiveMotion.fastEffectsSpec(motionEnabled),
                )
                when (item) {
                    is DayChip -> DayChipRow(item.label, animated)
                    is ChatBubble -> BubbleRow(item, animated)
                }
            }
        }
    }
}

/**
 * Scrolls to a newly arrived newest message only when the reader was at the bottom before it
 * arrived. Items are keyed, so the list keeps the reader's place when they have scrolled up.
 */
@Composable
private fun FollowNewestMessage(listState: LazyListState, newestFirst: List<ChatItem>, motionEnabled: Boolean) {
    val newestKey = newestFirst.firstOrNull()?.key
    val tolerance = with(LocalDensity.current) { 24.dp.roundToPx() }
    val current by rememberUpdatedState(newestFirst)
    val seen = remember { arrayOfNulls<String>(1) }
    LaunchedEffect(newestKey) {
        val previous = seen[0]
        seen[0] = newestKey
        if (previous == null || newestKey == null || previous == newestKey) return@LaunchedEffect
        val items = current
        val previousIndex = items.indexOfFirst { it.key == previous }
        if (previousIndex < 0) return@LaunchedEffect
        // Until the list measures the new items it reports the old positions (bottom = index 0);
        // afterwards the old newest item has moved to previousIndex.
        val bottom = if (listState.layoutInfo.totalItemsCount == items.size) previousIndex else 0
        val first = listState.firstVisibleItemIndex
        val atBottom = first < bottom || first == bottom && listState.firstVisibleItemScrollOffset <= tolerance
        if (atBottom) {
            if (motionEnabled) listState.animateScrollToItem(0) else listState.scrollToItem(0)
        }
    }
}

@Composable
private fun ConversationTopBar(state: ConversationUiState, appIcon: ImageBitmap?, onBack: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(color = colors.surface, contentColor = colors.onSurface) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(start = 4.dp, end = 16.dp, top = 4.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
            }
            Spacer(Modifier.width(4.dp))
            Avatar(
                title = state.title,
                colorKey = state.conversationKey.ifEmpty { state.title },
                appIcon = null,
                size = 38.dp,
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    state.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppDot(appIcon)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        conversationSubtitle(state.appName, state.isGroup, state.peopleCount),
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** The 10dp app-colored dot: the app's icon, or a primary dot when there is none. */
@Composable
private fun AppDot(appIcon: ImageBitmap?) {
    val shape = RoundedCornerShape(3.dp)
    if (appIcon != null) {
        Image(
            appIcon,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(10.dp).clip(shape),
        )
    } else {
        Box(Modifier.size(10.dp).clip(shape).background(MaterialTheme.colorScheme.primary))
    }
}

@Composable
private fun DayChipRow(label: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(top = 14.dp, bottom = 6.dp), contentAlignment = Alignment.Center) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ) {
            Text(
                label,
                Modifier.padding(horizontal = 12.dp, vertical = 4.dp).semantics { heading() },
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

@Composable
private fun BubbleRow(item: ChatBubble, modifier: Modifier = Modifier) {
    val m = item.message
    val colors = MaterialTheme.colorScheme
    val mine = m.isFromMe
    val container = if (mine) colors.primaryContainer else colors.surfaceContainerHigh
    val content = if (mine) colors.onPrimaryContainer else colors.onSurface
    val timeAlpha = remember(content, container) { readableAlpha(content, container) }
    val align = if (mine) Alignment.TopEnd else Alignment.TopStart
    Box(
        modifier.fillMaxWidth().padding(top = if (item.followsSameSender) 2.dp else 6.dp),
        contentAlignment = align,
    ) {
        Box(Modifier.fillMaxWidth(BUBBLE_MAX_WIDTH), contentAlignment = align) {
            Surface(shape = if (mine) OwnShape else IncomingShape, color = container, contentColor = content) {
                Column(Modifier.padding(start = 12.dp, end = 10.dp, top = 7.dp, bottom = 6.dp)) {
                    if (item.showSender) {
                        Text(
                            m.sender,
                            Modifier.padding(end = 2.dp, bottom = 1.dp),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = senderNameColor(m.sender),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    MessageText(m.text, bubbleTime(m.timestamp), content, content.copy(alpha = timeAlpha))
                }
            }
        }
    }
}

/** Pastel sender colors on the dark scheme; their deeper avatar-pair partners on the light one. */
@Composable
private fun senderNameColor(sender: String): Color = MaterialTheme.colorScheme.senderColor(senderColorIndex(sender))

/**
 * The message text with its time at the bottom-right: on the last line when it fits beside it,
 * otherwise on its own line underneath.
 */
@Composable
private fun MessageText(text: String, time: String, textColor: Color, timeColor: Color) {
    val textStyle = MaterialTheme.typography.bodyLarge.copy(lineHeight = 22.sp)
    val timeStyle = TextStyle(fontSize = 10.sp, lineHeight = 12.sp, fontWeight = FontWeight.Medium)
    val measurer = rememberTextMeasurer(cacheSize = 4)
    Layout(
        content = {
            Text(text, style = textStyle, color = textColor)
            Text(time, style = timeStyle, color = timeColor, maxLines = 1)
        },
    ) { measurables, constraints ->
        val gap = 8.dp.roundToPx()
        val below = 2.dp.roundToPx()
        val textPlaceable = measurables[0].measure(constraints.copy(minWidth = 0, minHeight = 0))
        val timePlaceable = measurables[1].measure(Constraints())
        val layout = measurer.measure(text, textStyle, constraints = Constraints(maxWidth = constraints.maxWidth))
        val last = layout.lineCount - 1
        val ltr = layout.getParagraphDirection(layout.getLineStart(last)) == ResolvedTextDirection.Ltr
        val inlineWidth = ceil(layout.getLineRight(last)).toInt() + gap + timePlaceable.width
        val timeY: Int
        val width: Int
        val height: Int
        if (ltr && inlineWidth <= constraints.maxWidth) {
            width = max(textPlaceable.width, inlineWidth)
            // Sit the time on the last line's baseline.
            timeY = (layout.getLineBaseline(last) - timePlaceable[FirstBaseline]).roundToInt()
            height = max(textPlaceable.height, timeY + timePlaceable.height)
        } else {
            width = max(textPlaceable.width, timePlaceable.width)
            timeY = textPlaceable.height + below
            height = timeY + timePlaceable.height
        }
        val w = width.coerceIn(constraints.minWidth, constraints.maxWidth)
        layout(w, height.coerceAtLeast(constraints.minHeight)) {
            textPlaceable.place(0, 0)
            timePlaceable.place(w - timePlaceable.width, timeY)
        }
    }
}

@Composable
private fun OpenAppBar(appName: String, onOpenApp: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(color = colors.surface) {
        OutlinedButton(
            onClick = onOpenApp,
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .heightIn(min = 48.dp),
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.5.dp, colors.onSurfaceVariant),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.onSurface),
        ) {
            Text(
                openAppLabel(appName),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Preview(uiMode = UI_MODE_NIGHT_YES, widthDp = 360, heightDp = 780)
@Composable
private fun ConversationContentPreview() {
    val now = System.currentTimeMillis()
    fun msg(id: Long, sender: String, text: String, minutesAgo: Long, me: Boolean = false) =
        MessageEntity(id, "d$id", "wa:fam", "com.whatsapp", sender, me, text, now - minutesAgo * 60_000, false)
    OneInboxTheme(darkTheme = true) {
        ConversationContent(
            state = ConversationUiState(
                appName = "WhatsApp",
                title = "Family group",
                isGroup = true,
                messages = listOf(
                    msg(1, "Mom", "Sunday dinner at 7? Bring the cake 🎂", 8),
                    msg(2, "Dad", "Photos from the trip, check them out", 6),
                    msg(3, "You", "I'll be there! 🙌", 5, me = true),
                    msg(4, "Mom", "Great, don't be late this time 😄", 5),
                    msg(5, "Lily", "明天见 😊", 1),
                ),
                packageName = "com.whatsapp",
                peopleCount = 3,
                canOpenApp = true,
                conversationKey = "wa:fam",
            ),
            appIcon = null,
            onBack = {},
            onOpenApp = {},
        )
    }
}
