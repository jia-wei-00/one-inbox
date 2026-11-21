package one.inbox.capture

import android.content.ComponentName
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import one.inbox.BuildConfig
import one.inbox.data.InboxRepository
import one.inbox.data.settings.SettingsStore
import javax.inject.Inject

/** Captures chat notifications into the local inbox. Never posts or dismisses notifications. */
@AndroidEntryPoint
class InboxListenerService : NotificationListenerService() {
    @Inject lateinit var repository: InboxRepository
    @Inject lateinit var settings: SettingsStore

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onListenerConnected() {
        ListenerState.connected.value = true
        scope.launch {
            try {
                val allowed = settings.allowedPackages.first()
                val active = activeSnapshots()
                active.forEach { repository.save(NotificationParser.parse(it, allowed)) }
                publishActive(allowed, active)
            } catch (e: Exception) {
                Log.w(TAG, "backfill failed", e)
            }
        }
    }

    override fun onListenerDisconnected() {
        ListenerState.connected.value = false
        requestRebind(ComponentName(this, InboxListenerService::class.java))
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        scope.launch {
            try {
                val allowed = settings.allowedPackages.first()
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "posted ${sbn.packageName} extras=${sbn.notification.extras?.keySet()}")
                }
                repository.save(NotificationParser.parse(SnapshotExtractor.from(sbn), allowed))
                publishActive(allowed, activeSnapshots())
            } catch (e: Exception) {
                Log.w(TAG, "capture failed", e)
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        scope.launch {
            try {
                publishActive(settings.allowedPackages.first(), activeSnapshots())
            } catch (e: Exception) {
                Log.w(TAG, "active refresh failed", e)
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun activeSnapshots(): List<NotificationSnapshot> =
        (activeNotifications ?: emptyArray()).mapNotNull { sbn ->
            try {
                SnapshotExtractor.from(sbn)
            } catch (e: Exception) {
                Log.w(TAG, "skipping unreadable notification", e)
                null
            }
        }

    private fun publishActive(allowed: Set<String>, snapshots: List<NotificationSnapshot>) {
        ListenerState.activeMessages.value = snapshots.flatMap { NotificationParser.parse(it, allowed) }
    }

    private companion object {
        const val TAG = "InboxListener"
    }
}
