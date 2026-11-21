package one.inbox.capture

import kotlinx.coroutines.flow.MutableStateFlow

/** Process-wide listener state, written by the notification listener service. */
object ListenerState {
    val connected = MutableStateFlow(false)
    val activeMessages = MutableStateFlow<List<CapturedMessage>>(emptyList())
}
