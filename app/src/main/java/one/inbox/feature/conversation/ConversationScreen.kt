package one.inbox.feature.conversation

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import one.inbox.ui.theme.rememberMotionEnabled

/** Wires [ConversationViewModel] into [ConversationContent] and launches the source app. */
@Composable
fun ConversationScreen(onBack: () -> Unit, viewModel: ConversationViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val appIcon = remember(state.packageName) {
        state.packageName.takeIf { it.isNotEmpty() }?.let(viewModel::icon)
    }
    ConversationContent(
        state = state,
        appIcon = appIcon,
        onBack = onBack,
        onOpenApp = {
            scope.launch {
                val intent = viewModel.openAppIntent() ?: return@launch
                try {
                    context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } catch (_: ActivityNotFoundException) {
                    // The app was uninstalled or disabled since the button was shown; nothing to open.
                }
            }
        },
        motionEnabled = rememberMotionEnabled(),
    )
}
