package one.inbox.feature.onboarding

import android.content.ComponentName
import android.content.Intent
import android.content.pm.ActivityInfo
import android.provider.Settings
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class XiaomiSettingsTest {
    private val context = RuntimeEnvironment.getApplication()

    @Test
    fun firstResolvableFallsBackToAppDetails() {
        val pm = context.packageManager
        val details = XiaomiSettings.appDetailsIntent("one.inbox")
        val info = ActivityInfo().apply {
            packageName = "com.android.settings"
            name = "InstalledAppDetails"
        }
        shadowOf(pm).addOrUpdateActivity(info)
        shadowOf(pm).addIntentFilterForActivity(
            ComponentName(info.packageName, info.name),
            android.content.IntentFilter(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                addDataScheme("package")
            },
        )
        val chosen = firstResolvable(pm, XiaomiSettings.autostartIntent(), XiaomiSettings.batteryIntent("one.inbox"), details)
        assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, chosen.action)
    }

    @Test
    fun firstResolvablePrefersFirstMatch() {
        val pm = context.packageManager
        val autostart = XiaomiSettings.autostartIntent()
        shadowOf(pm).addOrUpdateActivity(
            ActivityInfo().apply {
                packageName = autostart.component!!.packageName
                name = autostart.component!!.className
            },
        )
        val chosen = firstResolvable(pm, autostart, XiaomiSettings.appDetailsIntent("one.inbox"))
        assertEquals(autostart.component, chosen.component)
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
