package one.inbox.feature.conversation

import android.content.Context
import android.content.Intent
import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import one.inbox.capture.CapturedMessage
import one.inbox.core.AppInfoProvider
import one.inbox.data.InboxRepository
import one.inbox.data.db.AppDatabase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ConversationViewModelTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: InboxRepository
    private val waIcon = ImageBitmap(4, 4)

    private val appInfo = object : AppInfoProvider {
        override fun label(pkg: String) = "label:$pkg"
        override fun icon(pkg: String): ImageBitmap? = if (pkg == "com.whatsapp") waIcon else null
        override fun isLaunchable(pkg: String) = true
        override fun launchIntent(pkg: String): Intent? = Intent("launch:$pkg")
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val context = RuntimeEnvironment.getApplication() as Context
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries().build()
        repo = InboxRepository(db.inboxDao())
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    private fun vm(key: String) =
        ConversationViewModel(SavedStateHandle(mapOf("key" to key)), repo, appInfo)

    private fun m(key: String, group: Boolean, sender: String, me: Boolean, id: String, ts: Long) =
        CapturedMessage(key, "com.whatsapp", "T", group, sender, me, "t$id", ts, id)

    @Test
    fun groupHeaderCountsDistinctSendersAndOpenApp() = runTest {
        repo.save(
            listOf(
                m("g", true, "Mom", false, "1", 1), m("g", true, "Dad", false, "2", 2),
                m("g", true, "Mom", false, "3", 3), m("g", true, "You", true, "4", 4),
            ),
        )
        val s = vm("g").state.first { it.messages.size == 4 }
        assertEquals(2, s.peopleCount)
        assertEquals("com.whatsapp", s.packageName)
        assertTrue(s.canOpenApp)
    }

    @Test
    fun openAppIntentWorksWithoutStateCollection() = runTest {
        repo.save(listOf(m("g", true, "Mom", false, "1", 1)))
        assertEquals("launch:com.whatsapp", vm("g").openAppIntent()?.action)
    }

    @Test
    fun oneToOneHasNoPeopleCount() = runTest {
        repo.save(listOf(m("p", false, "Jason", false, "1", 1)))
        assertEquals(0, vm("p").state.first { it.messages.size == 1 }.peopleCount)
    }

    @Test
    fun exposesConversationKeyAndAppIcon() = runTest {
        repo.save(listOf(m("g", true, "Mom", false, "1", 1)))
        val vm = vm("g")
        assertEquals("g", vm.state.first { it.messages.size == 1 }.conversationKey)
        assertSame(waIcon, vm.icon("com.whatsapp"))
        assertNull(vm.icon("com.other"))
    }
}
