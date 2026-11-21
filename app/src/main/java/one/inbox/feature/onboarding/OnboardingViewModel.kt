package one.inbox.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import one.inbox.core.AccessChecker
import one.inbox.data.settings.SettingsStore
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val access: AccessChecker,
    private val settings: SettingsStore,
) : ViewModel() {
    fun hasNotificationAccess(): Boolean = access.hasNotificationAccess()

    fun finish(onDone: () -> Unit) {
        viewModelScope.launch {
            settings.setOnboardingDone(true)
            onDone()
        }
    }
}
