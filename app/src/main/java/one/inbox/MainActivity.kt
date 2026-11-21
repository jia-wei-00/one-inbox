package one.inbox

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import one.inbox.data.settings.SettingsStore
import one.inbox.navigation.OneInboxNavGraph
import one.inbox.ui.theme.OneInboxTheme
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var settings: SettingsStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            OneInboxTheme {
                val onboardingDone by produceState<Boolean?>(null) { value = settings.onboardingDone.first() }
                onboardingDone?.let { OneInboxNavGraph(onboardingDone = it) }
            }
        }
    }
}
