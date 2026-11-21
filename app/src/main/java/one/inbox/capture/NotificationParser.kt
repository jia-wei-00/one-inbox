package one.inbox.capture

import one.inbox.core.ChatApps
import java.security.MessageDigest

object NotificationParser {
    private val IGNORED_CATEGORIES = setOf("call", "progress", "service", "transport")
    private val PLACEHOLDERS = listOf(
        Regex("(?i)^\\d+ new messages?( from \\d+ chats?)?$"),
        Regex("(?i)^checking for new messages.*"),
    )

    fun conversationKey(s: NotificationSnapshot): String =
        "${s.userId}|${s.packageName}|${s.shortcutId ?: s.conversationTitle ?: s.title ?: s.notificationKey}"

    fun parse(s: NotificationSnapshot, allowed: Set<String>): List<CapturedMessage> {
        if (s.packageName !in allowed || s.isGroupSummary || s.isOngoing) return emptyList()
        if (s.category in IGNORED_CATEGORIES) return emptyList()

        val key = conversationKey(s)
        val convTitle = s.conversationTitle ?: s.title
            ?: ChatApps.displayName(s.packageName) ?: s.packageName

        fun build(sender: String, fromMe: Boolean, text: String, ts: Long, dedupeInput: String) =
            CapturedMessage(
                conversationKey = key,
                packageName = s.packageName,
                conversationTitle = convTitle,
                isGroup = s.isGroupConversation,
                sender = sender,
                isFromMe = fromMe,
                text = text,
                timestamp = ts,
                dedupeKey = sha256(dedupeInput),
            )

        if (s.messages.isNotEmpty()) {
            return s.messages.mapNotNull { m ->
                val text = resolveText(m) ?: return@mapNotNull null
                if (isPlaceholder(text)) return@mapNotNull null
                val fromMe = m.sender == null || m.sender == s.selfName
                val sender = if (fromMe) "You" else m.sender!!
                val ts = if (m.timestamp <= 0) s.postTime else m.timestamp
                build(sender, fromMe, text, ts, "$key|$sender|$ts|$text")
            }
        }

        val text = s.text
        if (text.isNullOrBlank() || isPlaceholder(text)) return emptyList()
        val sender = s.title ?: convTitle
        return listOf(build(sender, false, text, s.postTime, "$key|$sender|$text"))
    }

    private fun resolveText(m: SnapshotMessage): String? {
        if (!m.text.isNullOrBlank()) return m.text
        val mime = m.mimeType ?: return null
        return when {
            mime.startsWith("image/") -> "📷 Photo"
            mime.startsWith("audio/") -> "🎤 Voice message"
            else -> "📎 Attachment"
        }
    }

    private fun isPlaceholder(text: String): Boolean {
        val t = text.trim()
        return PLACEHOLDERS.any { it.matches(t) }
    }

    private fun sha256(input: String): String =
        MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
