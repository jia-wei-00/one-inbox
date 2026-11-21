package one.inbox.feature.onboarding

import android.content.Context
import android.os.PowerManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect

@OptIn(ExperimentalMaterial3Api::class)
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

    Scaffold(topBar = { TopAppBar(title = { Text("Set up One Inbox") }) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StepCard(
                title = "1. Notification access",
                status = if (hasAccess) "Granted" else "Needed",
                hint = "Greyed out? Open App info → ⋮ → Allow restricted settings, then try again.",
                onOpen = { launchFirst(context, XiaomiSettings.notificationAccessIntent()) },
            )
            StepCard(
                title = "2. Battery: No restrictions",
                // HyperOS "No restrictions" may not show up in PowerManager, so the owner can confirm it.
                status = if (batteryOk || batteryConfirmed) "Done" else "Set it, then tick the box",
                checked = batteryConfirmed,
                onChecked = { batteryConfirmed = it },
                onOpen = {
                    val pkg = context.packageName
                    launchFirst(
                        context,
                        XiaomiSettings.batteryIntent(pkg),
                        XiaomiSettings.ignoreBatteryOptimizationsIntent(pkg),
                        XiaomiSettings.appDetailsIntent(pkg),
                    )
                },
            )
            StepCard(
                title = "3. Autostart",
                status = if (autostartDone) "Confirmed" else "Enable it, then tick the box",
                checked = autostartDone,
                onChecked = { autostartDone = it },
                onOpen = {
                    launchFirst(context, XiaomiSettings.autostartIntent(), XiaomiSettings.appDetailsIntent(context.packageName))
                },
            )
            StepCard(
                title = "4. Lock One Inbox in Recents (optional)",
                status = if (lockedInRecents) "Confirmed" else "Long-press the app card in Recents and tap the lock",
                checked = lockedInRecents,
                onChecked = { lockedInRecents = it },
                onOpen = null,
            )
            Button(
                enabled = hasAccess,
                onClick = { viewModel.finish(onDone) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Done") }
        }
    }
}

@Composable
private fun StepCard(
    title: String,
    status: String,
    onOpen: (() -> Unit)?,
    hint: String? = null,
    checked: Boolean? = null,
    onChecked: ((Boolean) -> Unit)? = null,
) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(status, style = MaterialTheme.typography.bodyMedium)
                if (hint != null) Text(hint, style = MaterialTheme.typography.bodySmall)
            }
            if (checked != null && onChecked != null) Checkbox(checked = checked, onCheckedChange = onChecked)
            if (onOpen != null) OutlinedButton(onClick = onOpen) { Text("Open") }
        }
    }
}

private fun isIgnoringBattery(context: Context): Boolean =
    (context.getSystemService(Context.POWER_SERVICE) as PowerManager)
        .isIgnoringBatteryOptimizations(context.packageName)
