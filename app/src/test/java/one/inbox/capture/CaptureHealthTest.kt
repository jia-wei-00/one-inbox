package one.inbox.capture

import org.junit.Assert.assertEquals
import org.junit.Test

class CaptureHealthTest {
    private fun msg(key: String, fromMe: Boolean = false) = CapturedMessage(
        conversationKey = "c", packageName = "com.whatsapp", conversationTitle = "Chat",
        isGroup = false, sender = "A", isFromMe = fromMe, text = "hi", timestamp = 1L, dedupeKey = key,
    )

    @Test
    fun noAccessWins() {
        assertEquals(CaptureHealth.NoAccess, evaluateHealth(false, false, listOf(msg("a")), emptySet()))
    }

    @Test
    fun disconnectedWhenAccessButNotConnected() {
        assertEquals(CaptureHealth.Disconnected, evaluateHealth(true, false, listOf(msg("a")), emptySet()))
    }

    @Test
    fun missedCountsOnlyUnknownIncomingMessages() {
        val active = listOf(msg("a"), msg("b"), msg("c", fromMe = true), msg("d"))
        assertEquals(CaptureHealth.Missed(2), evaluateHealth(true, true, active, setOf("a", "x")))
    }

    @Test
    fun okWhenEverythingKnown() {
        assertEquals(CaptureHealth.Ok, evaluateHealth(true, true, listOf(msg("a"), msg("c", true)), setOf("a")))
    }
}
