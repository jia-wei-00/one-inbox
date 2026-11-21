package one.inbox.feature.catchup

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import one.inbox.ui.theme.rememberMotionEnabled

/** Wires [CatchUpViewModel] into [CatchUpContent]; the "{relative time}" refreshes every 30s. */
@Composable
fun CatchUpScreen(
    onBack: () -> Unit,
    onOpenConversation: (String) -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: CatchUpViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val now by produceState(System.currentTimeMillis(), state.latest?.generatedAt) {
        value = System.currentTimeMillis()
        while (true) {
            delay(30_000)
            value = System.currentTimeMillis()
        }
    }
    val icon = remember(viewModel) { viewModel::icon }
    CatchUpContent(
        state = state,
        icon = icon,
        onBack = onBack,
        onSummarize = viewModel::summarize,
        onMarkAllRead = viewModel::markAllRead,
        onOpenConversation = onOpenConversation,
        onOpenSettings = onOpenSettings,
        motionEnabled = rememberMotionEnabled(),
        now = now,
    )
}
