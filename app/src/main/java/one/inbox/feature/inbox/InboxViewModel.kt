package one.inbox.feature.inbox

import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import android.util.Log
import kotlinx.coroutines.CancellationException
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
import one.inbox.data.DeletedConversation
import one.inbox.data.InboxCleaner
import one.inbox.data.InboxRepository
import one.inbox.data.db.ConversationRow
import one.inbox.data.settings.SettingsStore
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RailApp(val packageName: String, val label: String, val unreadCount: Int)

data class InboxUiState(
    val rail: List<RailApp> = emptyList(),
    val selected: String? = null,
    val conversations: List<ConversationRow> = emptyList(),
    val health: CaptureHealth = CaptureHealth.Ok,
    val sidebarOpen: Boolean = true,
    val totalUnread: Int = 0,
    val appsWithUnread: Int = 0,
    /** False until the repository and settings have emitted once; until then nothing real is known. */
    val loaded: Boolean = false,
    /** The chat just swiped away, while its Undo is offered. */
    val deleted: DeletedConversation? = null,
)

private const val AUTO_CLEAN_INTERVAL_MS = 6 * 60 * 60 * 1000L

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class InboxViewModel @Inject constructor(
    private val repo: InboxRepository,
    private val appInfo: AppInfoProvider,
    private val access: AccessChecker,
    private val settings: SettingsStore,
    private val cleaner: InboxCleaner,
) : ViewModel() {
    private val selected = MutableStateFlow<String?>(null)
    private val healthTick = MutableStateFlow(0)
    private val deleted = MutableStateFlow<DeletedConversation?>(null)


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

    val state: StateFlow<InboxUiState> = combine(
        combine(rail, selected, conversations, health) { r, s, c, h -> InboxUiState(r, s, c, h) },
        settings.sidebarOpen,
        deleted,
    ) { base, open, justDeleted ->
        base.copy(
            sidebarOpen = open,
            deleted = justDeleted,
            totalUnread = base.rail.sumOf { it.unreadCount },
            appsWithUnread = base.rail.count { it.unreadCount > 0 },
            loaded = true,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), InboxUiState())

    fun select(packageName: String?) {
        selected.value = packageName
    }

    fun toggleSidebar() {
        viewModelScope.launch { settings.toggleSidebar() }
    }

    private var lastAutoClean = 0L

    /**
     * Applies the auto-delete setting when the inbox comes on screen, at most every few hours, so an
     * old chat just opened (and so marked read) doesn't vanish the moment you go back.
     */
    fun autoClean(now: Long = System.currentTimeMillis()) {
        if (lastAutoClean != 0L && now - lastAutoClean < AUTO_CLEAN_INTERVAL_MS) return
        lastAutoClean = now
        viewModelScope.launch {
            try {
                cleaner.autoClean()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("InboxViewModel", "auto-delete failed", e)
            }
        }
    }

    /** Deletes a chat for good, offering Undo until [undoDelete] or [dismissUndo]. */
    fun delete(conversationKey: String) {
        viewModelScope.launch {
            repo.deleteConversation(conversationKey)?.let { deleted.value = it }
        }
    }

    /** Restores [chat], but only while it is still the one offered (a newer delete replaces the offer). */
    fun undoDelete(chat: DeletedConversation) {
        if (deleted.compareAndSet(chat, null)) viewModelScope.launch { repo.restore(chat) }
    }

    fun dismissUndo(chat: DeletedConversation) {
        deleted.compareAndSet(chat, null)
    }

    /** Re-run the capture health check (called on resume and every 15 minutes while visible). */
    fun refreshHealth() = healthTick.update { it + 1 }

    fun icon(packageName: String): ImageBitmap? = appInfo.icon(packageName)
}
