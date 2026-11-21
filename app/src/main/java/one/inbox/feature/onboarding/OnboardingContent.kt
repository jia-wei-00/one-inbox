package one.inbox.feature.onboarding

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import one.inbox.ui.theme.DoneGreen
import one.inbox.ui.theme.OneInboxTheme

enum class OnboardingStep { Access, Battery, Autostart, Recents }

/**
 * Where each setup step stands. [access] and [batteryOk] are live system checks; the rest are
 * the owner's own confirmations (HyperOS may not report "No restrictions" to PowerManager).
 */
data class OnboardingSteps(
    val access: Boolean,
    val batteryOk: Boolean,
    val batteryConfirmed: Boolean,
    val autostart: Boolean,
    val recents: Boolean,
)

const val RESTRICTED_SETTINGS_HINT = "Greyed out? Open App info → ⋮ → Allow restricted settings, then try again."

/**
 * The first-run checklist: a hero, one card per step (numbered circle, status line, the owner's
 * checkbox where the system can't tell, a tonal "Open") and a pinned "Done", enabled once
 * notification access is granted.
 */
@Composable
fun OnboardingContent(
    steps: OnboardingSteps,
    onOpen: (OnboardingStep) -> Unit,
    onToggle: (OnboardingStep, Boolean) -> Unit,
    onDone: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Scaffold(
        containerColor = colors.background,
        contentWindowInsets = WindowInsets(0),
        bottomBar = { DoneBar(steps.access, onDone) },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Hero()
            StepCard(
                number = 1,
                title = "Notification access",
                done = steps.access,
                hint = "Needed",
                note = RESTRICTED_SETTINGS_HINT.takeUnless { steps.access },
                onOpen = { onOpen(OnboardingStep.Access) },
            )
            StepCard(
                number = 2,
                title = "Battery: No restrictions",
                done = steps.batteryOk || steps.batteryConfirmed,
                hint = "Set it, then tick the box",
                // Once PowerManager confirms it, the owner has nothing left to tick.
                checked = steps.batteryConfirmed.takeUnless { steps.batteryOk },
                onChecked = { onToggle(OnboardingStep.Battery, it) },
                onOpen = { onOpen(OnboardingStep.Battery) },
            )
            StepCard(
                number = 3,
                title = "Autostart",
                done = steps.autostart,
                hint = "Enable it, then tick the box",
                checked = steps.autostart,
                onChecked = { onToggle(OnboardingStep.Autostart, it) },
                onOpen = { onOpen(OnboardingStep.Autostart) },
            )
            // Recents has no settings screen to open: it's a long-press in the task switcher.
            StepCard(
                number = 4,
                title = "Lock One Inbox in Recents (optional)",
                done = steps.recents,
                hint = "Long-press the app card in Recents and tap the lock",
                checked = steps.recents,
                onChecked = { onToggle(OnboardingStep.Recents, it) },
                onOpen = null,
            )
        }
    }
}

@Composable
private fun Hero() {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, bottom = 8.dp)) {
        Text("Set up One Inbox", style = MaterialTheme.typography.headlineLarge, color = colors.onBackground)
        Spacer(Modifier.height(4.dp))
        Text(
            "4 quick steps so no message slips through",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
        )
    }
}

@Composable
private fun StepCard(
    number: Int,
    title: String,
    done: Boolean,
    hint: String,
    onOpen: (() -> Unit)?,
    note: String? = null,
    checked: Boolean? = null,
    onChecked: (Boolean) -> Unit = {},
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = colors.surfaceContainerHigh,
        contentColor = colors.onSurface,
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                StepCircle(number, done)
                Spacer(Modifier.width(StepTextGap))
                Column(Modifier.weight(1f).padding(top = 2.dp)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(2.dp))
                    if (done) {
                        Text(
                            "Done ✓",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = DoneGreen,
                        )
                    } else {
                        Text(hint, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                    }
                    if (note != null) {
                        Spacer(Modifier.height(6.dp))
                        Text(note, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                    }
                }
            }
            if (checked != null || onOpen != null) {
                Row(
                    // Lines up with the title, past the circle.
                    Modifier.fillMaxWidth().padding(start = StepCircleSize + StepTextGap - 12.dp, top = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (checked != null) {
                        Row(
                            Modifier
                                .heightIn(min = 48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .toggleable(value = checked, role = Role.Checkbox, onValueChange = onChecked)
                                .semantics { contentDescription = "$title done" }
                                .padding(start = 12.dp, end = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = checked, onCheckedChange = null)
                            Spacer(Modifier.width(10.dp))
                            Text(
                                "I've done this",
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.clearAndSetSemantics {},
                            )
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    if (onOpen != null) {
                        FilledTonalButton(
                            onClick = onOpen,
                            modifier = Modifier.semantics { contentDescription = "Open $title" },
                        ) {
                            Text("Open", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

/** The step number on a tonal circle, or a green ✓ once the step is done. */
@Composable
private fun StepCircle(number: Int, done: Boolean) {
    val colors = MaterialTheme.colorScheme
    Box(
        Modifier
            .size(StepCircleSize)
            .clip(CircleShape)
            .background(if (done) DoneGreen.copy(alpha = 0.18f) else colors.primaryContainer)
            .clearAndSetSemantics {},
        contentAlignment = Alignment.Center,
    ) {
        if (done) {
            Icon(Icons.Rounded.Check, contentDescription = null, tint = DoneGreen, modifier = Modifier.size(22.dp))
        } else {
            Text(
                number.toString(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = colors.onPrimaryContainer,
            )
        }
    }
}

@Composable
private fun DoneBar(enabled: Boolean, onDone: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.background) {
        Button(
            onClick = onDone,
            enabled = enabled,
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .heightIn(min = 56.dp),
        ) {
            Text("Done", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
    }
}

private val StepCircleSize = 36.dp
private val StepTextGap = 14.dp

@Preview(uiMode = UI_MODE_NIGHT_YES, widthDp = 360, heightDp = 780)
@Composable
private fun OnboardingContentPreview() {
    OneInboxTheme(darkTheme = true) {
        OnboardingContent(
            steps = OnboardingSteps(
                access = true,
                batteryOk = false,
                batteryConfirmed = true,
                autostart = false,
                recents = false,
            ),
            onOpen = {},
            onToggle = { _, _ -> },
            onDone = {},
        )
    }
}
