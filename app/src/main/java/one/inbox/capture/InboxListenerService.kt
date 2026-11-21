package one.inbox.capture

import android.content.ComponentName
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
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

    /** Events are queued here and handled one at a time, in arrival order. */
    private val events = Channel<suspend () -> Unit>(Channel.UNLIMITED)

    init {
        scope.launch {
            for (event in events) {
                try {
                    event()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "event failed", e)
                }
            }
        }
    }

    /** Re-reads the showing notifications, so newly allowed apps get captured and the health snapshot stays fresh. */
    private val rescan: suspend () -> Unit = {
        publishActive(settings.allowedPackages.first(), activeSnapshots())
    }

    private var rescanLoop: Job? = null

    override fun onListenerConnected() {
        ListenerState.connected.value = true
        events.trySend(rescan)
        rescanLoop?.cancel()
        rescanLoop = scope.launch {
            while (true) {
                delay(RESCAN_INTERVAL_MS)
                events.trySend(rescan)
            }
        }
    }

    override fun onListenerDisconnected() {
        ListenerState.connected.value = false
        rescanLoop?.cancel()
        rescanLoop = null
        try {
            requestRebind(ComponentName(this, InboxListenerService::class.java))
        } catch (e: Exception) {
            Log.w(TAG, "requestRebind failed", e)
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        events.trySend {
            try {
                settings.addSeenPackage(sbn.packageName)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "couldn't record seen package", e)
            }
            val allowed = settings.allowedPackages.first()
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "posted ${sbn.packageName} extras=${sbn.notification.extras?.keySet()}")
            }
            repository.save(NotificationParser.parse(SnapshotExtractor.from(sbn), allowed))
            publishActive(allowed, activeSnapshots())
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        events.trySend {
            publishActive(settings.allowedPackages.first(), activeSnapshots())
        }
    }

    override fun onDestroy() {
        events.close()
        scope.cancel()
        super.onDestroy()
    }

    private fun activeSnapshots(): List<NotificationSnapshot> =
        (activeNotifications ?: emptyArray()).mapNotNull { sbn ->
            try {
                SnapshotExtractor.from(sbn)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "skipping unreadable notification", e)
                null
            }
        }

    /** Saves the showing messages (save dedupes) and publishes them for the health check. */
    private suspend fun publishActive(allowed: Set<String>, snapshots: List<NotificationSnapshot>) {
        val active = snapshots.flatMap { NotificationParser.parse(it, allowed) }
        repository.save(active)
        ListenerState.activeMessages.value = active
    }

    private companion object {
        const val TAG = "InboxListener"
        const val RESCAN_INTERVAL_MS = 15 * 60 * 1000L
    }
}
