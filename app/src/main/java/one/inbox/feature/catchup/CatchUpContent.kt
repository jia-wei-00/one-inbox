package one.inbox.feature.catchup

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Celebration
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.MarkChatUnread
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import one.inbox.summary.CatchUp
import one.inbox.summary.CatchUpItem
import one.inbox.summary.StoredCatchUp
import one.inbox.ui.components.Avatar
import one.inbox.ui.components.SectionCard
import one.inbox.ui.theme.ExpressiveMotion
import one.inbox.ui.theme.OneInboxTheme

/** How long ago [then] was, coarsely: "just now", "5 min ago", "2 h ago", "yesterday", "3 days ago". */
fun relativeTime(then: Long, now: Long): String {
    val minutes = (now - then).coerceAtLeast(0) / 60_000
    val hours = minutes / 60
    val days = hours / 24
    return when {
        minutes < 1 -> "just now"
        hours < 1 -> "$minutes min ago"
        days < 1 -> "$hours h ago"
        days == 1L -> "yesterday"
        else -> "$days days ago"
    }
}

/** The hero subtitle: "N unread · M apps · {relative time}", or an invitation before the first summary. */
fun catchUpSubtitle(latest: StoredCatchUp?, now: Long): String {
    if (latest == null) return "Summarize your unread messages"
    val apps = if (latest.appCount == 1) "app" else "apps"
    return "${latest.unreadCount} unread · ${latest.appCount} $apps · ${relativeTime(latest.generatedAt, now)}"
}

/** "**{title}** {summary}": the title in bold, else the app name, else the summary alone. */
fun itemText(label: ItemLabel?, summary: String): AnnotatedString = buildAnnotatedString {
    val name = label?.title ?: label?.appName
    if (name != null) {
        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(name) }
        append(" ")
    }
    append(summary)
}

enum class CatchUpMessageKind { NoKey, NothingUnread, Refused, Failed }

/** Which kind of card a [CatchUpUiState.message] gets; anything unrecognised is a failure text. */
fun messageKind(message: String): CatchUpMessageKind = when (message) {
    MSG_NO_API_KEY -> CatchUpMessageKind.NoKey
    MSG_NO_UNREAD -> CatchUpMessageKind.NothingUnread
    MSG_REFUSED -> CatchUpMessageKind.Refused
    else -> CatchUpMessageKind.Failed
}

fun lowPriorityTitle(count: Int): String = "🔕 Low priority · $count"

/**
 * The catch-up: a hero title with the summary's counts and age, a friendly card for the last
 * outcome's message, the three bucket cards (low priority collapsed) and the pinned actions.
 */
@Composable
fun CatchUpContent(
    state: CatchUpUiState,
    icon: (conversationKey: String) -> ImageBitmap?,
    onBack: () -> Unit,
    onSummarize: () -> Unit,
    onMarkAllRead: () -> Unit,
    onOpenConversation: (String) -> Unit,
    onOpenSettings: () -> Unit,
    motionEnabled: Boolean = true,
    now: Long = System.currentTimeMillis(),
) {
    // App icons are decoded from the PackageManager; keep them for the life of the screen.
    val iconOf: (String) -> ImageBitmap? = remember(icon) {
        val cache = HashMap<String, ImageBitmap?>()
        val cached: (String) -> ImageBitmap? = { key ->
            if (key in cache) cache[key] else icon(key).also { cache[key] = it }
        }
        cached
    }
    var lowExpanded by rememberSaveable { mutableStateOf(false) }
    val latest = state.latest
    val colors = MaterialTheme.colorScheme

    Scaffold(
        containerColor = colors.background,
        contentWindowInsets = WindowInsets(0),
        topBar = { BackBar(onBack) },
        bottomBar = { ActionBar(latest != null, state.running, onSummarize, onMarkAllRead) },
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding).fillMaxSize().testTag("catchup-list"),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "hero") { Hero(latest, state.newSince, now) }
            state.message?.let { msg ->
                item(key = "message") {
                    MessageCard(msg, onOpenSettings, Modifier.animateItem(
                        fadeInSpec = ExpressiveMotion.defaultEffectsSpec(motionEnabled),
                        placementSpec = ExpressiveMotion.defaultSpatialSpec(motionEnabled),
                        fadeOutSpec = ExpressiveMotion.fastEffectsSpec(motionEnabled),
                    ))
                }
            }
            val c = latest?.catchUp ?: return@LazyColumn
            if (c.needsReply.isNotEmpty()) {
                item(key = "needs") {
                    BucketCard(
                        "⚠ Needs your reply", c.needsReply, colors.tertiaryContainer, colors.onTertiaryContainer,
                        state.labels, iconOf, onOpenConversation,
                    )
                }
            }
            if (c.worthKnowing.isNotEmpty()) {
                item(key = "worth") {
                    BucketCard(
                        "💬 Worth knowing", c.worthKnowing, colors.secondaryContainer, colors.onSecondaryContainer,
                        state.labels, iconOf, onOpenConversation,
                    )
                }
            }
            if (c.lowPriority.isNotEmpty()) {
                item(key = "low") {
                    LowPriorityCard(
                        c.lowPriority, lowExpanded, { lowExpanded = !lowExpanded },
                        state.labels, iconOf, onOpenConversation, motionEnabled,
                    )
                }
            }
        }
    }
}

@Composable
private fun BackBar(onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).statusBarsPadding()
            .padding(start = 4.dp, top = 4.dp),
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
        }
    }
}

@Composable
private fun Hero(latest: StoredCatchUp?, newSince: Int, now: Long) {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, bottom = 6.dp)) {
        Text("Catch-up ✨", style = MaterialTheme.typography.headlineLarge, color = colors.onBackground)
        Spacer(Modifier.height(4.dp))
        Text(
            catchUpSubtitle(latest, now),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
        )
        if (latest != null && latest.droppedCount > 0) {
            Text(
                "Oldest ${latest.droppedCount} messages left out",
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
        }
        if (newSince > 0) {
            Spacer(Modifier.height(10.dp))
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = colors.primaryContainer,
                contentColor = colors.onPrimaryContainer,
            ) {
                Row(
                    Modifier.padding(start = 8.dp, end = 12.dp, top = 5.dp, bottom = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.MarkChatUnread, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "$newSince new since",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun MessageCard(message: String, onOpenSettings: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val kind = messageKind(message)
    val error = kind == CatchUpMessageKind.Refused || kind == CatchUpMessageKind.Failed
    val glyph: ImageVector = when (kind) {
        CatchUpMessageKind.NoKey -> Icons.Rounded.Key
        CatchUpMessageKind.NothingUnread -> Icons.Rounded.Celebration
        CatchUpMessageKind.Refused -> Icons.Rounded.Block
        CatchUpMessageKind.Failed -> Icons.Rounded.ErrorOutline
    }
    SectionCard(
        containerColor = if (error) colors.errorContainer else colors.surfaceContainerHigh,
        contentColor = if (error) colors.onErrorContainer else colors.onSurface,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val tint = if (error) LocalContentColor.current else colors.primary
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(tint.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(glyph, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(14.dp))
            Text(message, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        }
        if (kind == CatchUpMessageKind.NoKey) {
            TextButton(onClick = onOpenSettings, modifier = Modifier.align(Alignment.End)) {
                Text("Open Settings", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun BucketCard(
    title: String,
    items: List<CatchUpItem>,
    container: Color,
    content: Color,
    labels: Map<String, ItemLabel>,
    iconOf: (String) -> ImageBitmap?,
    onOpen: (String) -> Unit,
) {
    SectionCard(
        containerColor = container,
        contentColor = content,
        modifier = Modifier.fillMaxWidth(),
        title = title,
        trailing = {
            Text(items.size.toString(), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        },
    ) {
        items.forEach { ItemRow(it, labels[it.conversationKey], iconOf, onOpen) }
    }
}

/**
 * Collapsed by default: the whole header strip toggles it, and the chevron turns on a spring.
 * Built on a plain Surface (not SectionCard) so the 48dp+ header reaches the card's edges.
 */
@Composable
private fun LowPriorityCard(
    items: List<CatchUpItem>,
    expanded: Boolean,
    onToggle: () -> Unit,
    labels: Map<String, ItemLabel>,
    iconOf: (String) -> ImageBitmap?,
    onOpen: (String) -> Unit,
    motionEnabled: Boolean,
) {
    val colors = MaterialTheme.colorScheme
    val rotation by animateFloatAsState(
        if (expanded) 180f else 0f,
        ExpressiveMotion.defaultSpatialSpec(motionEnabled),
        label = "chevron",
    )
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = colors.surfaceContainerHigh,
        contentColor = colors.onSurface,
    ) {
        Column {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle)
                    .heightIn(min = 56.dp)
                    .padding(start = 16.dp, end = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    lowPriorityTitle(items.size),
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Icon(
                    Icons.Rounded.KeyboardArrowDown,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = colors.onSurfaceVariant,
                    modifier = Modifier.graphicsLayer { rotationZ = rotation },
                )
            }
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(ExpressiveMotion.defaultSpatialSpec(motionEnabled), Alignment.Top) +
                    fadeIn(ExpressiveMotion.defaultEffectsSpec(motionEnabled)),
                exit = shrinkVertically(ExpressiveMotion.defaultSpatialSpec(motionEnabled), Alignment.Top) +
                    fadeOut(ExpressiveMotion.fastEffectsSpec(motionEnabled)),
            ) {
                Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 10.dp)) {
                    items.forEach { ItemRow(it, labels[it.conversationKey], iconOf, onOpen) }
                }
            }
        }
    }
}

@Composable
private fun ItemRow(
    item: CatchUpItem,
    label: ItemLabel?,
    iconOf: (String) -> ImageBitmap?,
    onOpen: (String) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable { onOpen(item.conversationKey) }
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Avatar(
            title = label?.title ?: label?.appName.orEmpty(),
            colorKey = item.conversationKey,
            appIcon = iconOf(item.conversationKey),
            size = 32.dp,
        )
        Spacer(Modifier.width(12.dp))
        Text(
            itemText(label, item.summary),
            // Centers a single line on the 32dp avatar.
            Modifier.weight(1f).padding(top = 6.dp),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

/**
 * The pinned actions: tonal "Mark all read" (once there is a summary) beside the filled summarize
 * button. When a label wouldn't fit half the width (large font scales) they stack full-width.
 */
@Composable
private fun ActionBar(hasLatest: Boolean, running: Boolean, onSummarize: () -> Unit, onMarkAllRead: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val summarizeLabel = if (hasLatest) "Summarize again" else "Summarize"
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
    Surface(color = colors.background) {
        BoxWithConstraints(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            val density = LocalDensity.current
            val widest = remember(summarizeLabel, labelStyle, density) {
                listOf(
                    measurer.measure("Mark all read", labelStyle).size.width,
                    measurer.measure(summarizeLabel, labelStyle).size.width,
                    measurer.measure("Summarizing…", labelStyle).size.width + with(density) { SpinnerRoom.roundToPx() },
                ).max()
            }
            val half = (maxWidth - ActionGap) / 2 - ButtonHorizontalPadding * 2
            val stacked = hasLatest && with(density) { widest.toDp() } > half
            val markAllRead = @Composable { modifier: Modifier ->
                FilledTonalButton(
                    onClick = onMarkAllRead,
                    modifier = modifier.heightIn(min = 52.dp),
                    contentPadding = ButtonPadding,
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = colors.surfaceContainerHighest,
                        contentColor = colors.onSurface,
                    ),
                ) {
                    ButtonLabel("Mark all read")
                }
            }
            val summarize = @Composable { modifier: Modifier ->
                Button(
                    onClick = onSummarize,
                    enabled = !running,
                    modifier = modifier.heightIn(min = 52.dp),
                    contentPadding = ButtonPadding,
                    // While running the button reads as busy, not greyed out.
                    colors = ButtonDefaults.buttonColors(
                        disabledContainerColor = colors.primaryContainer,
                        disabledContentColor = colors.onPrimaryContainer,
                    ),
                ) {
                    if (running) {
                        CircularProgressIndicator(
                            Modifier.size(18.dp),
                            color = LocalContentColor.current,
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.width(SpinnerRoom - 18.dp))
                        ButtonLabel("Summarizing…")
                    } else {
                        ButtonLabel(summarizeLabel)
                    }
                }
            }
            if (stacked) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    markAllRead(Modifier.fillMaxWidth())
                    summarize(Modifier.fillMaxWidth())
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(ActionGap)) {
                    if (hasLatest) markAllRead(Modifier.weight(1f))
                    summarize(Modifier.weight(1f))
                }
            }
        }
    }
}

private val ActionGap = 10.dp
private val ButtonHorizontalPadding = 12.dp
private val ButtonPadding = PaddingValues(horizontal = ButtonHorizontalPadding, vertical = 10.dp)

/** The spinner (18dp) plus its gap before "Summarizing…". */
private val SpinnerRoom = 28.dp

@Composable
private fun ButtonLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Preview(uiMode = UI_MODE_NIGHT_YES, widthDp = 360, heightDp = 780)
@Composable
private fun CatchUpContentPreview() {
    val now = System.currentTimeMillis()
    val mom = "0|com.whatsapp|Mom"
    val alex = "0|com.facebook.orca|Alex Tan"
    val team = "0|org.telegram.messenger|Team launch"
    val low = (1..6).map { CatchUpItem("0|com.whatsapp|g$it", "Chatter about plans $it.") }
    val state = CatchUpUiState(
        latest = StoredCatchUp(
            CatchUp(
                needsReply = listOf(
                    CatchUpItem(mom, "is asking if you're coming to Sunday dinner, and to bring the cake."),
                    CatchUpItem(alex, "wants to set a call time this week."),
                ),
                worthKnowing = listOf(CatchUpItem(team, "Launch moved to Friday.")),
                lowPriority = low,
            ),
            generatedAt = now - 5 * 60_000,
            includedIds = emptyList(),
            unreadCount = 62,
            appCount = 6,
            droppedCount = 0,
        ),
        newSince = 3,
        labels = mapOf(
            mom to ItemLabel("Mom", "WhatsApp"),
            alex to ItemLabel("Alex Tan", "Messenger"),
            team to ItemLabel("Team launch", "Telegram"),
        ) + low.associate { it.conversationKey to ItemLabel(null, "WhatsApp") },
    )
    OneInboxTheme(darkTheme = true) {
        CatchUpContent(
            state = state,
            icon = { null },
            onBack = {},
            onSummarize = {},
            onMarkAllRead = {},
            onOpenConversation = {},
            onOpenSettings = {},
            now = now,
        )
    }
}
