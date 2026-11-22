package one.inbox.data

import kotlinx.coroutines.flow.Flow
import one.inbox.capture.CapturedMessage
import one.inbox.data.db.AppSummary
import one.inbox.data.db.ConversationEntity
import one.inbox.data.db.ConversationRow
import one.inbox.data.db.InboxDao
import one.inbox.data.db.MessageEntity
import javax.inject.Inject

class InboxRepository @Inject constructor(private val dao: InboxDao) {
    suspend fun save(messages: List<CapturedMessage>) {
        if (messages.isEmpty()) return
        val entities = messages.map {
            MessageEntity(
                dedupeKey = it.dedupeKey,
                conversationKey = it.conversationKey,
                packageName = it.packageName,
                sender = it.sender,
                isFromMe = it.isFromMe,
                text = it.text,
                timestamp = it.timestamp,
                unread = !it.isFromMe,
            )
        }
        dao.saveAll(entities) { kept ->
            val keptKeys = kept.mapTo(HashSet()) { it.dedupeKey }
            messages.filter { it.dedupeKey in keptKeys }.groupBy { it.conversationKey }.map { (key, group) ->
                // On equal timestamps the later message in the notification is the newer one.
                val newest = group.withIndex().maxWith(compareBy({ it.value.timestamp }, { it.index })).value
                ConversationEntity(
                    key = key,
                    packageName = newest.packageName,
                    title = newest.conversationTitle,
                    isGroup = newest.isGroup,
                    lastMessageAt = newest.timestamp,
                    preview = previewFor(newest.isGroup, newest.sender, newest.isFromMe, newest.text),
                )
            }
        }
    }

    fun apps(): Flow<List<AppSummary>> = dao.apps()
    fun conversations(packageName: String?): Flow<List<ConversationRow>> = dao.conversations(packageName)
    fun messages(conversationKey: String): Flow<List<MessageEntity>> = dao.messages(conversationKey)
    fun conversation(key: String): Flow<ConversationEntity?> = dao.conversation(key)
    suspend fun markConversationRead(key: String) = dao.markConversationRead(key)
    suspend fun markRead(ids: Collection<Long>) {
        if (ids.isNotEmpty()) dao.markRead(ids)
    }
    suspend fun unread(): List<MessageEntity> = dao.unread()
    suspend fun ownMessagesSince(conversationKey: String, since: Long): List<MessageEntity> =
        dao.ownMessagesSince(conversationKey, since)
    fun unreadIds(): Flow<List<Long>> = dao.unreadIds()
    /** Deletes a conversation for good (its messages aren't captured again); null if it doesn't exist. */
    suspend fun deleteConversation(key: String): DeletedConversation? =
        dao.deleteConversation(key, System.currentTimeMillis())?.let { (conversation, messages) ->
            DeletedConversation(conversation, messages)
        }

    /** Undo for [deleteConversation]. */
    suspend fun restore(deleted: DeletedConversation) = dao.restore(deleted.conversation, deleted.messages)

    /** Deletes conversations with nothing unread whose last message is before [before]; returns how many. */
    suspend fun deleteReadConversations(before: Long = Long.MAX_VALUE): Int =
        dao.deleteReadConversations(before, System.currentTimeMillis())

    /** How many conversations [deleteReadConversations] would delete for [before]. */
    suspend fun countReadConversations(before: Long): Int = dao.countReadConversations(before)

    /** How many conversations have nothing unread. */
    fun readConversationCount(): Flow<Int> = dao.readConversationCount()

    suspend fun knownDedupeKeys(keys: Collection<String>): Set<String> =
        if (keys.isEmpty()) emptySet() else dao.knownDedupeKeys(keys).toSet()
}

/** A deleted conversation, kept in memory so it can be restored. */
data class DeletedConversation(val conversation: ConversationEntity, val messages: List<MessageEntity>)

/** Conversation-list preview: groups show who spoke, 1:1 shows "You: " only for own messages. */
internal fun previewFor(isGroup: Boolean, sender: String, isFromMe: Boolean, text: String): String = when {
    isGroup -> "$sender: $text"
    isFromMe -> "You: $text"
    else -> text
}
