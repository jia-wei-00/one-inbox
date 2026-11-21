package one.inbox.feature.catchup

import android.content.Context
import androidx.compose.ui.graphics.ImageBitmap
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import one.inbox.capture.CapturedMessage
import one.inbox.core.AppInfoProvider
import one.inbox.data.InboxRepository
import one.inbox.data.db.AppDatabase
import one.inbox.data.settings.ApiKeyStore
import one.inbox.data.settings.SettingsStore
import one.inbox.summary.CatchUpRepository
import one.inbox.summary.SummarizeResult
import one.inbox.summary.Summarizer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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

private class GatedSummarizer(val gate: CompletableDeferred<Unit>, val json: String) : Summarizer {
    override suspend fun summarize(prompt: String, apiKey: String): SummarizeResult {
        gate.await()
        return SummarizeResult.Json(json)
    }
}

private const val REPLY_JSON =
    """{"needs_reply":[{"conversation_id":"c1","summary":"Bob asks about lunch"}],"worth_knowing":[],"low_priority":[]}"""

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CatchUpViewModelTest {
    @get:Rule val tmp = TemporaryFolder()
    private lateinit var db: AppDatabase
    private lateinit var inbox: InboxRepository

    private val appInfo = object : AppInfoProvider {
        override fun label(pkg: String) = "label:$pkg"
        override fun icon(pkg: String): ImageBitmap? = null
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication() as Context, AppDatabase::class.java)
            .allowMainThreadQueries().build()
        inbox = InboxRepository(db.inboxDao())
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    private fun TestScope.vm(key: String?, summarizer: Summarizer): CatchUpViewModel {
        val repo = CatchUpRepository(
            inbox,
            SettingsStore(PreferenceDataStoreFactory.create(scope = backgroundScope) { File(tmp.root, "t.preferences_pb") }),
            FakeKeyStore(key),
            summarizer,
        )
        return CatchUpViewModel(repo, inbox, appInfo).also {
            it.state.onEach { }.launchIn(backgroundScope)
        }
    }

    private suspend fun seed() = inbox.save(
        listOf(
            CapturedMessage("wa:alice", "com.whatsapp", "Alice", false, "A", false, "hi", 1000, "a"),
            CapturedMessage("tg:bob", "org.telegram.messenger", "Bob", false, "B", false, "yo", 2000, "b"),
        ),
    )

    @Test
    fun summarizeShowsRunningThenLatest() = runTest {
        seed()
        val gate = CompletableDeferred<Unit>()
        val vm = vm("k", GatedSummarizer(gate, REPLY_JSON))
        vm.summarize()
        assertTrue(vm.state.first { it.running }.running)
        vm.summarize() // ignored while running
        gate.complete(Unit)
        val s = vm.state.first { !it.running && it.latest != null }
        assertNull(s.message)
        assertEquals(2, s.latest!!.unreadCount)
        val label = s.labels["tg:bob"]
        assertNotNull(label)
        assertEquals("Bob", label!!.title)
        assertEquals("label:org.telegram.messenger", label.appName)
    }

    @Test
    fun noApiKeySetsMessage() = runTest {
        seed()
        val vm = vm(null, GatedSummarizer(CompletableDeferred(), REPLY_JSON))
        vm.summarize()
        val s = vm.state.first { it.message != null }
        assertFalse(s.running)
        assertEquals("Add your Claude API key in Settings", s.message)
        assertNull(s.latest)
    }

    @Test
    fun markAllReadDelegatesToRepository() = runTest {
        seed()
        val gate = CompletableDeferred<Unit>().also { it.complete(Unit) }
        val vm = vm("k", GatedSummarizer(gate, REPLY_JSON))
        vm.summarize()
        vm.state.first { it.latest != null && !it.running }
        assertEquals(2, inbox.unread().size)
        vm.markAllRead()
        withContext(Dispatchers.Default) {
            withTimeout(5_000) { while (inbox.unread().isNotEmpty()) delay(20) }
        }
        assertEquals(0, inbox.unread().size)
    }
}
