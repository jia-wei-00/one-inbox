package one.inbox.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
abstract class InboxDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertMessages(messages: List<MessageEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertConversation(conversation: ConversationEntity)

    @Query("SELECT * FROM conversations WHERE `key` = :key")
    abstract suspend fun conversationOnce(key: String): ConversationEntity?

    /**
     * Atomic dedupe-insert plus conversation upsert. Messages the owner deleted are skipped, and
     * [conversationsFor] builds the conversations from the messages that remain.
     */
    @Transaction
    open suspend fun saveAll(
        messages: List<MessageEntity>,
        conversationsFor: (kept: List<MessageEntity>) -> List<ConversationEntity>,
    ) {
        val deleted = deletedDedupeKeys(messages.map { it.dedupeKey }).toSet()
        val kept = messages.filter { it.dedupeKey !in deleted }
        if (kept.isEmpty()) return
        insertMessages(kept)
        conversationsFor(kept).forEach { upsertKeepingNewer(it) }
    }

    /** An older message never overwrites a newer preview. */
    private suspend fun upsertKeepingNewer(incoming: ConversationEntity) {
        val existing = conversationOnce(incoming.key)
        upsertConversation(if (existing != null && existing.lastMessageAt > incoming.lastMessageAt) existing else incoming)
    }

    @Query("SELECT dedupeKey FROM deleted_messages WHERE dedupeKey IN (:keys)")
    abstract suspend fun deletedDedupeKeys(keys: Collection<String>): List<String>

    @Query("SELECT * FROM messages WHERE conversationKey = :key ORDER BY id ASC")
    abstract suspend fun messagesOnce(key: String): List<MessageEntity>

    @Query(
        "INSERT OR REPLACE INTO deleted_messages (dedupeKey, deletedAt) " +
            "SELECT dedupeKey, :now FROM messages WHERE conversationKey IN (:keys)",
    )
    abstract suspend fun tombstoneMessagesOf(keys: Collection<String>, now: Long)

    @Query("DELETE FROM messages WHERE conversationKey IN (:keys)")
    abstract suspend fun deleteMessagesOf(keys: Collection<String>)

    @Query("DELETE FROM conversations WHERE `key` IN (:keys)")
    abstract suspend fun deleteConversationRows(keys: Collection<String>)

    @Query("DELETE FROM deleted_messages WHERE dedupeKey IN (:keys)")
    abstract suspend fun removeTombstones(keys: Collection<String>)

    /** Deletes the conversations and their messages, remembering the messages as deleted. */
    @Transaction
    open suspend fun deleteConversations(keys: Collection<String>, now: Long) {
        keys.chunked(SQL_CHUNK).forEach { chunk ->
            tombstoneMessagesOf(chunk, now)
            deleteMessagesOf(chunk)
            deleteConversationRows(chunk)
        }
    }

    /** Deletes one conversation and returns what was deleted, for undo; null if it doesn't exist. */
    @Transaction
    open suspend fun deleteConversation(key: String, now: Long): Pair<ConversationEntity, List<MessageEntity>>? {
        val conversation = conversationOnce(key) ?: return null
        val messages = messagesOnce(key)
        deleteConversations(listOf(key), now)
        return conversation to messages
    }

    /** Undoes [deleteConversation]: the messages come back with their ids and read state. */
    @Transaction
    open suspend fun restore(conversation: ConversationEntity, messages: List<MessageEntity>) {
        messages.map { it.dedupeKey }.chunked(SQL_CHUNK).forEach { removeTombstones(it) }
        insertMessages(messages)
        upsertKeepingNewer(conversation)
    }

    /** Conversations with no unread message whose last message is before [before]. */
    @Query(
        """
        SELECT c.`key` FROM conversations c
        WHERE c.lastMessageAt < :before
          AND NOT EXISTS (SELECT 1 FROM messages m WHERE m.conversationKey = c.`key` AND m.unread = 1)
        """,
    )
    abstract suspend fun readConversationKeys(before: Long): List<String>

    @Query(
        """
        SELECT COUNT(*) FROM conversations c
        WHERE c.lastMessageAt < :before
          AND NOT EXISTS (SELECT 1 FROM messages m WHERE m.conversationKey = c.`key` AND m.unread = 1)
        """,
    )
    abstract suspend fun countReadConversations(before: Long): Int

    @Query(
        """
        SELECT COUNT(*) FROM conversations c
        WHERE NOT EXISTS (SELECT 1 FROM messages m WHERE m.conversationKey = c.`key` AND m.unread = 1)
        """,
    )
    abstract fun readConversationCount(): Flow<Int>

    /** Deletes every read conversation last active before [before]; returns how many. */
    @Transaction
    open suspend fun deleteReadConversations(before: Long, now: Long): Int {
        val keys = readConversationKeys(before)
        deleteConversations(keys, now)
        return keys.size
    }

    @Query(
        """
        SELECT packageName, COUNT(CASE WHEN unread THEN 1 END) AS unreadCount, MAX(timestamp) AS lastMessageAt
        FROM messages GROUP BY packageName ORDER BY lastMessageAt DESC
        """,
    )
    abstract fun apps(): Flow<List<AppSummary>>

    @Query(
        """
        SELECT c.`key` AS `key`, c.packageName, c.title, c.isGroup, c.lastMessageAt, c.preview,
               COUNT(CASE WHEN m.unread THEN 1 END) AS unreadCount
        FROM conversations c LEFT JOIN messages m ON m.conversationKey = c.`key`
        WHERE :packageName IS NULL OR c.packageName = :packageName
        GROUP BY c.`key` ORDER BY c.lastMessageAt DESC
        """,
    )
    abstract fun conversations(packageName: String?): Flow<List<ConversationRow>>

    @Query("SELECT * FROM messages WHERE conversationKey = :key ORDER BY timestamp ASC, id ASC")
    abstract fun messages(key: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM conversations WHERE `key` = :key")
    abstract fun conversation(key: String): Flow<ConversationEntity?>

    @Query("UPDATE messages SET unread = 0 WHERE conversationKey = :key")
    abstract suspend fun markConversationRead(key: String)

    @Query("UPDATE messages SET unread = 0 WHERE id IN (:ids)")
    abstract suspend fun markRead(ids: Collection<Long>)

    @Query("SELECT * FROM messages WHERE unread = 1 ORDER BY timestamp ASC, id ASC")
    abstract suspend fun unread(): List<MessageEntity>

    @Query(
        "SELECT * FROM messages WHERE conversationKey = :key AND isFromMe = 1 AND timestamp >= :since ORDER BY timestamp ASC, id ASC",
    )
    abstract suspend fun ownMessagesSince(key: String, since: Long): List<MessageEntity>

    @Query("SELECT id FROM messages WHERE unread = 1 AND isFromMe = 0 ORDER BY id ASC")
    abstract fun unreadIds(): Flow<List<Long>>

    /** Keys already captured, including messages the owner deleted. */
    @Query(
        "SELECT dedupeKey FROM messages WHERE dedupeKey IN (:keys) " +
            "UNION SELECT dedupeKey FROM deleted_messages WHERE dedupeKey IN (:keys)",
    )
    abstract suspend fun knownDedupeKeys(keys: Collection<String>): List<String>

    private companion object {
        /** Stays under SQLite's bound-parameter limit. */
        const val SQL_CHUNK = 500
    }
}
