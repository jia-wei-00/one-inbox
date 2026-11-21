package one.inbox.feature.catchup

import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import one.inbox.core.AppInfoProvider
import one.inbox.data.InboxRepository
import one.inbox.summary.CatchUpOutcome
import one.inbox.summary.CatchUpRepository
import one.inbox.summary.StoredCatchUp
import javax.inject.Inject

const val MSG_NO_UNREAD = "Nothing unread 🎉"
const val MSG_NO_API_KEY = "Add your Claude API key in Settings"
const val MSG_REFUSED = "Claude declined to summarize this batch."

/** Display-ready name parts for one summarized conversation. [title] is null if the conversation row is gone; [appName] is null only if the key is malformed. */
data class ItemLabel(val title: String?, val appName: String?)

data class CatchUpUiState(
    val latest: StoredCatchUp? = null,
    val newSince: Int = 0,
    val running: Boolean = false,
    val message: String? = null,
    val labels: Map<String, ItemLabel> = emptyMap(),
)

@HiltViewModel
class CatchUpViewModel @Inject constructor(
    private val repo: CatchUpRepository,
    private val inbox: InboxRepository,
    private val appInfo: AppInfoProvider,
) : ViewModel() {
    private val message = repo.lastOutcome.map { outcome ->
        when (outcome) {
            null, is CatchUpOutcome.Done -> null
            CatchUpOutcome.NoUnread -> MSG_NO_UNREAD
            CatchUpOutcome.NoApiKey -> MSG_NO_API_KEY
            CatchUpOutcome.Refused -> MSG_REFUSED
            is CatchUpOutcome.Failed -> outcome.message
        }
    }

    private val latestWithLabels = repo.latest.map { stored ->
        stored to (stored?.let { labelsFor(it) } ?: emptyMap())
    }

    val state: StateFlow<CatchUpUiState> = combine(
        latestWithLabels, repo.newSinceCount, repo.running, message,
    ) { (latest, labels), newSince, isRunning, msg ->
        CatchUpUiState(latest, newSince, isRunning, msg, labels)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CatchUpUiState())

    private suspend fun labelsFor(stored: StoredCatchUp): Map<String, ItemLabel> {
        val c = stored.catchUp
        return (c.needsReply + c.worthKnowing + c.lowPriority).map { it.conversationKey }.distinct()
            .map { key ->
                val conv = inbox.conversation(key).first()
                val pkg = conv?.packageName ?: packageOf(key)
                key to ItemLabel(conv?.title, pkg?.let(appInfo::label))
            }.toMap()
    }

    /** The source app's icon for a summarized conversation, or null if the key is malformed or the app has none. */
    fun icon(conversationKey: String): ImageBitmap? = packageOf(conversationKey)?.let(appInfo::icon)

    /** The package part of a conversation key (`"$userId|$pkg|$rest"`), or null if the key doesn't split. */
    private fun packageOf(key: String): String? = key.split("|", limit = 3).takeIf { it.size >= 3 }?.get(1)

    /** Runs in the repository's application scope, so closing this screen does not cancel it. */
    fun summarize() = repo.start()

    fun markAllRead() {
        viewModelScope.launch { repo.markSummaryRead() }
    }
}
