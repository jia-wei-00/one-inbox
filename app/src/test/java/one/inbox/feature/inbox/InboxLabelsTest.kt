package one.inbox.feature.inbox

import org.junit.Assert.assertEquals
import org.junit.Test

class InboxLabelsTest {

    @Test
    fun subtitleSaysAllCaughtUpWhenNothingUnread() {
        assertEquals("All caught up", inboxSubtitle(totalUnread = 0, appsWithUnread = 0))
    }

    @Test
    fun subtitleUsesSingularForOneApp() {
        assertEquals("1 unread across 1 app", inboxSubtitle(totalUnread = 1, appsWithUnread = 1))
        assertEquals("5 unread across 1 app", inboxSubtitle(totalUnread = 5, appsWithUnread = 1))
    }

    @Test
    fun subtitleUsesPluralForSeveralApps() {
        assertEquals("7 unread across 3 apps", inboxSubtitle(totalUnread = 7, appsWithUnread = 3))
    }

    @Test
    fun fabLabelShowsCountOnlyWhenUnread() {
        assertEquals("Catch up · 7", catchUpLabel(7))
        assertEquals("Catch up", catchUpLabel(0))
    }

    @Test
    fun filterChipNamesTheSelectedAppOrAllApps() {
        val labels = mapOf("com.whatsapp" to "WhatsApp")
        assertEquals("All apps", filterChipLabel(null) { labels.getValue(it) })
        assertEquals("WhatsApp", filterChipLabel("com.whatsapp") { labels.getValue(it) })
    }
}
