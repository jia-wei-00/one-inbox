package one.inbox.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey val key: String,
    val packageName: String,
    val title: String,
    val isGroup: Boolean,
    val lastMessageAt: Long,
    val preview: String,
)

@Entity(
    tableName = "messages",
    indices = [Index(value = ["dedupeKey"], unique = true), Index("conversationKey")],
)
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dedupeKey: String,
    val conversationKey: String,
    val packageName: String,
    val sender: String,
    val isFromMe: Boolean,
    val text: String,
    val timestamp: Long,
    val unread: Boolean,
)

data class AppSummary(val packageName: String, val unreadCount: Int, val lastMessageAt: Long)

data class ConversationRow(
    val key: String,
    val packageName: String,
    val title: String,
    val isGroup: Boolean,
    val lastMessageAt: Long,
    val preview: String,
    val unreadCount: Int,
)
