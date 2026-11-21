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

    /** Atomic dedupe-insert plus conversation upsert; an older message never overwrites a newer preview. */
    @Transaction
    open suspend fun saveAll(messages: List<MessageEntity>, conversations: List<ConversationEntity>) {
        insertMessages(messages)
        for (incoming in conversations) {
            val existing = conversationOnce(incoming.key)
            upsertConversation(
                if (existing != null && existing.lastMessageAt > incoming.lastMessageAt) existing else incoming,
            )
        }
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

    @Query("SELECT id FROM messages WHERE unread = 1 AND isFromMe = 0 ORDER BY id ASC")
    abstract fun unreadIds(): Flow<List<Long>>

    @Query("SELECT dedupeKey FROM messages WHERE dedupeKey IN (:keys)")
    abstract suspend fun knownDedupeKeys(keys: Collection<String>): List<String>
}
