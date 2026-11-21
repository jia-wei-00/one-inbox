package one.inbox.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.serialization.Serializable
import one.inbox.feature.conversation.ConversationScreen
import one.inbox.feature.inbox.InboxScreen

@Serializable object InboxRoute
@Serializable object CatchUpRoute
@Serializable data class ConversationRoute(val key: String)
@Serializable object OnboardingRoute
@Serializable object SettingsRoute

@Composable
fun OneInboxNavGraph(onboardingDone: Boolean) {
    val nav = rememberNavController()
    NavHost(nav, startDestination = if (onboardingDone) InboxRoute else OnboardingRoute) {
        composable<InboxRoute> {
            InboxScreen(
                onOpenConversation = { nav.navigate(ConversationRoute(it)) },
                onCatchUp = { nav.navigate(CatchUpRoute) },
                onSettings = { nav.navigate(SettingsRoute) },
                onOnboarding = { nav.navigate(OnboardingRoute) },
            )
        }
        composable<ConversationRoute> { ConversationScreen(onBack = { nav.popBackStack() }) }
        composable<CatchUpRoute> { ComingSoon() }
        composable<OnboardingRoute> { ComingSoon() }
        composable<SettingsRoute> { ComingSoon() }
    }
}

@Composable
private fun ComingSoon() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Coming soon") }
}
