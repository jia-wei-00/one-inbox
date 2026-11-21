package one.inbox.feature.conversation

import one.inbox.data.db.MessageEntity
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Consecutive messages from the same sender within this window form one visual group. */
const val GROUPING_WINDOW_MS = 2 * 60 * 1000L

/** One row of the conversation list, in chronological order. */
sealed interface ChatItem {
    val key: String
}

/** A day separator chip: "Today", "Yesterday" or "MMM d". */
data class DayChip(val date: LocalDate, val label: String) : ChatItem {
    override val key: String get() = "day:$date"
}

/**
 * A message bubble. [followsSameSender] is true when the previous bubble on the same day is from
 * the same sender less than [GROUPING_WINDOW_MS] earlier (tight spacing, no repeated name);
 * [showSender] puts the sender's name above an incoming bubble in a group.
 */
data class ChatBubble(
    val message: MessageEntity,
    val showSender: Boolean,
    val followsSameSender: Boolean,
) : ChatItem {
    override val key: String get() = "msg:${message.id}:${message.dedupeKey}"
}

/** "Today", "Yesterday", "MMM d" this year, or "MMM d, yyyy" for other years. */
fun dayLabel(day: LocalDate, today: LocalDate): String = when (day) {
    today -> "Today"
    today.minusDays(1) -> "Yesterday"
    else -> {
        val pattern = if (day.year == today.year) "MMM d" else "MMM d, yyyy"
        DateTimeFormatter.ofPattern(pattern, Locale.getDefault()).format(day)
    }
}

/** Day chips and bubbles for [messages] (already in chronological order). */
fun buildChatItems(
    messages: List<MessageEntity>,
    isGroup: Boolean,
    today: LocalDate = LocalDate.now(),
    zone: ZoneId = ZoneId.systemDefault(),
): List<ChatItem> {
    val items = ArrayList<ChatItem>(messages.size + 4)
    var lastDay: LocalDate? = null
    var previous: MessageEntity? = null
    for (m in messages) {
        val day = Instant.ofEpochMilli(m.timestamp).atZone(zone).toLocalDate()
        if (day != lastDay) {
            items += DayChip(day, dayLabel(day, today))
            lastDay = day
            previous = null
        }
        val grouped = previous != null && sameSender(previous, m) &&
            m.timestamp - previous.timestamp in 0..GROUPING_WINDOW_MS
        items += ChatBubble(m, showSender = isGroup && !m.isFromMe && !grouped, followsSameSender = grouped)
        previous = m
    }
    return items
}

private fun sameSender(a: MessageEntity, b: MessageEntity): Boolean =
    a.isFromMe == b.isFromMe && (a.isFromMe || a.sender == b.sender)

/** Header subtitle: "{app} · N people" for groups with other senders, otherwise just "{app}". */
fun conversationSubtitle(appName: String, isGroup: Boolean, peopleCount: Int): String = when {
    !isGroup || peopleCount <= 0 -> appName
    peopleCount == 1 -> "$appName · 1 person"
    else -> "$appName · $peopleCount people"
}

/** The bottom bar button text. */
fun openAppLabel(appName: String): String = "Open $appName ↗"

/** The time shown inside a bubble: "HH:mm". */
fun bubbleTime(millis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
    DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault()).format(Instant.ofEpochMilli(millis).atZone(zone))
