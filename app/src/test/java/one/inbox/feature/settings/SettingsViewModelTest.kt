package one.inbox.feature.settings

import android.content.Context
import android.content.Intent
import androidx.room.Room
import org.robolectric.RuntimeEnvironment
import androidx.compose.ui.graphics.ImageBitmap
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import one.inbox.core.AppInfoProvider
import one.inbox.core.ChatApps
import one.inbox.capture.CapturedMessage
import one.inbox.data.InboxCleaner
import one.inbox.data.InboxRepository
import one.inbox.data.db.AppDatabase
import one.inbox.data.settings.ApiKeyStore
import one.inbox.data.settings.SettingsStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SettingsViewModelTest {
    @get:Rule val tmp = TemporaryFolder()

    private val appInfo = object : AppInfoProvider {
        override fun label(pkg: String) = "label:$pkg"
        override fun icon(pkg: String): ImageBitmap? = null
        override fun isLaunchable(pkg: String) = pkg == "com.example.chat"
        override fun launchIntent(pkg: String): Intent? = null
    }
    private var failing = false
    private val keys = object : ApiKeyStore {
        override suspend fun get(): String? = null
        override suspend fun set(key: String) { if (failing) throw IllegalStateException("keystore") }
        override suspend fun clear() { if (failing) throw IllegalStateException("keystore") }
    }

    private lateinit var db: AppDatabase
    private lateinit var repo: InboxRepository

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val context = RuntimeEnvironment.getApplication() as Context
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        repo = InboxRepository(db.inboxDao())
    }

    @After fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    private fun kotlinx.coroutines.test.TestScope.vm(): Pair<SettingsViewModel, SettingsStore> {
        val store = SettingsStore(
            PreferenceDataStoreFactory.create(scope = backgroundScope) { File(tmp.root, "t.preferences_pb") },
        )
        return SettingsViewModel(store, keys, appInfo, repo, InboxCleaner(repo, store)) to store
    }

    @Test
    fun seenNonLaunchablePackagesAreHidden() = runTest {
        val (vm, store) = vm()
        store.addSeenPackage("com.example.chat")
        store.addSeenPackage("com.android.systemui")
        store.setAllowed("com.example.allowed", true)
        val pkgs = vm.state.first { "com.example.chat" in it.apps.map { a -> a.packageName } }.apps.map { it.packageName }
        assertFalse("com.android.systemui" in pkgs)
        assertTrue("com.example.allowed" in pkgs)
        assertTrue(ChatApps.DEFAULT.keys.all { it in pkgs })
    }

    @Test
    fun saveAndClearFailuresDoNotCrash() = runTest {
        val (vm, _) = vm()
        failing = true
        vm.saveKey("sk-x")
        assertEquals(MSG_KEY_SAVE_FAILED, vm.state.first { it.keyMessage != null }.keyMessage)
        vm.clearKey()
        assertEquals(MSG_KEY_SAVE_FAILED, vm.state.first().keyMessage)
    }

    private fun msg(dedupe: String, conv: String, ts: Long) =
        CapturedMessage(conv, "com.whatsapp", conv, false, "Someone", false, "hi", ts, dedupe)

    @Test
    fun clearReadChatsKeepsUnreadOnesAndReportsTheCount() = runTest {
        val (vm, _) = vm()
        repo.save(listOf(msg("a", "wa:read", 1000), msg("b", "wa:unread", 2000)))
        repo.markConversationRead("wa:read")
        assertEquals(1, vm.state.first { it.readChats == 1 }.readChats)
        vm.clearReadChats()
        val state = vm.state.first { it.cleanupMessage != null && it.readChats == 0 }
        assertEquals("Deleted 1 chat", state.cleanupMessage)
        assertEquals(0, state.readChats)
        assertEquals(listOf("wa:unread"), repo.conversations(null).first().map { it.key })
    }

    @Test
    fun aPeriodThatWouldDeleteChatsAsksFirst() = runTest {
        val (vm, store) = vm()
        val old = System.currentTimeMillis() - 40L * 24 * 60 * 60 * 1000
        repo.save(listOf(msg("a", "wa:old", old)))
        repo.markConversationRead("wa:old")
        assertEquals(0, vm.state.first { it.readChats == 1 }.autoDeleteDays)

        vm.setAutoDeleteDays(30)
        assertEquals(AutoDeleteConfirm(30, 1), vm.state.first { it.confirmAutoDelete != null }.confirmAutoDelete)
        assertEquals(0, store.autoDeleteDays.first())
        vm.cancelAutoDelete()
        assertEquals(null, vm.state.first { it.confirmAutoDelete == null }.confirmAutoDelete)
        assertEquals(1, repo.conversations(null).first().size)

        vm.setAutoDeleteDays(30)
        vm.state.first { it.confirmAutoDelete != null }
        vm.confirmAutoDelete()
        val done = vm.state.first { it.autoDeleteDays == 30 && it.readChats == 0 && it.cleanupMessage != null }
        assertEquals("Deleted 1 chat", done.cleanupMessage)
        assertTrue(repo.conversations(null).first().isEmpty())
    }

    @Test
    fun aPeriodThatDeletesNothingAppliesWithoutAsking() = runTest {
        val (vm, store) = vm()
        repo.save(listOf(msg("a", "wa:recent", System.currentTimeMillis())))
        repo.markConversationRead("wa:recent")
        vm.setAutoDeleteDays(7)
        assertEquals(7, vm.state.first { it.autoDeleteDays == 7 }.autoDeleteDays)
        assertEquals(null, vm.state.value.confirmAutoDelete)
        assertEquals(7, store.autoDeleteDays.first())
        assertEquals(1, repo.conversations(null).first().size)
    }

    @Test
    fun cleanupMessagePluralises() {
        assertEquals("Deleted 1 chat", cleanupMessage(1))
        assertEquals("Deleted 3 chats", cleanupMessage(3))
        assertEquals("No read chats to delete", cleanupMessage(0))
    }
}
