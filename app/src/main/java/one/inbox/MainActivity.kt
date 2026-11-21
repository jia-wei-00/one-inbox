package one.inbox

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
        // Draw behind transparent system bars on every version (Android 11 included); each screen
        // pads itself for the status and navigation bars. Bar icons follow the system light/dark
        // mode, as the theme does.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        setContent {
            OneInboxTheme {
                val onboardingDone by produceState<Boolean?>(null) { value = settings.onboardingDone.first() }
                onboardingDone?.let { OneInboxNavGraph(onboardingDone = it) }
            }
        }
    }
}
