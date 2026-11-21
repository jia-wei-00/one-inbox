package one.inbox.data

import android.content.Context
import androidx.room.Room
import org.robolectric.RuntimeEnvironment
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import one.inbox.capture.CapturedMessage
import one.inbox.data.db.AppDatabase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class InboxRepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: InboxRepository

    @Before
    fun setUp() {
        val context = RuntimeEnvironment.getApplication() as Context
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries().build()
        repo = InboxRepository(db.inboxDao())
    }

    @After
    fun tearDown() = db.close()

    private fun msg(
        dedupe: String,
        conv: String = "wa:alice",
        pkg: String = "com.whatsapp",
        title: String = "Alice",
        fromMe: Boolean = false,
        text: String = "hi",
        ts: Long = 1000,
    ) = CapturedMessage(conv, pkg, title, false, "Alice", fromMe, text, ts, dedupe)

    @Test
    fun duplicateDedupeKeysAreStoredOnce() = runTest {
        val list = listOf(msg("a"), msg("b", ts = 2000))
        repo.save(list)
        val first = repo.unread().size
        repo.save(list)
        assertEquals(first, repo.unread().size)
        assertEquals(2, first)
    }

    @Test
    fun ownMessagesAreNeverUnread() = runTest {
        repo.save(listOf(msg("a", fromMe = true), msg("b", ts = 2000)))
        val unread = repo.unread()
        assertEquals(listOf("b"), unread.map { it.dedupeKey })
    }

    @Test
    fun appsListsOnlyPackagesWithMessagesWithUnreadCounts() = runTest {
        repo.save(
            listOf(
                msg("a", ts = 1000),
                msg("b", ts = 2000),
                msg("c", conv = "tg:bob", pkg = "org.telegram.messenger", ts = 3000, fromMe = true),
            ),
        )
        val apps = repo.apps().first()
        assertEquals(listOf("org.telegram.messenger", "com.whatsapp"), apps.map { it.packageName })
        assertEquals(listOf(0, 2), apps.map { it.unreadCount })
        assertEquals(listOf(3000L, 2000L), apps.map { it.lastMessageAt })
    }

    @Test
    fun conversationsFilterByPackageAndNullMeansAll() = runTest {
        repo.save(
            listOf(
                msg("a", ts = 1000),
                msg("b", conv = "tg:bob", pkg = "org.telegram.messenger", title = "Bob", ts = 2000),
            ),
        )
        assertEquals(listOf("tg:bob", "wa:alice"), repo.conversations(null).first().map { it.key })
        val wa = repo.conversations("com.whatsapp").first()
        assertEquals(listOf("wa:alice"), wa.map { it.key })
        assertEquals(1, wa.single().unreadCount)
    }

    @Test
    fun markConversationReadClearsOnlyThatConversation() = runTest {
        repo.save(listOf(msg("a"), msg("b", conv = "wa:bob", title = "Bob")))
        repo.markConversationRead("wa:alice")
        assertEquals(listOf("b"), repo.unread().map { it.dedupeKey })
    }

    @Test
    fun markReadClearsOnlyGivenIds() = runTest {
        repo.save(listOf(msg("a", ts = 1000), msg("b", ts = 2000), msg("c", ts = 3000)))
        val all = repo.unread()
        repo.markRead(listOf(all[0].id, all[2].id))
        assertEquals(listOf("b"), repo.unread().map { it.dedupeKey })
    }

    @Test
    fun renamedConversationKeepsKeyAndUpdatesTitle() = runTest {
        repo.save(listOf(msg("a", title = "Alice", ts = 1000)))
        repo.save(listOf(msg("b", title = "Alice B", text = "new", ts = 2000)))
        val rows = repo.conversations(null).first()
        assertEquals(1, rows.size)
        assertEquals("Alice B", rows.single().title)
        assertEquals("Alice B", repo.conversation("wa:alice").first()?.title)
    }

    @Test
    fun olderMessageDoesNotOverwritePreview() = runTest {
        repo.save(listOf(msg("a", text = "newest", ts = 5000)))
        repo.save(listOf(msg("b", text = "older", ts = 1000)))
        val conv = repo.conversation("wa:alice").first()!!
        assertEquals("newest", conv.preview)
        assertEquals(5000L, conv.lastMessageAt)
        assertTrue(repo.messages("wa:alice").first().map { it.text } == listOf("older", "newest"))
    }

    @Test
    fun equalTimestampsPreviewTheLastMessage() = runTest {
        repo.save(listOf(msg("a", text = "first", ts = 1000), msg("b", text = "second", ts = 1000)))
        assertEquals("second", repo.conversation("wa:alice").first()!!.preview)
    }
}
