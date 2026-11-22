package one.inbox.data

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import one.inbox.capture.CapturedMessage
import one.inbox.data.db.AppDatabase
import one.inbox.data.settings.SettingsStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class InboxCleanerTest {
    @get:Rule val tmp = TemporaryFolder()
    private lateinit var db: AppDatabase
    private lateinit var repo: InboxRepository

    private val day = 24 * 60 * 60 * 1000L
    private val now = 100 * day

    @Before
    fun setUp() {
        val context = RuntimeEnvironment.getApplication() as Context
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        repo = InboxRepository(db.inboxDao())
    }

    @After
    fun tearDown() = db.close()

    private fun TestScope.cleaner(): Pair<InboxCleaner, SettingsStore> {
        val store = SettingsStore(
            PreferenceDataStoreFactory.create(scope = backgroundScope) { File(tmp.root, "t.preferences_pb") },
        )
        return InboxCleaner(repo, store) to store
    }

    private fun msg(dedupe: String, conv: String, ts: Long) =
        CapturedMessage(conv, "com.whatsapp", conv, false, "Someone", false, "hi", ts, dedupe)

    private suspend fun seed() {
        repo.save(
            listOf(
                msg("a", "wa:read-old", now - 31 * day),
                msg("b", "wa:read-recent", now - 29 * day),
                msg("c", "wa:unread-old", now - 60 * day),
            ),
        )
        repo.markConversationRead("wa:read-old")
        repo.markConversationRead("wa:read-recent")
    }

    private suspend fun keys() = repo.conversations(null).first().map { it.key }.toSet()

    @Test
    fun offByDefaultDeletesNothing() = runTest {
        seed()
        val (cleaner, _) = cleaner()
        assertEquals(0, cleaner.autoClean(now))
        assertEquals(3, keys().size)
    }

    @Test
    fun deletesOnlyReadChatsOlderThanThePeriod() = runTest {
        seed()
        val (cleaner, store) = cleaner()
        store.setAutoDeleteDays(30)
        assertEquals(1, cleaner.autoClean(now))
        assertEquals(setOf("wa:read-recent", "wa:unread-old"), keys())
    }
}
