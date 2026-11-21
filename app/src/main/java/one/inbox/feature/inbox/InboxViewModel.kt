package one.inbox.feature.inbox

import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import one.inbox.capture.CaptureHealth
import one.inbox.capture.ListenerState
import one.inbox.capture.evaluateHealth
import one.inbox.core.AccessChecker
import one.inbox.core.AppInfoProvider
import one.inbox.data.InboxRepository
import one.inbox.data.db.ConversationRow
import javax.inject.Inject

data class RailApp(val packageName: String, val label: String, val unreadCount: Int)

data class InboxUiState(
    val rail: List<RailApp> = emptyList(),
    val selected: String? = null,
    val conversations: List<ConversationRow> = emptyList(),
    val health: CaptureHealth = CaptureHealth.Ok,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class InboxViewModel @Inject constructor(
    private val repo: InboxRepository,
    private val appInfo: AppInfoProvider,
    private val access: AccessChecker,
) : ViewModel() {
    private val selected = MutableStateFlow<String?>(null)
    private val healthTick = MutableStateFlow(0)

    private val rail = repo.apps().map { list ->
        list.map { RailApp(it.packageName, appInfo.label(it.packageName), it.unreadCount) }
    }

    private val conversations = selected.flatMapLatest { repo.conversations(it) }

    private val health = combine(
        ListenerState.connected,
        ListenerState.activeMessages,
        healthTick,
    ) { connected, active, _ -> connected to active }
        .mapLatest { (connected, active) ->
            val granted = access.hasNotificationAccess()
            val known = repo.knownDedupeKeys(active.map { it.dedupeKey })
            evaluateHealth(granted, connected, active, known)
        }

    val state: StateFlow<InboxUiState> = combine(rail, selected, conversations, health) { r, s, c, h ->
        InboxUiState(r, s, c, h)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), InboxUiState())

    fun select(packageName: String?) {
        selected.value = packageName
    }

    /** Re-run the capture health check (called on resume and every 15 minutes while visible). */
    fun refreshHealth() = healthTick.update { it + 1 }

    fun icon(packageName: String): ImageBitmap? = appInfo.icon(packageName)
}
