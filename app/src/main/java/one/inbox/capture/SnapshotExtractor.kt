package one.inbox.capture

import android.app.Notification
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat

/** Reads a real [StatusBarNotification] into the Android-free [NotificationSnapshot]. */
object SnapshotExtractor {
    fun from(sbn: StatusBarNotification): NotificationSnapshot {
        val n = sbn.notification
        val extras = n.extras
        val style = NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(n)
        val selfName = style?.user?.name?.toString()
        val messages = style?.messages.orEmpty().map { m ->
            val name = m.person?.name?.toString()
            SnapshotMessage(
                sender = if (m.person == null || name == selfName) null else name,
                text = m.text?.toString(),
                timestamp = m.timestamp,
                mimeType = m.dataMimeType,
            )
        }
        return NotificationSnapshot(
            packageName = sbn.packageName,
            notificationKey = sbn.key,
            userId = sbn.user.hashCode(),
            postTime = sbn.postTime,
            shortcutId = n.shortcutId,
            isGroupSummary = n.flags and Notification.FLAG_GROUP_SUMMARY != 0,
            isOngoing = n.flags and Notification.FLAG_ONGOING_EVENT != 0,
            category = n.category,
            title = extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString(),
            text = extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString(),
            conversationTitle = style?.conversationTitle?.toString(),
            isGroupConversation = style?.isGroupConversation ?: false,
            selfName = selfName,
            messages = messages,
        )
    }
}
