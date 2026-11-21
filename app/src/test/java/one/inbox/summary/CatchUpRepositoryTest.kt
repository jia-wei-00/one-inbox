package one.inbox.summary

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import one.inbox.capture.CapturedMessage
import one.inbox.data.InboxRepository
import one.inbox.data.db.AppDatabase
import one.inbox.data.settings.ApiKeyStore
import one.inbox.data.settings.SettingsStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

private class FakeKeyStore(var key: String?) : ApiKeyStore {
    override suspend fun get() = key
    override suspend fun set(key: String) { this.key = key }
    override suspend fun clear() { key = null }
}

private class FakeSummarizer(var result: SummarizeResult) : Summarizer {
    var calls = 0
    override suspend fun summarize(prompt: String, apiKey: String): SummarizeResult {
        calls++
        return result
    }
}

private const val REPLY_JSON =
    """{"needs_reply":[{"conversation_id":"c1","summary":"Alice asks about lunch"}],"worth_knowing":[],"low_priority":[{"conversation_id":"c2","summary":"Bob memes"}]}"""

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CatchUpRepositoryTest {
    @get:Rule val tmp = TemporaryFolder()
    private lateinit var db: AppDatabase
    private lateinit var inbox: InboxRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication() as Context, AppDatabase::class.java)
            .allowMainThreadQueries().build()
        inbox = InboxRepository(db.inboxDao())
    }

    @After
    fun tearDown() = db.close()

    private fun TestScope.repo(key: String?, summarizer: Summarizer) = CatchUpRepository(
        inbox,
        SettingsStore(PreferenceDataStoreFactory.create(scope = backgroundScope) { File(tmp.root, "t.preferences_pb") }),
        FakeKeyStore(key),
        summarizer,
    )

    private fun msg(dedupe: String, conv: String, pkg: String, ts: Long) =
        CapturedMessage(conv, pkg, conv, false, "S", false, "text $dedupe", ts, dedupe)

    private suspend fun seed() = inbox.save(
        listOf(
            msg("a", "wa:alice", "com.whatsapp", 1000),
            msg("b", "tg:bob", "org.telegram.messenger", 2000),
        ),
    )

    @Test
    fun noApiKeyShortCircuits() = runTest {
        seed()
        val s = FakeSummarizer(SummarizeResult.Json(REPLY_JSON))
        assertEquals(CatchUpOutcome.NoApiKey, repo(null, s).run())
        assertEquals(0, s.calls)
    }

    @Test
    fun noUnreadShortCircuits() = runTest {
        val s = FakeSummarizer(SummarizeResult.Json(REPLY_JSON))
        assertEquals(CatchUpOutcome.NoUnread, repo("k", s).run())
        assertEquals(0, s.calls)
    }

    @Test
    fun successStoresLatestWithCountsAndKeys() = runTest {
        seed()
        val r = repo("k", FakeSummarizer(SummarizeResult.Json(REPLY_JSON)))
        val outcome = r.run()
        assertTrue(outcome is CatchUpOutcome.Done)
        val stored = r.latest.first()
        assertNotNull(stored)
        assertEquals((outcome as CatchUpOutcome.Done).stored, stored)
        assertEquals(2, stored!!.unreadCount)
        assertEquals(2, stored.appCount)
        assertEquals(0, stored.droppedCount)
        assertEquals(2, stored.includedIds.size)
        // newest conversation (tg:bob) is c1
        assertEquals(listOf("tg:bob"), stored.catchUp.needsReply.map { it.conversationKey })
        assertEquals(listOf("wa:alice"), stored.catchUp.lowPriority.map { it.conversationKey })
        assertEquals(2, inbox.unread().size)
    }

    @Test
    fun markSummaryReadOnlyClearsIncluded() = runTest {
        seed()
        val r = repo("k", FakeSummarizer(SummarizeResult.Json(REPLY_JSON)))
        r.run()
        inbox.save(listOf(msg("c", "wa:alice", "com.whatsapp", 3000)))
        assertEquals(1, r.newSinceCount.first())
        r.markSummaryRead()
        assertEquals(listOf("c"), inbox.unread().map { it.dedupeKey })
        assertEquals(1, r.newSinceCount.first())
    }

    @Test
    fun failureLeavesUnreadAndPreviousSummary() = runTest {
        seed()
        val s = FakeSummarizer(SummarizeResult.Json(REPLY_JSON))
        val r = repo("k", s)
        r.run()
        val first = r.latest.first()
        s.result = SummarizeResult.Failed("No connection")
        assertEquals(CatchUpOutcome.Failed("No connection"), r.run())
        assertEquals(first, r.latest.first())
        s.result = SummarizeResult.Refused
        assertEquals(CatchUpOutcome.Refused, r.run())
        assertEquals(first, r.latest.first())
        s.result = SummarizeResult.Json("not json")
        assertEquals(CatchUpOutcome.Failed("Couldn't read the summary"), r.run())
        assertEquals(first, r.latest.first())
        assertEquals(2, inbox.unread().size)
    }
}
