package one.inbox.data.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import one.inbox.BuildConfig
import one.inbox.core.ChatApps
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
class SettingsStoreTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun kotlinx.coroutines.test.TestScope.store() = SettingsStore(
        PreferenceDataStoreFactory.create(scope = backgroundScope) { File(tmp.root, "test.preferences_pb") },
    )

    @Test
    fun allowlistDefaultsThenPersistsEdits() = runTest {
        val s = store()
        val defaults = ChatApps.defaultAllowlist(BuildConfig.DEBUG)
        assertEquals(defaults, s.allowedPackages.first())
        s.setAllowed("com.whatsapp", false)
        assertEquals(defaults - "com.whatsapp", s.allowedPackages.first())
        s.setAllowed("com.example.new", true)
        assertTrue("com.example.new" in s.allowedPackages.first())
    }

    @Test
    fun onboardingFlagPersists() = runTest {
        val s = store()
        assertFalse(s.onboardingDone.first())
        s.setOnboardingDone(true)
        assertTrue(s.onboardingDone.first())
    }
}
