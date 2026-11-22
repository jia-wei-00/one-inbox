package one.inbox.feature.inbox

import android.content.Intent
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import one.inbox.capture.CaptureHealth
import one.inbox.ui.theme.rememberMotionEnabled
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

/** Wires [InboxViewModel] into [InboxContent] and re-checks capture health while visible. */
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
            viewModel.autoClean()
            while (true) {
                viewModel.refreshHealth()
                delay(HEALTH_INTERVAL_MS)
            }
        }
    }
    val icon = remember(viewModel) { { pkg: String -> viewModel.icon(pkg) } }
    val rail by rememberUpdatedState(state.rail)
    val label = remember { { pkg: String -> rail.firstOrNull { it.packageName == pkg }?.label ?: pkg } }
    InboxContent(
        state = state,
        icon = icon,
        label = label,
        onSelect = viewModel::select,
        onToggleSidebar = viewModel::toggleSidebar,
        onOpenConversation = onOpenConversation,
        onCatchUp = onCatchUp,
        onSettings = onSettings,
        onFixHealth = {
            if (state.health == CaptureHealth.NoAccess) {
                context.startActivity(
                    Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            } else {
                onOnboarding()
            }
        },
        motionEnabled = rememberMotionEnabled(),
        onDelete = viewModel::delete,
        onUndoDelete = viewModel::undoDelete,
        onUndoDismissed = viewModel::dismissUndo,
    )
}
