package one.inbox.feature.inbox

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import one.inbox.capture.CaptureHealth
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val HEALTH_INTERVAL_MS = 15 * 60 * 1000L

fun formatListTime(millis: Long, now: LocalDate = LocalDate.now(), zone: ZoneId = ZoneId.systemDefault()): String {
    val dt = Instant.ofEpochMilli(millis).atZone(zone)
    val pattern = if (dt.toLocalDate() == now) "HH:mm" else "MMM d"
    return DateTimeFormatter.ofPattern(pattern, Locale.getDefault()).format(dt)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InboxScreen(
    onOpenConversation: (String) -> Unit,
    onCatchUp: () -> Unit,
    onSettings: () -> Unit,
    onOnboarding: () -> Unit,
    viewModel: InboxViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                viewModel.refreshHealth()
                delay(HEALTH_INTERVAL_MS)
            }
        }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("One Inbox") },
                actions = {
                    IconButton(onClick = onSettings) { Icon(Icons.Filled.Settings, "Settings") }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            HealthBanner(state.health, onFix = {
                if (state.health == CaptureHealth.NoAccess) {
                    context.startActivity(
                        Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                } else {
                    onOnboarding()
                }
            })
            Row(Modifier.fillMaxSize()) {
                AppRail(state.rail, state.selected, viewModel::icon, onCatchUp, viewModel::select)
                LazyColumn(Modifier.weight(1f)) {
                    items(state.conversations, key = { it.key }) { row ->
                        Row(
                            Modifier.fillMaxWidth().clickable { onOpenConversation(row.key) }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(row.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    row.preview,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(formatListTime(row.lastMessageAt), style = MaterialTheme.typography.labelSmall)
                                if (row.unreadCount > 0) {
                                    Badge(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary,
                                    ) { Text(row.unreadCount.toString(), color = MaterialTheme.colorScheme.onPrimary) }
                                }
                            }
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}
