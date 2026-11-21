package one.inbox.feature.onboarding

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.provider.Settings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Records launch attempts; intents whose component class (or action) is in [failures] throw. */
private class LaunchRecorder(base: Context, private val failures: Map<String, Exception>) : ContextWrapper(base) {
    val attempts = mutableListOf<Intent>()
    var started: Intent? = null

    override fun startActivity(intent: Intent) {
        attempts += intent
        failures[intent.component?.className ?: intent.action]?.let { throw it }
        started = intent
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class XiaomiSettingsTest {
    private val context = RuntimeEnvironment.getApplication()
    private val autostart = XiaomiSettings.autostartIntent()
    private val battery = XiaomiSettings.batteryIntent("one.inbox")
    private val details = XiaomiSettings.appDetailsIntent("one.inbox")

    @Test
    fun launchFirstSkipsUnexportedAndMissingScreens() {
        val ctx = LaunchRecorder(
            context,
            mapOf(
                autostart.component!!.className to SecurityException("not exported"),
                battery.component!!.className to ActivityNotFoundException(),
            ),
        )
        assertTrue(launchFirst(ctx, autostart, battery, details))
        assertEquals(3, ctx.attempts.size)
        assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, ctx.started!!.action)
        assertTrue(ctx.started!!.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
    }

    @Test
    fun launchFirstStopsAtFirstSuccess() {
        val ctx = LaunchRecorder(context, emptyMap())
        assertTrue(launchFirst(ctx, autostart, details))
        assertEquals(1, ctx.attempts.size)
        assertEquals(autostart.component, ctx.started!!.component)
    }

    @Test
    fun launchFirstReportsFailureWhenNothingOpens() {
        val ctx = LaunchRecorder(
            context,
            mapOf(
                autostart.component!!.className to SecurityException("not exported"),
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS to ActivityNotFoundException(),
            ),
        )
        assertFalse(launchFirst(ctx, autostart, details))
        assertEquals(2, ctx.attempts.size)
        assertNull(ctx.started)
    }

    @Test
    fun batteryIntentCarriesPackageExtras() {
        val intent = XiaomiSettings.batteryIntent("one.inbox")
        assertEquals(
            ComponentName("com.miui.powerkeeper", "com.miui.powerkeeper.ui.HiddenAppsConfigActivity"),
            intent.component,
        )
        assertEquals("one.inbox", intent.getStringExtra("package_name"))
        assertEquals("One Inbox", intent.getStringExtra("package_label"))
    }

    @Test
    fun notificationAccessUsesListenerSettings() {
        assertEquals(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS, XiaomiSettings.notificationAccessIntent().action)
    }
}
