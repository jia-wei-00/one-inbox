package one.inbox.feature.onboarding

import android.content.Context
import android.os.PowerManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect

/** Reads the live statuses, keeps the owner's confirmations and opens the settings screens for [OnboardingContent]. */
@Composable
fun OnboardingScreen(
    onDone: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    var hasAccess by rememberSaveable { mutableStateOf(viewModel.hasNotificationAccess()) }
    var batteryOk by rememberSaveable { mutableStateOf(isIgnoringBattery(context)) }
    var batteryConfirmed by rememberSaveable { mutableStateOf(false) }
    var autostartDone by rememberSaveable { mutableStateOf(false) }
    var lockedInRecents by rememberSaveable { mutableStateOf(false) }

    // The user returns from system settings, so re-read the live statuses.
    LifecycleResumeEffect(Unit) {
        hasAccess = viewModel.hasNotificationAccess()
        batteryOk = isIgnoringBattery(context)
        onPauseOrDispose {}
    }

    OnboardingContent(
        steps = OnboardingSteps(
            access = hasAccess,
            batteryOk = batteryOk,
            batteryConfirmed = batteryConfirmed,
            autostart = autostartDone,
            recents = lockedInRecents,
        ),
        onOpen = { step ->
            val pkg = context.packageName
            when (step) {
                OnboardingStep.Access -> launchFirst(context, XiaomiSettings.notificationAccessIntent())
                OnboardingStep.Battery -> launchFirst(
                    context,
                    XiaomiSettings.batteryIntent(pkg),
                    XiaomiSettings.ignoreBatteryOptimizationsIntent(pkg),
                    XiaomiSettings.appDetailsIntent(pkg),
                )
                OnboardingStep.Autostart ->
                    launchFirst(context, XiaomiSettings.autostartIntent(), XiaomiSettings.appDetailsIntent(pkg))
                // Locking in Recents happens in the task switcher; there is no screen to open.
                OnboardingStep.Recents -> Unit
            }
        },
        onToggle = { step, checked ->
            when (step) {
                // HyperOS "No restrictions" may not show up in PowerManager, so the owner can confirm it.
                OnboardingStep.Battery -> batteryConfirmed = checked
                OnboardingStep.Autostart -> autostartDone = checked
                OnboardingStep.Recents -> lockedInRecents = checked
                // Access is a live system check, never an owner confirmation.
                OnboardingStep.Access -> Unit
            }
        },
        onDone = { viewModel.finish(onDone) },
    )
}

private fun isIgnoringBattery(context: Context): Boolean =
    (context.getSystemService(Context.POWER_SERVICE) as PowerManager)
        .isIgnoringBatteryOptimizations(context.packageName)
