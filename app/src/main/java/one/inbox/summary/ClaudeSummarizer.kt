package one.inbox.summary

import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.core.JsonValue
import com.anthropic.errors.AnthropicIoException
import com.anthropic.errors.AnthropicServiceException
import com.anthropic.errors.RateLimitException
import com.anthropic.errors.UnauthorizedException
import com.anthropic.models.messages.JsonOutputFormat
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.OutputConfig
import com.anthropic.models.messages.StopReason
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

internal const val SYSTEM_PROMPT = """You summarize a person's unread chat messages from several apps so they can catch up in one glance.
Sort every conversation into exactly one bucket:
- needs_reply: someone asked them something, is waiting on them, or needs a decision.
- worth_knowing: useful information or updates that need no reply.
- low_priority: chit-chat, memes, promotions, noise.
For each conversation write one short line (max ~20 words) naming who and what. Use the conversation_id given in the "### cN" header.
Write each summary in the main language of that conversation. Messages from "You" were sent by the person; if they already answered, it does not need a reply."""

class ClaudeSummarizer @Inject constructor() : Summarizer {

    override suspend fun summarize(prompt: String, apiKey: String): SummarizeResult =
        withContext(Dispatchers.IO) {
            try {
                val client = AnthropicOkHttpClient.builder().apiKey(apiKey).build()
                try {
                    val message = client.messages().create(buildParams(prompt))
                    if (message.stopReason().orElse(null) == StopReason.REFUSAL) {
                        SummarizeResult.Refused
                    } else {
                        val text = message.content()
                            .flatMap { listOfNotNull(it.text().orElse(null)) }
                            .joinToString("") { it.text() }
                        SummarizeResult.Json(text)
                    }
                } finally {
                    client.close()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: UnauthorizedException) {
                SummarizeResult.Failed("Invalid API key")
            } catch (e: AnthropicIoException) {
                SummarizeResult.Failed("No connection")
            } catch (e: IOException) {
                SummarizeResult.Failed("No connection")
            } catch (e: RateLimitException) {
                SummarizeResult.Failed("Claude is busy, try again shortly")
            } catch (e: AnthropicServiceException) {
                SummarizeResult.Failed(e.message ?: "Claude request failed")
            } catch (e: Exception) {
                SummarizeResult.Failed(e.message ?: "Claude request failed")
            }
        }

    private fun buildParams(prompt: String): MessageCreateParams =
        MessageCreateParams.builder()
            .model("claude-opus-5-5")
            .maxTokens(16000L)
            .system(SYSTEM_PROMPT)
            .addUserMessage(prompt)
            .outputConfig(
                OutputConfig.builder()
                    .effort(OutputConfig.Effort.LOW)
                    .format(JsonOutputFormat.builder().schema(SCHEMA).build())
                    .build(),
            )
            .putAdditionalHeader("anthropic-beta", "server-side-fallback-2026-07-01")
            .putAdditionalBodyProperty("fallbacks", JsonValue.from("default"))
            .build()

    private companion object {
        private val ITEM = mapOf(
            "type" to "object",
            "properties" to mapOf(
                "conversation_id" to mapOf("type" to "string"),
                "summary" to mapOf("type" to "string"),
            ),
            "required" to listOf("conversation_id", "summary"),
            "additionalProperties" to false,
        )

        private fun bucket() = mapOf("type" to "array", "items" to ITEM)

        val SCHEMA: JsonOutputFormat.Schema = JsonOutputFormat.Schema.builder()
            .putAdditionalProperty("type", JsonValue.from("object"))
            .putAdditionalProperty(
                "properties",
                JsonValue.from(
                    mapOf(
                        "needs_reply" to bucket(),
                        "worth_knowing" to bucket(),
                        "low_priority" to bucket(),
                    ),
                ),
            )
            .putAdditionalProperty("required", JsonValue.from(listOf("needs_reply", "worth_knowing", "low_priority")))
            .putAdditionalProperty("additionalProperties", JsonValue.from(false))
            .build()
    }
}
