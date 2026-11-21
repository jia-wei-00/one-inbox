package one.inbox.feature.inbox

import android.content.Context
import androidx.compose.ui.graphics.ImageBitmap
import androidx.room.Room
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import one.inbox.capture.CapturedMessage
import one.inbox.core.AccessChecker
import one.inbox.core.AppInfoProvider
import one.inbox.data.InboxRepository
import one.inbox.data.db.AppDatabase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class InboxViewModelTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: InboxRepository
    private lateinit var vm: InboxViewModel

    private val appInfo = object : AppInfoProvider {
        override fun label(pkg: String) = "label:$pkg"
        override fun icon(pkg: String): ImageBitmap? = null
        override fun isLaunchable(pkg: String) = true
    }
    private val access = object : AccessChecker {
        override fun hasNotificationAccess() = true
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val context = RuntimeEnvironment.getApplication() as Context
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries().build()
        repo = InboxRepository(db.inboxDao())
        vm = InboxViewModel(repo, appInfo, access)
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    private fun msg(dedupe: String, conv: String, pkg: String, title: String, ts: Long) =
        CapturedMessage(conv, pkg, title, false, "Someone", false, "hi $dedupe", ts, dedupe)

    private suspend fun seed() = repo.save(
        listOf(
            msg("a", "wa:alice", "com.whatsapp", "Alice", 1000),
            msg("b", "wa:alice", "com.whatsapp", "Alice", 2000),
            msg("c", "tg:bob", "org.telegram.messenger", "Bob", 3000),
        ),
    )

    @Test
    fun railListsAppsWithUnreadCounts() = runTest {
        seed()
        val state = vm.state.first { it.rail.size == 2 }
        val byPkg = state.rail.associateBy { it.packageName }
        assertEquals(2, byPkg.getValue("com.whatsapp").unreadCount)
        assertEquals(1, byPkg.getValue("org.telegram.messenger").unreadCount)
        assertEquals("label:com.whatsapp", byPkg.getValue("com.whatsapp").label)
    }

    @Test
    fun selectFiltersConversationsAndNullShowsAll() = runTest {
        seed()
        assertEquals(2, vm.state.first { it.conversations.size == 2 }.conversations.size)
        vm.select("com.whatsapp")
        val filtered = vm.state.first { it.selected == "com.whatsapp" && it.conversations.size == 1 }
        assertEquals("Alice", filtered.conversations.single().title)
        vm.select(null)
        assertEquals(2, vm.state.first { it.selected == null && it.conversations.size == 2 }.conversations.size)
    }
}
