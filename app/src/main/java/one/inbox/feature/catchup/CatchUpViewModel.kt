package one.inbox.feature.catchup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
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

/** Display-ready name parts for one summarized conversation. [title] is null if the conversation row is gone. */
data class ItemLabel(val title: String?, val appName: String)

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
    private val running = MutableStateFlow(false)
    private val message = MutableStateFlow<String?>(null)

    private val latestWithLabels = repo.latest.map { stored ->
        stored to (stored?.let { labelsFor(it) } ?: emptyMap())
    }

    val state: StateFlow<CatchUpUiState> = combine(
        latestWithLabels, repo.newSinceCount, running, message,
    ) { (latest, labels), newSince, isRunning, msg ->
        CatchUpUiState(latest, newSince, isRunning, msg, labels)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CatchUpUiState())

    private suspend fun labelsFor(stored: StoredCatchUp): Map<String, ItemLabel> {
        val c = stored.catchUp
        return (c.needsReply + c.worthKnowing + c.lowPriority).map { it.conversationKey }.distinct()
            .mapNotNull { key ->
                inbox.conversation(key).first()?.let { key to ItemLabel(it.title, appInfo.label(it.packageName)) }
            }.toMap()
    }

    fun summarize() {
        if (!running.compareAndSet(false, true)) return
        viewModelScope.launch {
            try {
                message.value = when (val outcome = repo.run()) {
                    is CatchUpOutcome.Done -> null
                    CatchUpOutcome.NoUnread -> MSG_NO_UNREAD
                    CatchUpOutcome.NoApiKey -> MSG_NO_API_KEY
                    CatchUpOutcome.Refused -> MSG_REFUSED
                    is CatchUpOutcome.Failed -> outcome.message
                }
            } finally {
                running.value = false
            }
        }
    }

    fun markAllRead() {
        viewModelScope.launch { repo.markSummaryRead() }
    }
}
