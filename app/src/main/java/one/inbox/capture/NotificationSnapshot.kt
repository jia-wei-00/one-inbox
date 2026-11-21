package one.inbox.capture

/** A message inside a MessagingStyle notification. [sender] null means the device owner. */
data class SnapshotMessage(
    val sender: String?,
    val text: String?,
    val timestamp: Long,
    val mimeType: String? = null,
)

/** Plain, Android-free copy of the notification fields the parser needs. */
data class NotificationSnapshot(
    val packageName: String,
    val notificationKey: String,
    val userId: Int,
    val postTime: Long,
    val shortcutId: String?,
    val isGroupSummary: Boolean,
    val isOngoing: Boolean,
    val category: String?,
    val title: String?,
    val text: String?,
    val conversationTitle: String?,
    val isGroupConversation: Boolean,
    val selfName: String?,
    val messages: List<SnapshotMessage>,
)
