package one.inbox.summary

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import one.inbox.core.ChatApps
import one.inbox.data.InboxRepository
import one.inbox.data.settings.ApiKeyStore
import one.inbox.data.settings.SettingsStore
import javax.inject.Inject

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

class CatchUpRepository @Inject constructor(
    private val inbox: InboxRepository,
    private val settings: SettingsStore,
    private val apiKeyStore: ApiKeyStore,
    private val summarizer: Summarizer,
) {
    private val json = Json { ignoreUnknownKeys = true }

    val latest: Flow<StoredCatchUp?> = settings.lastCatchUpJson.map { raw ->
        raw?.let { runCatching { json.decodeFromString<StoredCatchUp>(it) }.getOrNull() }
    }

    /** Unread incoming messages that the latest summary did not include. */
    val newSinceCount: Flow<Int> = combine(latest, inbox.unreadIds()) { stored, ids ->
        val included = stored?.includedIds?.toSet() ?: emptySet()
        ids.count { it !in included }
    }

    /** Summarizes unread messages. Never marks anything read; failures keep the previous summary. */
    suspend fun run(): CatchUpOutcome {
        val apiKey = withContext(Dispatchers.IO) { apiKeyStore.get() }
        if (apiKey.isNullOrBlank()) return CatchUpOutcome.NoApiKey
        val unread = inbox.unread()
        if (unread.isEmpty()) return CatchUpOutcome.NoUnread

        val conversationKeys = unread.map { it.conversationKey }.distinct()
        val conversations = conversationKeys.associateWith { inbox.conversation(it).first() }
        val input = buildSummaryInput(
            unread.map {
                UnreadMessage(
                    id = it.id,
                    conversationKey = it.conversationKey,
                    appName = ChatApps.displayName(it.packageName) ?: it.packageName,
                    conversationTitle = conversations[it.conversationKey]?.title ?: it.sender,
                    isGroup = conversations[it.conversationKey]?.isGroup ?: false,
                    sender = if (it.isFromMe) "You" else it.sender,
                    text = it.text,
                    timestamp = it.timestamp,
                )
            },
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
}
