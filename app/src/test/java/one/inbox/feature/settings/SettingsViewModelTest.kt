package one.inbox.feature.settings

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
    }
    private var failing = false
    private val keys = object : ApiKeyStore {
        override suspend fun get(): String? = null
        override suspend fun set(key: String) { if (failing) throw IllegalStateException("keystore") }
        override suspend fun clear() { if (failing) throw IllegalStateException("keystore") }
    }

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private fun kotlinx.coroutines.test.TestScope.vm(): Pair<SettingsViewModel, SettingsStore> {
        val store = SettingsStore(
            PreferenceDataStoreFactory.create(scope = backgroundScope) { File(tmp.root, "t.preferences_pb") },
        )
        return SettingsViewModel(store, keys, appInfo) to store
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
}
