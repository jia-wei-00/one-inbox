package one.inbox.summary

interface Summarizer {
    suspend fun summarize(prompt: String, apiKey: String): SummarizeResult
}

sealed interface SummarizeResult {
    data class Json(val text: String) : SummarizeResult
    data object Refused : SummarizeResult
    data class Failed(val message: String) : SummarizeResult
}
