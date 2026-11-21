package one.inbox.summary

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class CatchUpItem(val conversationKey: String, val summary: String)

@Serializable
data class CatchUp(
    val needsReply: List<CatchUpItem>,
    val worthKnowing: List<CatchUpItem>,
    val lowPriority: List<CatchUpItem>,
)

@Serializable
private data class WireItem(@SerialName("conversation_id") val conversationId: String, val summary: String)

@Serializable
private data class WireCatchUp(
    @SerialName("needs_reply") val needsReply: List<WireItem> = emptyList(),
    @SerialName("worth_knowing") val worthKnowing: List<WireItem> = emptyList(),
    @SerialName("low_priority") val lowPriority: List<WireItem> = emptyList(),
)

private val wireJson = Json { ignoreUnknownKeys = true }

/**
 * Parses the model's JSON reply, mapping "cN" ids back to conversation keys and dropping items
 * with unknown ids. Throws [kotlinx.serialization.SerializationException] on malformed JSON.
 */
fun parseCatchUp(json: String, conversationIds: Map<String, String>): CatchUp {
    val wire = wireJson.decodeFromString<WireCatchUp>(json)
    fun map(items: List<WireItem>) = items.mapNotNull { item ->
        conversationIds[item.conversationId]?.let { CatchUpItem(it, item.summary) }
    }
    return CatchUp(map(wire.needsReply), map(wire.worthKnowing), map(wire.lowPriority))
}
