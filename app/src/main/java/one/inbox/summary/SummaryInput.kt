package one.inbox.summary

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

data class UnreadMessage(
    val id: Long,
    val conversationKey: String,
    val appName: String,
    val conversationTitle: String,
    val isGroup: Boolean,
    val sender: String,
    val text: String,
    val timestamp: Long,
)

data class SummaryInput(
    val prompt: String,
    val includedIds: List<Long>,
    /** "c1" -> conversationKey */
    val conversationIds: Map<String, String>,
    val droppedCount: Int,
    val appCount: Int,
)

/**
 * Builds the prompt from unread messages. Conversations are ordered by their newest message and
 * numbered c1, c2, ... If the whole prompt exceeds [maxChars], the oldest messages are dropped first.
 */
fun buildSummaryInput(
    messages: List<UnreadMessage>,
    maxChars: Int = 400_000,
    zone: ZoneId = ZoneId.systemDefault(),
): SummaryInput {
    val oldestFirst = messages.sortedWith(compareBy({ it.timestamp }, { it.id }))

    // Smallest number of oldest messages to drop so the prompt fits (length shrinks as we drop).
    var lo = 0
    var hi = oldestFirst.size
    while (lo < hi) {
        val mid = (lo + hi) / 2
        if (render(oldestFirst.drop(mid), zone).prompt.length <= maxChars) hi = mid else lo = mid + 1
    }
    val kept = oldestFirst.drop(lo)
    val rendered = render(kept, zone)
    return SummaryInput(
        prompt = rendered.prompt,
        includedIds = kept.map { it.id },
        conversationIds = rendered.conversationIds,
        droppedCount = lo,
        appCount = kept.map { it.appName }.distinct().size,
    )
}

private class Rendered(val prompt: String, val conversationIds: Map<String, String>)

private val timeFormat = DateTimeFormatter.ofPattern("HH:mm")
private val dateTimeFormat = DateTimeFormatter.ofPattern("MMM d HH:mm", Locale.ENGLISH)

private fun render(oldestFirst: List<UnreadMessage>, zone: ZoneId): Rendered {
    val multiDay = oldestFirst.map { Instant.ofEpochMilli(it.timestamp).atZone(zone).toLocalDate() }
        .distinct().size > 1
    val format = (if (multiDay) dateTimeFormat else timeFormat).withZone(zone)

    val conversations = oldestFirst.groupBy { it.conversationKey }.values
        .sortedByDescending { group -> group.maxOf { it.timestamp } }
    val ids = LinkedHashMap<String, String>()
    val prompt = buildString {
        conversations.forEachIndexed { index, group ->
            val id = "c${index + 1}"
            ids[id] = group.first().conversationKey
            val first = group.first()
            if (index > 0) append('\n')
            append("### $id · ${first.appName} · ${first.conversationTitle} (${if (first.isGroup) "group" else "direct"})\n")
            group.forEach { m ->
                append("[${format.format(Instant.ofEpochMilli(m.timestamp))}] ${m.sender}: ${m.text}\n")
            }
        }
    }
    return Rendered(prompt, ids)
}
