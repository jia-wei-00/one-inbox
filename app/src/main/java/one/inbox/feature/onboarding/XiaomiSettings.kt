package one.inbox.feature.onboarding

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings

/** Settings screens the first-run checklist opens. Xiaomi screens may not exist, so callers use [firstResolvable]. */
object XiaomiSettings {
    private const val APP_LABEL = "One Inbox"

    fun autostartIntent(): Intent = Intent().setComponent(
        ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
    )

    /** HyperOS per-app battery screen. */
    fun batteryIntent(pkg: String): Intent = Intent()
        .setComponent(ComponentName("com.miui.powerkeeper", "com.miui.powerkeeper.ui.HiddenAppsConfigActivity"))
        .putExtra("package_name", pkg)
        .putExtra("package_label", APP_LABEL)

    /** Stock Android battery-optimisation prompt, used when the HyperOS screen is missing. */
    fun ignoreBatteryOptimizationsIntent(pkg: String): Intent =
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$pkg"))

    fun notificationAccessIntent(): Intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)

    fun appDetailsIntent(pkg: String): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg"))
}

/** First intent that resolves to an activity; the last one when none do. */
fun firstResolvable(pm: PackageManager, vararg intents: Intent): Intent =
    intents.firstOrNull { pm.resolveActivity(it, 0) != null } ?: intents.last()
