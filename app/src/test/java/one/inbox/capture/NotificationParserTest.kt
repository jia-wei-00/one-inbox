package one.inbox.capture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationParserTest {
    private val wa = "com.whatsapp"
    private val allowed = setOf(wa)

    private fun snapshot(
        packageName: String = wa,
        notificationKey: String = "0|com.whatsapp|1|null|10",
        userId: Int = 0,
        postTime: Long = 5_000L,
        shortcutId: String? = null,
        isGroupSummary: Boolean = false,
        isOngoing: Boolean = false,
        category: String? = "msg",
        title: String? = "Alex",
        text: String? = null,
        conversationTitle: String? = null,
        isGroupConversation: Boolean = false,
        selfName: String? = null,
        messages: List<SnapshotMessage> = emptyList(),
    ) = NotificationSnapshot(
        packageName, notificationKey, userId, postTime, shortcutId, isGroupSummary, isOngoing,
        category, title, text, conversationTitle, isGroupConversation, selfName, messages,
    )

    @Test
    fun messagingStyleYieldsOneMessagePerEntry() {
        val s = snapshot(
            conversationTitle = "Family",
            isGroupConversation = true,
            messages = listOf(
                SnapshotMessage("Mum", "dinner?", 1_000L),
                SnapshotMessage("Dad", "yes", 2_000L),
            ),
        )
        val out = NotificationParser.parse(s, allowed)
        assertEquals(2, out.size)
        assertEquals("Mum", out[0].sender)
        assertEquals("dinner?", out[0].text)
        assertEquals(1_000L, out[0].timestamp)
        assertEquals("Dad", out[1].sender)
        assertEquals(2_000L, out[1].timestamp)
        assertEquals("Family", out[0].conversationTitle)
        assertTrue(out[0].isGroup)
        assertEquals(wa, out[0].packageName)
    }

    @Test
    fun repostWithAccumulatedMessagesHasStableDedupeKeys() {
        val m1 = SnapshotMessage("Mum", "dinner?", 1_000L)
        val m2 = SnapshotMessage("Mum", "hello?", 2_000L)
        val a = NotificationParser.parse(snapshot(messages = listOf(m1)), allowed)
        val b = NotificationParser.parse(snapshot(messages = listOf(m1, m2)), allowed)
        assertEquals(a[0].dedupeKey, b[0].dedupeKey)
        assertNotEquals(b[0].dedupeKey, b[1].dedupeKey)
    }

    @Test
    fun ownMessagesAreFromMe() {
        val s = snapshot(
            selfName = "Jia",
            messages = listOf(
                SnapshotMessage(null, "hi", 1L),
                SnapshotMessage("Jia", "again", 2L),
                SnapshotMessage("Alex", "yo", 3L),
            ),
        )
        val out = NotificationParser.parse(s, allowed)
        assertTrue(out[0].isFromMe)
        assertEquals("You", out[0].sender)
        assertTrue(out[1].isFromMe)
        assertEquals("You", out[1].sender)
        assertEquals(false, out[2].isFromMe)
        assertEquals("Alex", out[2].sender)
    }

    @Test
    fun fallbackUsesTitleAndText() {
        val out = NotificationParser.parse(snapshot(title = "Alex", text = "hi", postTime = 7_000L), allowed)
        assertEquals(1, out.size)
        assertEquals("Alex", out[0].sender)
        assertEquals("hi", out[0].text)
        assertEquals(7_000L, out[0].timestamp)
        assertEquals("Alex", out[0].conversationTitle)
    }

    @Test
    fun fallbackDedupeIgnoresTimestamp() {
        val a = NotificationParser.parse(snapshot(text = "hi", postTime = 1L), allowed)
        val b = NotificationParser.parse(snapshot(text = "hi", postTime = 2L), allowed)
        assertEquals(a[0].dedupeKey, b[0].dedupeKey)
    }

    @Test
    fun blankAndSystemNotificationsProduceNothing() {
        val ok = snapshot(text = "hi")
        assertEquals(1, NotificationParser.parse(ok, allowed).size)
        val cases = listOf(
            ok.copy(isGroupSummary = true),
            ok.copy(isOngoing = true),
            ok.copy(category = "call"),
            ok.copy(category = "progress"),
            ok.copy(category = "service"),
            ok.copy(category = "transport"),
            ok.copy(text = "   "),
            ok.copy(text = "3 new messages"),
            ok.copy(text = "1 new message from 1 chat"),
            ok.copy(text = "Checking for new messages…"),
            ok.copy(text = "5 new messages from 2 chats"),
            ok.copy(packageName = "com.other"),
            ok.copy(text = null, messages = listOf(SnapshotMessage("A", "2 new messages", 1L))),
            ok.copy(text = null, messages = listOf(SnapshotMessage("A", "  ", 1L))),
        )
        cases.forEachIndexed { i, c ->
            assertTrue("case $i should be empty", NotificationParser.parse(c, allowed).isEmpty())
        }
    }

    @Test
    fun mediaBecomesPlaceholder() {
        val s = snapshot(
            messages = listOf(
                SnapshotMessage("A", "", 1L, "image/jpeg"),
                SnapshotMessage("A", null, 2L, "audio/ogg"),
                SnapshotMessage("A", " ", 3L, "application/pdf"),
                SnapshotMessage("A", "", 4L, null),
            ),
        )
        val out = NotificationParser.parse(s, allowed)
        assertEquals(listOf("📷 Photo", "🎤 Voice message", "📎 Attachment"), out.map { it.text })
    }

    @Test
    fun dualAppsProfilesDoNotMerge() {
        val a = NotificationParser.conversationKey(snapshot(userId = 0))
        val b = NotificationParser.conversationKey(snapshot(userId = 999))
        assertNotEquals(a, b)
    }

    @Test
    fun shortcutIdWinsOverTitle() {
        val a = NotificationParser.conversationKey(snapshot(shortcutId = "sc1", title = "Alex"))
        val b = NotificationParser.conversationKey(snapshot(shortcutId = "sc1", title = "Alex (2)"))
        assertEquals(a, b)
        assertEquals("0|com.whatsapp|sc1", a)
    }

    @Test
    fun zeroTimestampFallsBackToPostTime() {
        val out = NotificationParser.parse(
            snapshot(postTime = 9_000L, messages = listOf(SnapshotMessage("A", "x", 0L))),
            allowed,
        )
        assertEquals(9_000L, out[0].timestamp)
    }

    @Test
    fun titleFallsBackToDisplayNameThenPackage() {
        val noTitle = snapshot(title = null, text = "hi")
        assertEquals("WhatsApp", NotificationParser.parse(noTitle, allowed)[0].conversationTitle)
        val unknown = snapshot(packageName = "com.other", title = null, text = "hi")
        assertEquals("com.other", NotificationParser.parse(unknown, setOf("com.other"))[0].conversationTitle)
    }

    @Test
    fun cjkAndEmojiPreserved() {
        val text = "明天见 😊 jumpa esok"
        val viaMessages = NotificationParser.parse(
            snapshot(messages = listOf(SnapshotMessage("A", text, 1L))), allowed,
        )
        assertEquals(text, viaMessages[0].text)
        val viaFallback = NotificationParser.parse(snapshot(text = text), allowed)
        assertEquals(text, viaFallback[0].text)
    }
}
