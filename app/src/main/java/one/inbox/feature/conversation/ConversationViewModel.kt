package one.inbox.feature.conversation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import one.inbox.core.AppInfoProvider
import one.inbox.data.InboxRepository
import one.inbox.data.db.MessageEntity
import one.inbox.navigation.ConversationRoute
import javax.inject.Inject

data class ConversationUiState(
    val appName: String = "",
    val title: String = "",
    val isGroup: Boolean = false,
    val messages: List<MessageEntity> = emptyList(),
)

@HiltViewModel
class ConversationViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val repo: InboxRepository,
    private val appInfo: AppInfoProvider,
) : ViewModel() {
    private val key = savedState.toRoute<ConversationRoute>().key

    val state: StateFlow<ConversationUiState> =
        combine(repo.conversation(key), repo.messages(key)) { conv, msgs ->
            ConversationUiState(
                appName = conv?.let { appInfo.label(it.packageName) } ?: "",
                title = conv?.title ?: "",
                isGroup = conv?.isGroup ?: false,
                messages = msgs,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ConversationUiState())

    init {
        viewModelScope.launch { repo.markConversationRead(key) }
    }
}
