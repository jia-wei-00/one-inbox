package one.inbox.summary

import kotlinx.serialization.SerializationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CatchUpParseTest {
    private val ids = mapOf("c1" to "wa:mom", "c2" to "tg:work")

    @Test
    fun parsesBucketsAndMapsIds() {
        val json = """
            {"needs_reply":[{"conversation_id":"c1","summary":"Dinner Sunday?"}],
             "worth_knowing":[{"conversation_id":"c2","summary":"Deploy done"}],
             "low_priority":[],
             "extra":"ignored"}
        """.trimIndent()
        val result = parseCatchUp(json, ids)
        assertEquals(listOf(CatchUpItem("wa:mom", "Dinner Sunday?")), result.needsReply)
        assertEquals(listOf(CatchUpItem("tg:work", "Deploy done")), result.worthKnowing)
        assertTrue(result.lowPriority.isEmpty())
    }

    @Test
    fun unknownConversationIdsAreDropped() {
        val json = """{"needs_reply":[{"conversation_id":"c9","summary":"ghost"},{"conversation_id":"c1","summary":"real"}],"worth_knowing":[],"low_priority":[]}"""
        val result = parseCatchUp(json, ids)
        assertEquals(listOf(CatchUpItem("wa:mom", "real")), result.needsReply)
    }

    @Test(expected = SerializationException::class)
    fun malformedJsonThrows() {
        parseCatchUp("not json {", ids)
    }
}
