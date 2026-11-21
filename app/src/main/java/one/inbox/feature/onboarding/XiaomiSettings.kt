package one.inbox.feature.onboarding

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

/** Settings screens the first-run checklist opens. Xiaomi screens may be missing or unexported, so callers use [launchFirst]. */
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

/**
 * Starts the first intent that actually launches. A screen can resolve yet still throw (e.g. an
 * unexported HyperOS activity raises SecurityException), so each is tried in order. False if none open.
 */
fun launchFirst(context: Context, vararg intents: Intent): Boolean = intents.any { intent ->
    try {
        context.startActivity(Intent(intent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (_: Exception) {
        false
    }
}
