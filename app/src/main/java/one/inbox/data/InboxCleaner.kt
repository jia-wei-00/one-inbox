package one.inbox.data

import kotlinx.coroutines.flow.first
import one.inbox.data.settings.SettingsStore
import javax.inject.Inject

/** The "Auto-delete read chats" choices in Settings, in days; 0 is Off. */
val AUTO_DELETE_CHOICES = listOf(0, 7, 30, 90)

private const val DAY_MS = 24 * 60 * 60 * 1000L

/** Applies the auto-delete setting. Chats with anything unread are never touched. */
class InboxCleaner @Inject constructor(
    private val inbox: InboxRepository,
    private val settings: SettingsStore,
) {
    /** Deletes read chats last active more than the chosen number of days ago; returns how many. */
    suspend fun autoClean(now: Long = System.currentTimeMillis()): Int {
        val days = settings.autoDeleteDays.first()
        if (days <= 0) return 0
        return inbox.deleteReadConversations(before = now - days * DAY_MS)
    }
}
