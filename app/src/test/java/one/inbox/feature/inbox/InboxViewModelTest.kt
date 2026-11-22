package one.inbox.feature.inbox

import android.content.Context
import android.content.Intent
import androidx.compose.ui.graphics.ImageBitmap
import androidx.room.Room
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import one.inbox.capture.CapturedMessage
import one.inbox.core.AccessChecker
import one.inbox.core.AppInfoProvider
import one.inbox.data.InboxCleaner
import one.inbox.data.InboxRepository
import one.inbox.data.db.AppDatabase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.CoroutineScope
import one.inbox.data.settings.SettingsStore
import java.io.File
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
    private lateinit var settings: SettingsStore
    @get:Rule val tmp = TemporaryFolder()

    private val appInfo = object : AppInfoProvider {
        override fun label(pkg: String) = "label:$pkg"
        override fun icon(pkg: String): ImageBitmap? = null
        override fun isLaunchable(pkg: String) = true
        override fun launchIntent(pkg: String): Intent? = null
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
        settings = SettingsStore(
            PreferenceDataStoreFactory.create(scope = CoroutineScope(UnconfinedTestDispatcher())) { File(tmp.root, "t.preferences_pb") },
        )
        vm = newVm()
    }

    private fun newVm() = InboxViewModel(repo, appInfo, access, settings, InboxCleaner(repo, settings))

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

    @Test
    fun totalsAndSidebarToggle() = runTest {
        seed()
        repo.save(listOf(CapturedMessage("sg:c", "org.signal", "C", false, "Me", true, "x", 4000, "d")))
        val state = vm.state.first { it.rail.size == 3 }
        assertEquals(3, state.rail.size)
        assertEquals(3, state.totalUnread)
        assertEquals(2, state.appsWithUnread)
        assertEquals(true, state.sidebarOpen)
        vm.toggleSidebar()
        vm.state.first { !it.sidebarOpen }
        assertEquals(false, settings.sidebarOpen.first())
    }

    @Test
    fun startsNotLoadedAndTheFirstRealEmissionIsLoaded() = runTest {
        assertFalse(vm.state.value.loaded)
        val firstReal = vm.state.drop(1).first()
        assertTrue(firstReal.loaded)
    }

    @Test
    fun deleteRemovesTheChatAndUndoBringsItBack() = runTest {
        seed()
        vm.state.first { it.conversations.size == 2 }
        vm.delete("wa:alice")
        val deleted = vm.state.first { it.conversations.size == 1 && it.deleted != null }
        assertEquals("Alice", deleted.deleted!!.conversation.title)
        assertEquals(1, deleted.totalUnread)
        vm.undoDelete(deleted.deleted!!)
        val restored = vm.state.first { it.conversations.size == 2 && it.deleted == null }
        assertEquals(3, restored.totalUnread)
    }

    @Test
    fun dismissingTheUndoKeepsTheChatDeleted() = runTest {
        seed()
        vm.delete("wa:alice")
        val alice = vm.state.first { it.deleted != null }.deleted!!
        vm.dismissUndo(alice)
        val state = vm.state.first { it.deleted == null }
        assertEquals(listOf("tg:bob"), state.conversations.map { it.key })
        vm.undoDelete(alice)
        assertEquals(1, repo.conversations(null).first().size)
    }

    @Test
    fun aStaleUndoDoesNotTouchTheNewerDelete() = runTest {
        seed()
        vm.delete("wa:alice")
        val alice = vm.state.first { it.deleted?.conversation?.key == "wa:alice" }.deleted!!
        vm.delete("tg:bob")
        val bob = vm.state.first { it.deleted?.conversation?.key == "tg:bob" }.deleted!!
        // Alice's snackbar was replaced: answering it late changes nothing.
        vm.undoDelete(alice)
        vm.dismissUndo(alice)
        assertEquals(bob, vm.state.value.deleted)
        assertTrue(repo.conversations(null).first().isEmpty())
        vm.undoDelete(bob)
        assertEquals(listOf("tg:bob"), vm.state.first { it.conversations.size == 1 }.conversations.map { it.key })
    }

    @Test
    fun autoCleanAppliesTheAutoDeleteSetting() = runTest {
        val old = System.currentTimeMillis() - 40L * 24 * 60 * 60 * 1000
        repo.save(listOf(msg("o", "wa:old", "com.whatsapp", "Old", old), msg("n", "tg:new", "org.telegram.messenger", "New", old)))
        repo.markConversationRead("wa:old")
        settings.setAutoDeleteDays(30)
        val fresh = newVm()
        fresh.autoClean()
        val state = fresh.state.first { it.conversations.size == 1 }
        assertEquals("New", state.conversations.single().title)
        assertNull(state.deleted)
    }

    @Test
    fun autoCleanRunsAtMostEverySixHours() = runTest {
        val old = System.currentTimeMillis() - 40L * 24 * 60 * 60 * 1000
        settings.setAutoDeleteDays(30)
        val now = System.currentTimeMillis()
        vm.autoClean(now)
        // An old chat read just now (e.g. opened from the inbox) survives going back to the inbox.
        repo.save(listOf(msg("o", "wa:old", "com.whatsapp", "Old", old)))
        repo.markConversationRead("wa:old")
        vm.autoClean(now + 60_000)
        assertEquals(listOf("wa:old"), vm.state.first { it.conversations.isNotEmpty() }.conversations.map { it.key })
        vm.autoClean(now + 7L * 60 * 60 * 1000)
        vm.state.first { it.conversations.isEmpty() }
    }
}
