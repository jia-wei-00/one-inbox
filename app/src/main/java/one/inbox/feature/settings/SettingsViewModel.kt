package one.inbox.feature.settings

import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import one.inbox.BuildConfig
import one.inbox.core.AppInfoProvider
import one.inbox.core.ChatApps
import one.inbox.data.InboxCleaner
import one.inbox.data.InboxRepository
import one.inbox.data.settings.ApiKeyStore
import one.inbox.data.settings.SettingsStore
import javax.inject.Inject

data class AppToggle(val packageName: String, val label: String, val allowed: Boolean)

data class SettingsUiState(
    val hasKey: Boolean = false,
    val keyMessage: String? = null,
    val apps: List<AppToggle> = emptyList(),
    /** Auto-delete period for read chats in days; 0 is Off. */
    val autoDeleteDays: Int = 0,
    /** Chats with nothing unread, i.e. what "Clear read chats" would delete. */
    val readChats: Int = 0,
    val cleanupMessage: String? = null,
    /** A chosen auto-delete period that would delete chats right away, waiting for confirmation. */
    val confirmAutoDelete: AutoDeleteConfirm? = null,
)

data class AutoDeleteConfirm(val days: Int, val chats: Int)

private data class CleanupState(val days: Int, val readChats: Int, val message: String?, val confirm: AutoDeleteConfirm?)

const val MSG_KEY_SAVED = "Key saved"
const val MSG_KEY_SAVE_FAILED = "Couldn't save the key"

fun cleanupMessage(deleted: Int): String = when (deleted) {
    0 -> "No read chats to delete"
    1 -> "Deleted 1 chat"
    else -> "Deleted $deleted chats"
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsStore,
    private val apiKeys: ApiKeyStore,
    private val appInfo: AppInfoProvider,
    private val inbox: InboxRepository,
    private val cleaner: InboxCleaner,
) : ViewModel() {
    private val hasKey = MutableStateFlow(false)
    private val keyMessage = MutableStateFlow<String?>(null)
    private val cleanupStatus = MutableStateFlow<String?>(null)
    private val pendingAutoDelete = MutableStateFlow<AutoDeleteConfirm?>(null)

    // PackageManager lookups (isLaunchable, label) stay off the main thread.
    private val apps = combine(settings.allowedPackages, settings.seenPackages) { allowed, seen ->
        val packages = (ChatApps.DEFAULT.keys + allowed + seen.filter(appInfo::isLaunchable))
            .filter { it != BuildConfig.APPLICATION_ID }.distinct()
        packages.map { AppToggle(it, appInfo.label(it), it in allowed) }
            .sortedBy { it.label.lowercase() }
    }.flowOn(Dispatchers.Default)

    private val cleanup = combine(
        settings.autoDeleteDays,
        inbox.readConversationCount(),
        cleanupStatus,
        pendingAutoDelete,
        ::CleanupState,
    )

    val state: StateFlow<SettingsUiState> = combine(hasKey, keyMessage, apps, cleanup) { key, msg, apps, c ->
        SettingsUiState(key, msg, apps, c.days, c.readChats, c.message, c.confirm)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    init {
        viewModelScope.launch { hasKey.value = readKey() != null }
    }

    fun icon(packageName: String): ImageBitmap? = appInfo.icon(packageName)

    fun saveKey(raw: String, onSaved: () -> Unit = {}) {
        val key = raw.trim()
        if (key.isEmpty()) return
        viewModelScope.launch {
            keyMessage.value = try {
                withContext(Dispatchers.IO) { apiKeys.set(key) }
                hasKey.value = true
                onSaved()
                MSG_KEY_SAVED
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                MSG_KEY_SAVE_FAILED
            }
        }
    }

    fun clearKey() {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { apiKeys.clear() }
                hasKey.value = false
                keyMessage.value = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                keyMessage.value = MSG_KEY_SAVE_FAILED
            }
        }
    }

    fun setAllowed(packageName: String, allowed: Boolean) {
        viewModelScope.launch { settings.setAllowed(packageName, allowed) }
    }

    /**
     * Chooses the auto-delete period. It applies at once; if that would delete chats right away,
     * it waits for [confirmAutoDelete] instead (a mis-tap on "7 days" shouldn't wipe a month of chats).
     */
    fun setAutoDeleteDays(days: Int) {
        viewModelScope.launch {
            val chats = cleaner.wouldDelete(days)
            if (chats > 0) pendingAutoDelete.value = AutoDeleteConfirm(days, chats) else applyAutoDelete(days)
        }
    }

    fun confirmAutoDelete() {
        val pending = pendingAutoDelete.value ?: return
        pendingAutoDelete.value = null
        viewModelScope.launch { applyAutoDelete(pending.days) }
    }

    fun cancelAutoDelete() {
        pendingAutoDelete.value = null
    }

    private suspend fun applyAutoDelete(days: Int) {
        settings.setAutoDeleteDays(days)
        val deleted = cleaner.autoClean()
        if (deleted > 0) cleanupStatus.value = cleanupMessage(deleted)
    }

    /** Deletes every chat with nothing unread (the UI confirms first). */
    fun clearReadChats() {
        viewModelScope.launch {
            cleanupStatus.value = cleanupMessage(inbox.deleteReadConversations())
        }
    }

    private suspend fun readKey(): String? = try {
        withContext(Dispatchers.IO) { apiKeys.get() }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

}
