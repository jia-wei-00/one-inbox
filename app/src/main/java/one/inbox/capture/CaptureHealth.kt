package one.inbox.capture

sealed interface CaptureHealth {
    data object Ok : CaptureHealth
    data object NoAccess : CaptureHealth
    data object Disconnected : CaptureHealth
    data class Missed(val count: Int) : CaptureHealth
}

fun evaluateHealth(
    accessGranted: Boolean,
    listenerConnected: Boolean,
    activeMessages: List<CapturedMessage>,
    knownKeys: Set<String>,
): CaptureHealth {
    if (!accessGranted) return CaptureHealth.NoAccess
    if (!listenerConnected) return CaptureHealth.Disconnected
    val missed = activeMessages.count { !it.isFromMe && it.dedupeKey !in knownKeys }
    return if (missed > 0) CaptureHealth.Missed(missed) else CaptureHealth.Ok
}
