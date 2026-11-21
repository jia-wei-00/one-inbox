package one.inbox.feature.inbox

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import one.inbox.capture.CaptureHealth

fun healthText(health: CaptureHealth): String? = when (health) {
    CaptureHealth.Ok -> null
    CaptureHealth.NoAccess -> "Notification access is off — One Inbox can't see new messages."
    CaptureHealth.Disconnected -> "Capture is paused by the system."
    is CaptureHealth.Missed -> "${health.count} messages on your phone weren't captured."
}

@Composable
fun HealthBanner(health: CaptureHealth, onFix: () -> Unit, modifier: Modifier = Modifier) {
    val text = healthText(health) ?: return
    Surface(modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.errorContainer) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text,
                Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            TextButton(onClick = onFix) { Text(if (health == CaptureHealth.NoAccess) "Open settings" else "Fix") }
        }
    }
}
