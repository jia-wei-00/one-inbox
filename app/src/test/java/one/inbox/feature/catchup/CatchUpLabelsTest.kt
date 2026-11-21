package one.inbox.feature.catchup

import androidx.compose.ui.text.font.FontWeight
import one.inbox.summary.CatchUp
import one.inbox.summary.StoredCatchUp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CatchUpLabelsTest {
    private val now = 1_795_000_000_000L
    private val minute = 60_000L
    private val hour = 60 * minute
    private val day = 24 * hour

    @Test
    fun relativeTimeBuckets() {
        assertEquals("just now", relativeTime(now - 30_000, now))
        assertEquals("just now", relativeTime(now + 5 * minute, now)) // clock skew: never "in the future"
        assertEquals("1 min ago", relativeTime(now - minute, now))
        assertEquals("59 min ago", relativeTime(now - 59 * minute - 59_000, now))
        assertEquals("1 h ago", relativeTime(now - hour, now))
        assertEquals("23 h ago", relativeTime(now - 23 * hour - 59 * minute, now))
        assertEquals("yesterday", relativeTime(now - day, now))
        assertEquals("3 days ago", relativeTime(now - 3 * day - hour, now))
    }

    @Test
    fun subtitleBeforeFirstSummary() {
        assertEquals("Summarize your unread messages", catchUpSubtitle(null, now))
    }

    @Test
    fun subtitleWithSummary() {
        assertEquals("62 unread · 6 apps · 5 min ago", catchUpSubtitle(stored(62, 6, now - 5 * minute), now))
        assertEquals("1 unread · 1 app · just now", catchUpSubtitle(stored(1, 1, now), now))
    }

    @Test
    fun itemTextBoldsTheTitle() {
        val text = itemText(ItemLabel("Mom", "WhatsApp"), "is asking about dinner.")
        assertEquals("Mom is asking about dinner.", text.text)
        val bold = text.spanStyles.single()
        assertEquals(FontWeight.Bold, bold.item.fontWeight)
        assertEquals(0, bold.start)
        assertEquals(3, bold.end)
    }

    @Test
    fun itemTextFallsBackToAppNameThenSummaryOnly() {
        val app = itemText(ItemLabel(null, "WhatsApp"), "Someone said hi.")
        assertEquals("WhatsApp Someone said hi.", app.text)
        assertEquals(8, app.spanStyles.single().end)
        val none = itemText(ItemLabel(null, null), "Someone said hi.")
        assertEquals("Someone said hi.", none.text)
        assertTrue(none.spanStyles.isEmpty())
        assertEquals("Someone said hi.", itemText(null, "Someone said hi.").text)
    }

    @Test
    fun messageKinds() {
        assertEquals(CatchUpMessageKind.NoKey, messageKind(MSG_NO_API_KEY))
        assertEquals(CatchUpMessageKind.NothingUnread, messageKind(MSG_NO_UNREAD))
        assertEquals(CatchUpMessageKind.Refused, messageKind(MSG_REFUSED))
        assertEquals(CatchUpMessageKind.Failed, messageKind("No connection"))
    }

    @Test
    fun lowPriorityTitleCarriesTheCount() {
        assertEquals("🔕 Low priority · 31", lowPriorityTitle(31))
    }

    private fun stored(unread: Int, apps: Int, at: Long) =
        StoredCatchUp(CatchUp(emptyList(), emptyList(), emptyList()), at, emptyList(), unread, apps, 0)
}
