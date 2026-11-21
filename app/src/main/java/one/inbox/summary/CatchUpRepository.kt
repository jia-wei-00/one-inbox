package one.inbox.summary

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import one.inbox.core.ChatApps
import one.inbox.core.di.ApplicationScope
import one.inbox.data.InboxRepository
import one.inbox.data.db.ConversationEntity
import one.inbox.data.db.MessageEntity
import one.inbox.data.settings.ApiKeyStore
import one.inbox.data.settings.SettingsStore
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class StoredCatchUp(
    val catchUp: CatchUp,
    val generatedAt: Long,
    val includedIds: List<Long>,
    val unreadCount: Int,
    val appCount: Int,
    val droppedCount: Int,
)

sealed interface CatchUpOutcome {
    data class Done(val stored: StoredCatchUp) : CatchUpOutcome
    data object NoUnread : CatchUpOutcome
    data object NoApiKey : CatchUpOutcome
    data object Refused : CatchUpOutcome
    data class Failed(val message: String) : CatchUpOutcome
}

@Singleton
class CatchUpRepository @Inject constructor(
    private val inbox: InboxRepository,
    private val settings: SettingsStore,
    private val apiKeyStore: ApiKeyStore,
    private val summarizer: Summarizer,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val json = Json { ignoreUnknownKeys = true }

    private val _running = MutableStateFlow(false)

    /** True while a catch-up started by [start] is in flight. */
    val running: StateFlow<Boolean> = _running.asStateFlow()

    private val _lastOutcome = MutableStateFlow<CatchUpOutcome?>(null)

    /** Outcome of the most recent [start] run, null before the first one finishes. */
    val lastOutcome: StateFlow<CatchUpOutcome?> = _lastOutcome.asStateFlow()

    val latest: Flow<StoredCatchUp?> = settings.lastCatchUpJson.map { raw ->
        raw?.let { runCatching { json.decodeFromString<StoredCatchUp>(it) }.getOrNull() }
    }

    /** Unread incoming messages that the latest summary did not include. */
    val newSinceCount: Flow<Int> = combine(latest, inbox.unreadIds()) { stored, ids ->
        val included = stored?.includedIds?.toSet() ?: emptySet()
        ids.count { it !in included }
    }

    /**
     * Runs a catch-up in the application scope, so leaving the screen never throws away a paid
     * Claude call. Ignored while one is already running.
     */
    fun start() {
        if (!_running.compareAndSet(false, true)) return
        scope.launch {
            try {
                _lastOutcome.value = run()
            } finally {
                _running.value = false
            }
        }
    }

    /** Summarizes unread messages. Never marks anything read; failures keep the previous summary. */
    suspend fun run(): CatchUpOutcome {
        val apiKey = withContext(Dispatchers.IO) { apiKeyStore.get() }
        if (apiKey.isNullOrBlank()) return CatchUpOutcome.NoApiKey
        val unread = inbox.unread()
        if (unread.isEmpty()) return CatchUpOutcome.NoUnread

        val conversationKeys = unread.map { it.conversationKey }.distinct()
        val conversations = conversationKeys.associateWith { inbox.conversation(it).first() }
        // The owner's replies since the oldest unread message, so Claude can tell what is answered.
        val own = conversationKeys.flatMap { key ->
            inbox.ownMessagesSince(key, unread.filter { it.conversationKey == key }.minOf { it.timestamp })
        }
        val input = buildSummaryInput(
            unread.map { toUnreadMessage(it, conversations) } + own.map { toUnreadMessage(it, conversations) },
        )
        return when (val result = summarizer.summarize(input.prompt, apiKey)) {
            is SummarizeResult.Refused -> CatchUpOutcome.Refused
            is SummarizeResult.Failed -> CatchUpOutcome.Failed(result.message)
            is SummarizeResult.Json -> {
                val catchUp = try {
                    parseCatchUp(result.text, input.conversationIds)
                } catch (e: SerializationException) {
                    return CatchUpOutcome.Failed("Couldn't read the summary")
                } catch (e: IllegalArgumentException) {
                    return CatchUpOutcome.Failed("Couldn't read the summary")
                }
                val stored = StoredCatchUp(
                    catchUp = catchUp,
                    generatedAt = System.currentTimeMillis(),
                    includedIds = input.includedIds,
                    unreadCount = input.includedIds.size,
                    appCount = input.appCount,
                    droppedCount = input.droppedCount,
                )
                settings.setLastCatchUpJson(json.encodeToString(stored))
                CatchUpOutcome.Done(stored)
            }
        }
    }

    /** Marks read only the messages the latest summary covered. */
    suspend fun markSummaryRead() {
        val stored = latest.first() ?: return
        inbox.markRead(stored.includedIds)
    }

    private fun toUnreadMessage(m: MessageEntity, conversations: Map<String, ConversationEntity?>) =
        UnreadMessage(
            id = m.id,
            conversationKey = m.conversationKey,
            appName = ChatApps.displayName(m.packageName) ?: m.packageName,
            conversationTitle = conversations[m.conversationKey]?.title ?: m.sender,
            isGroup = conversations[m.conversationKey]?.isGroup ?: false,
            sender = if (m.isFromMe) "You" else m.sender,
            text = m.text,
            timestamp = m.timestamp,
            isFromMe = m.isFromMe,
        )
}
