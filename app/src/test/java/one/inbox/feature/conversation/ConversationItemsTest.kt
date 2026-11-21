package one.inbox.feature.conversation

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import one.inbox.data.db.MessageEntity
import one.inbox.ui.theme.VioletNightDark
import one.inbox.ui.theme.VioletNightLight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

class ConversationItemsTest {
    private val zone = ZoneOffset.UTC
    private val today = LocalDate.of(2026, 11, 22)

    private var nextId = 1L
    private fun msg(sender: String, day: LocalDate, h: Int, m: Int, s: Int = 0, me: Boolean = false) = MessageEntity(
        id = nextId,
        dedupeKey = "d${nextId++}",
        conversationKey = "wa:fam",
        packageName = "com.whatsapp",
        sender = sender,
        isFromMe = me,
        text = "hi",
        timestamp = day.atTime(LocalTime.of(h, m, s)).toInstant(zone).toEpochMilli(),
        unread = false,
    )

    @Test
    fun dayLabels() {
        assertEquals("Today", dayLabel(today, today))
        assertEquals("Yesterday", dayLabel(today.minusDays(1), today))
        assertEquals("Nov 18", dayLabel(LocalDate.of(2026, 11, 18), today))
        assertEquals("Dec 31, 2025", dayLabel(LocalDate.of(2025, 12, 31), today))
    }

    @Test
    fun dayChipBeforeEachNewDay() {
        val yesterday = today.minusDays(1)
        val items = buildChatItems(
            listOf(msg("Mom", yesterday, 20, 0), msg("Dad", today, 9, 0), msg("Mom", today, 9, 5)),
            isGroup = true, today = today, zone = zone,
        )
        assertEquals(5, items.size)
        assertEquals("Yesterday", (items[0] as DayChip).label)
        assertTrue(items[1] is ChatBubble)
        assertEquals("Today", (items[2] as DayChip).label)
        assertTrue(items[3] is ChatBubble && items[4] is ChatBubble)
    }

    @Test
    fun sameSenderWithinTwoMinutesIsGroupedAndNamedOnce() {
        val items = buildChatItems(
            listOf(
                msg("Mom", today, 9, 0),
                msg("Mom", today, 9, 2), // exactly 2 minutes later: grouped
                msg("Mom", today, 9, 4, 1), // 2:01 later: new group
                msg("Dad", today, 9, 4, 30), // other sender
                msg("You", today, 9, 5, me = true),
                msg("You", today, 9, 5, 30, me = true),
            ),
            isGroup = true, today = today, zone = zone,
        ).filterIsInstance<ChatBubble>()
        assertEquals(listOf(false, true, false, false, false, true), items.map { it.followsSameSender })
        // Names only on the first incoming bubble of each group, never on own bubbles.
        assertEquals(listOf(true, false, true, true, false, false), items.map { it.showSender })
    }

    @Test
    fun noSenderNamesInOneToOne() {
        val items = buildChatItems(
            listOf(msg("Jason", today, 9, 0), msg("Jason", today, 9, 10)),
            isGroup = false, today = today, zone = zone,
        ).filterIsInstance<ChatBubble>()
        assertFalse(items.any { it.showSender })
    }

    @Test
    fun groupingDoesNotCrossDays() {
        val items = buildChatItems(
            listOf(msg("Mom", today.minusDays(1), 23, 59), msg("Mom", today, 0, 0, 30)),
            isGroup = true, today = today, zone = zone,
        ).filterIsInstance<ChatBubble>()
        assertEquals(listOf(false, false), items.map { it.followsSameSender })
        assertEquals(listOf(true, true), items.map { it.showSender })
    }

    @Test
    fun keysAreUnique() {
        val items = buildChatItems(
            listOf(msg("Mom", today, 9, 0), msg("Mom", today, 9, 1)),
            isGroup = true, today = today, zone = zone,
        )
        assertEquals(items.size, items.map { it.key }.toSet().size)
    }

    @Test
    fun subtitles() {
        assertEquals("WhatsApp · 4 people", conversationSubtitle("WhatsApp", isGroup = true, peopleCount = 4))
        assertEquals("WhatsApp · 1 person", conversationSubtitle("WhatsApp", isGroup = true, peopleCount = 1))
        assertEquals("WhatsApp", conversationSubtitle("WhatsApp", isGroup = true, peopleCount = 0))
        assertEquals("Messenger", conversationSubtitle("Messenger", isGroup = false, peopleCount = 0))
        assertEquals("Open WhatsApp ↗", openAppLabel("WhatsApp"))
    }

    @Test
    fun bubbleTimeStaysReadableOnBothBubblesInBothSchemes() {
        for (scheme in listOf(VioletNightDark, VioletNightLight)) {
            val pairs = listOf(
                scheme.onSurface to scheme.surfaceContainerHigh,
                scheme.onPrimaryContainer to scheme.primaryContainer,
            )
            for ((content, container) in pairs) {
                val alpha = readableAlpha(content, container)
                assertTrue("alpha $alpha below the preferred 60%", alpha >= 0.6f)
                val shown = content.copy(alpha = alpha).compositeOver(container)
                assertTrue("contrast below 4.5:1 for $content on $container", contrast(shown, container) >= 4.5)
            }
        }
        // The incoming dark bubble already passes at 60%, so it keeps the spec's value.
        assertEquals(0.6f, readableAlpha(VioletNightDark.onSurface, VioletNightDark.surfaceContainerHigh), 0.001f)
    }

    private fun contrast(a: Color, b: Color): Double {
        val la = a.luminance() + 0.05
        val lb = b.luminance() + 0.05
        return maxOf(la, lb).toDouble() / minOf(la, lb)
    }
}
