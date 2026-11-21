package one.inbox.feature.inbox

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.SyncProblem
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import one.inbox.capture.CaptureHealth

fun healthText(health: CaptureHealth): String? = when (health) {
    CaptureHealth.Ok -> null
    CaptureHealth.NoAccess -> "Notification access is off — One Inbox can't see new messages."
    CaptureHealth.Disconnected -> "Capture is paused by the system."
    is CaptureHealth.Missed -> "${health.count} messages on your phone weren't captured."
}

private fun healthAction(health: CaptureHealth): String =
    if (health == CaptureHealth.NoAccess) "Open settings" else "Fix"

private fun healthIcon(health: CaptureHealth): ImageVector = when (health) {
    CaptureHealth.NoAccess -> Icons.Rounded.NotificationsOff
    CaptureHealth.Disconnected -> Icons.Rounded.SyncProblem
    else -> Icons.Rounded.ErrorOutline
}

/** A rounded errorContainer card at the top of the list when capture isn't healthy; nothing when it is. */
@Composable
fun HealthBanner(health: CaptureHealth, onFix: () -> Unit, modifier: Modifier = Modifier) {
    val text = healthText(health) ?: return
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = colors.errorContainer,
        contentColor = colors.onErrorContainer,
    ) {
        Row(Modifier.padding(start = 14.dp, top = 14.dp, end = 8.dp, bottom = 4.dp)) {
            Box(
                Modifier.size(36.dp).background(colors.onErrorContainer.copy(alpha = 0.14f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(healthIcon(health), contentDescription = null, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text,
                    Modifier.padding(top = 2.dp, end = 6.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onErrorContainer,
                )
                TextButton(
                    onClick = onFix,
                    modifier = Modifier.align(Alignment.End),
                    colors = ButtonDefaults.textButtonColors(contentColor = colors.onErrorContainer),
                ) {
                    Text(healthAction(health), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
