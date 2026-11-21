package one.inbox.feature.conversation

import android.content.Intent
import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
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
    val packageName: String = "",
    val peopleCount: Int = 0,
    val canOpenApp: Boolean = false,
    val conversationKey: String = "",
)

@HiltViewModel
class ConversationViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val repo: InboxRepository,
    private val appInfo: AppInfoProvider,
) : ViewModel() {
    private val key = savedState.toRoute<ConversationRoute>().key

    // A PackageManager lookup, so done once per package rather than on every new message.
    private val canOpenApp = repo.conversation(key)
        .map { it?.packageName }
        .distinctUntilChanged()
        .map { pkg -> pkg != null && appInfo.isLaunchable(pkg) }

    val state: StateFlow<ConversationUiState> =
        combine(repo.conversation(key), repo.messages(key), canOpenApp) { conv, msgs, launchable ->
            ConversationUiState(
                appName = conv?.let { appInfo.label(it.packageName) } ?: "",
                title = conv?.title ?: "",
                isGroup = conv?.isGroup ?: false,
                messages = msgs,
                packageName = conv?.packageName ?: "",
                peopleCount = if (conv?.isGroup == true) {
                    msgs.filter { !it.isFromMe }.map { it.sender }.distinct().size
                } else {
                    0
                },
                canOpenApp = conv != null && launchable,
                conversationKey = key,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ConversationUiState())

    suspend fun openAppIntent(): Intent? =
        repo.conversation(key).first()?.packageName?.let { appInfo.launchIntent(it) }

    /** The source app's icon for the header dot; null when the app isn't installed. */
    fun icon(packageName: String): ImageBitmap? = appInfo.icon(packageName)

    init {
        viewModelScope.launch { repo.markConversationRead(key) }
    }
}
