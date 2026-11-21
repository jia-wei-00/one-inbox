package one.inbox.feature.settings

import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import one.inbox.BuildConfig
import one.inbox.core.AppInfoProvider
import one.inbox.core.ChatApps
import one.inbox.data.settings.ApiKeyStore
import one.inbox.data.settings.SettingsStore
import javax.inject.Inject

data class AppToggle(val packageName: String, val label: String, val allowed: Boolean)

data class SettingsUiState(
    val hasKey: Boolean = false,
    val keyMessage: String? = null,
    val apps: List<AppToggle> = emptyList(),
)

const val MSG_KEY_SAVED = "Key saved"
const val MSG_KEY_SAVE_FAILED = "Couldn't save the key"

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsStore,
    private val apiKeys: ApiKeyStore,
    private val appInfo: AppInfoProvider,
) : ViewModel() {
    private val hasKey = MutableStateFlow(false)
    private val keyMessage = MutableStateFlow<String?>(null)

    val state: StateFlow<SettingsUiState> = combine(
        hasKey, keyMessage, settings.allowedPackages, settings.seenPackages,
    ) { key, msg, allowed, seen ->
        val packages = (ChatApps.DEFAULT.keys + allowed + seen.filter(appInfo::isLaunchable))
            .filter { it != BuildConfig.APPLICATION_ID }.distinct()
        val apps = packages.map { AppToggle(it, appInfo.label(it), it in allowed) }
            .sortedBy { it.label.lowercase() }
        SettingsUiState(key, msg, apps)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    init {
        viewModelScope.launch { hasKey.value = readKey() != null }
    }

    fun icon(packageName: String): ImageBitmap? = appInfo.icon(packageName)

    fun saveKey(raw: String, onSaved: () -> Unit = {}) {
        val key = raw.trim()
        if (key.isEmpty()) return
        viewModelScope.launch {
            keyMessage.value = try {
                withContext(Dispatchers.IO) { apiKeys.set(key) }
                hasKey.value = true
                onSaved()
                MSG_KEY_SAVED
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                MSG_KEY_SAVE_FAILED
            }
        }
    }

    fun clearKey() {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { apiKeys.clear() }
                hasKey.value = false
                keyMessage.value = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                keyMessage.value = MSG_KEY_SAVE_FAILED
            }
        }
    }

    fun setAllowed(packageName: String, allowed: Boolean) {
        viewModelScope.launch { settings.setAllowed(packageName, allowed) }
    }

    private suspend fun readKey(): String? = try {
        withContext(Dispatchers.IO) { apiKeys.get() }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

}
