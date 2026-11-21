package one.inbox.capture

import android.app.Notification
import android.content.Context
import android.os.Process
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import org.robolectric.RuntimeEnvironment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SnapshotExtractorTest {
    private val context: Context = RuntimeEnvironment.getApplication()

    @Suppress("DEPRECATION")
    private fun sbn(n: Notification, pkg: String = "com.whatsapp") =
        StatusBarNotification(pkg, pkg, 1, "tag", 0, 0, 0, n, Process.myUserHandle(), 1234L)

    private fun builder() = NotificationCompat.Builder(context, "ch").setSmallIcon(android.R.drawable.sym_def_app_icon)

    @Test
    fun extractsMessagingStyle() {
        val me = Person.Builder().setName("Me").build()
        val alice = Person.Builder().setName("Alice").build()
        val style = NotificationCompat.MessagingStyle(me)
            .setConversationTitle("Family")
            .setGroupConversation(true)
            .addMessage("hello", 1000L, alice)
            .addMessage("hi back", 2000L, null as Person?)
        val n = builder().setStyle(style).setShortcutId("sc1").build()

        val s = SnapshotExtractor.from(sbn(n))

        assertEquals("com.whatsapp", s.packageName)
        assertEquals("Family", s.conversationTitle)
        assertTrue(s.isGroupConversation)
        assertEquals("Me", s.selfName)
        assertEquals("sc1", s.shortcutId)
        assertEquals(1234L, s.postTime)
        assertEquals(
            listOf(
                SnapshotMessage("Alice", "hello", 1000L),
                SnapshotMessage(null, "hi back", 2000L),
            ),
            s.messages,
        )
    }

    @Test
    fun ownerPersonNameMapsToNullSender() {
        val me = Person.Builder().setName("Me").build()
        val style = NotificationCompat.MessagingStyle(me).addMessage("mine", 1L, me)
        val s = SnapshotExtractor.from(sbn(builder().setStyle(style).build()))
        assertNull(s.messages.single().sender)
    }

    @Test
    fun extractsPlainTitleAndText() {
        val n = builder().setContentTitle("Bob").setContentText("yo").build()
        val s = SnapshotExtractor.from(sbn(n))
        assertEquals("Bob", s.title)
        assertEquals("yo", s.text)
        assertTrue(s.messages.isEmpty())
        assertFalse(s.isGroupConversation)
        assertNull(s.selfName)
    }

    @Test
    fun flagsAreRead() {
        val n = builder().setGroupSummary(true).setOngoing(true).setCategory("msg").build()
        val s = SnapshotExtractor.from(sbn(n))
        assertTrue(s.isGroupSummary)
        assertTrue(s.isOngoing)
        assertEquals("msg", s.category)

        val plain = SnapshotExtractor.from(sbn(builder().build()))
        assertFalse(plain.isGroupSummary)
        assertFalse(plain.isOngoing)
    }
}
