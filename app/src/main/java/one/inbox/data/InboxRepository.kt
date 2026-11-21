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
        val conversations = messages.groupBy { it.conversationKey }.map { (key, group) ->
            // On equal timestamps the later message in the notification is the newer one.
            val newest = group.withIndex().maxWith(compareBy({ it.value.timestamp }, { it.index })).value
            ConversationEntity(
                key = key,
                packageName = newest.packageName,
                title = newest.conversationTitle,
                isGroup = newest.isGroup,
                lastMessageAt = newest.timestamp,
                preview = newest.text,
            )
        }
        dao.saveAll(entities, conversations)
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
    suspend fun knownDedupeKeys(keys: Collection<String>): Set<String> =
        if (keys.isEmpty()) emptySet() else dao.knownDedupeKeys(keys).toSet()
}
