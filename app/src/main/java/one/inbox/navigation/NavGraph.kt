package one.inbox.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.serialization.Serializable
import one.inbox.feature.catchup.CatchUpScreen
import one.inbox.feature.conversation.ConversationScreen
import one.inbox.feature.inbox.InboxScreen
import one.inbox.feature.onboarding.OnboardingScreen
import one.inbox.feature.settings.SettingsScreen

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
        composable<CatchUpRoute> {
            CatchUpScreen(
                onBack = { nav.popBackStack() },
                onOpenConversation = { nav.navigate(ConversationRoute(it)) },
                onOpenSettings = { nav.navigate(SettingsRoute) },
            )
        }
        composable<OnboardingRoute> {
            OnboardingScreen(
                onDone = {
                    nav.navigate(InboxRoute) { popUpTo(nav.graph.id) { inclusive = true } }
                },
            )
        }
        composable<SettingsRoute> {
            SettingsScreen(
                onBack = { nav.popBackStack() },
                onRunSetupAgain = { nav.navigate(OnboardingRoute) },
            )
        }
    }
}
