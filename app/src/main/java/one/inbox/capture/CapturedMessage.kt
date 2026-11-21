package one.inbox.capture

data class CapturedMessage(
    val conversationKey: String,
    val packageName: String,
    val conversationTitle: String,
    val isGroup: Boolean,
    val sender: String,
    val isFromMe: Boolean,
    val text: String,
    val timestamp: Long,
    val dedupeKey: String,
)
