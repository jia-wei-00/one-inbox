package one.inbox.screenshots

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.github.takahirom.roborazzi.captureRoboImage
import one.inbox.feature.onboarding.OnboardingContent
import one.inbox.feature.onboarding.OnboardingStep
import one.inbox.feature.onboarding.OnboardingSteps
import one.inbox.feature.settings.AppToggle
import one.inbox.feature.settings.MSG_KEY_SAVED
import one.inbox.feature.settings.MSG_KEY_SAVE_FAILED
import one.inbox.feature.settings.SettingsContent
import one.inbox.feature.settings.SettingsUiState
import one.inbox.ui.theme.OneInboxTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h780dp-night-xxhdpi")
class SetupScreensScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    // --- Onboarding ---------------------------------------------------------------------------

    @Test
    fun onboarding_fresh() = captureOnboarding("onboarding_fresh", FRESH)

    @Test
    fun onboarding_partial() = captureOnboarding("onboarding_partial", FRESH.copy(access = true, batteryOk = true))

    /** Light scheme: the "Done ✓" green and the tonal "Open" use their light counterparts. */
    @Test
    fun onboarding_partialLight() = captureOnboarding(
        "onboarding_partialLight",
        FRESH.copy(access = true, batteryOk = true),
        dark = false,
    )

    @Test
    fun onboardingHeroIsAHeading() {
        compose.setContent { onboarding(FRESH) }
        compose.onNode(hasText("Set up One Inbox") and isHeading()).assertExists()
    }

    @Test
    fun settingsTitleIsAHeading() {
        compose.setContent { settings(SettingsUiState(apps = SIX_APPS)) }
        compose.onNode(hasText("Settings") and isHeading()).assertExists()
    }

    @Test
    fun doneIsEnabledOnlyWithAccess() {
        var steps by mutableStateOf(FRESH)
        compose.setContent { onboarding(steps) }
        compose.onNodeWithText("Done").assertIsNotEnabled()
        steps = FRESH.copy(access = true)
        compose.onNodeWithText("Done").assertIsEnabled()
    }

    @Test
    fun doneCallsBack() {
        var done = 0
        compose.setContent { onboarding(FRESH.copy(access = true), onDone = { done++ }) }
        compose.onNodeWithText("Done").performClick()
        assertEquals(1, done)
    }

    @Test
    fun heroCopy() {
        compose.setContent { onboarding(FRESH) }
        compose.onNodeWithText("Set up One Inbox").assertExists()
        compose.onNodeWithText("4 quick steps so no message slips through").assertExists()
    }

    @Test
    fun openButtonsOpenTheirStep() {
        val opened = mutableListOf<OnboardingStep>()
        compose.setContent { onboarding(FRESH, onOpen = { opened += it }) }
        compose.onNodeWithContentDescription("Open Notification access").performClick()
        compose.onNodeWithContentDescription("Open Battery: No restrictions").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Open Autostart").performScrollTo().performClick()
        assertEquals(listOf(OnboardingStep.Access, OnboardingStep.Battery, OnboardingStep.Autostart), opened)
    }

    @Test
    fun checkboxesToggleTheirStep() {
        val toggled = mutableListOf<Pair<OnboardingStep, Boolean>>()
        compose.setContent { onboarding(FRESH.copy(autostart = true), onToggle = { s, v -> toggled += s to v }) }
        compose.onNodeWithContentDescription("Battery: No restrictions done").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Autostart done").performScrollTo().assertIsOn().performClick()
        compose.onNodeWithContentDescription("Lock One Inbox in Recents (optional) done").performScrollTo()
            .assertIsOff().performClick()
        assertEquals(
            listOf(
                OnboardingStep.Battery to true,
                OnboardingStep.Autostart to false,
                OnboardingStep.Recents to true,
            ),
            toggled,
        )
    }

    @Test
    fun statusLinesShowDoneOrHint() {
        compose.setContent { onboarding(FRESH.copy(access = true, batteryConfirmed = true)) }
        compose.onAllNodesWithText("Done ✓").assertCountEquals(2)
        compose.onNodeWithText("Enable it, then tick the box").assertExists()
    }

    @Test
    fun restrictedSettingsHintShownUntilAccessGranted() {
        var steps by mutableStateOf(FRESH)
        compose.setContent { onboarding(steps) }
        compose.onNodeWithText(RESTRICTED_HINT).assertExists()
        steps = FRESH.copy(access = true)
        compose.onNodeWithText(RESTRICTED_HINT).assertDoesNotExist()
    }

    // --- Settings -----------------------------------------------------------------------------

    @Test
    fun settings_withKey() = captureSettings("settings_withKey", SettingsUiState(hasKey = true, apps = SIX_APPS))

    @Test
    fun settings_noKey_error() = captureSettings(
        "settings_noKey_error",
        SettingsUiState(hasKey = false, keyMessage = MSG_KEY_SAVE_FAILED, apps = SIX_APPS),
    )

    @Test
    fun savedKeyStatusAndNote() {
        compose.setContent { settings(SettingsUiState(hasKey = true, apps = SIX_APPS)) }
        compose.onNodeWithText(MSG_KEY_SAVED).assertExists()
        compose.onNodeWithText("Only sent to Claude when you tap Summarize").assertExists()
        compose.onNodeWithText("Clear").assertIsEnabled()
        compose.onNodeWithText("Save").assertIsNotEnabled()
    }

    @Test
    fun saveSendsTheDraftAndClearsItOnlyOnSuccess() {
        val saves = mutableListOf<Pair<String, () -> Unit>>()
        compose.setContent { settings(SettingsUiState(), onSaveKey = { raw, onSaved -> saves += raw to onSaved }) }
        compose.onNodeWithText("API key").performTextInput("sk-ant-secret")
        compose.onNodeWithText("Save").performClick()
        assertEquals(listOf("sk-ant-secret"), saves.map { it.first })
        // Not saved yet: the draft stays (masked) and Save stays enabled.
        compose.onNodeWithText("Save").assertIsEnabled()
        compose.runOnIdle { saves.single().second() }
        compose.onNodeWithText("Save").assertIsNotEnabled()
    }

    @Test
    fun keyIsMasked() {
        compose.setContent { settings(SettingsUiState()) }
        compose.onNodeWithText("API key").performTextInput("sk-ant-secret")
        // What is drawn and announced (EditableText) is masked; the raw value only lives in the field's input.
        val field = compose.onNode(hasSetTextAction()).fetchSemanticsNode().config
        assertTrue(SemanticsProperties.Password in field)
        assertEquals(13, field[SemanticsProperties.EditableText].text.length)
        assertFalse(field[SemanticsProperties.EditableText].text.contains("sk-ant"))
    }

    @Test
    fun clearCallsBack() {
        var cleared = 0
        compose.setContent { settings(SettingsUiState(hasKey = true), onClearKey = { cleared++ }) }
        compose.onNodeWithText("Clear").performClick()
        assertEquals(1, cleared)
    }

    @Test
    fun appRowTogglesCapture() {
        val toggled = mutableListOf<Pair<String, Boolean>>()
        compose.setContent { settings(SettingsUiState(apps = SIX_APPS), onSetAllowed = { p, v -> toggled += p to v }) }
        compose.onNodeWithText("WhatsApp").performScrollTo().performClick()
        compose.onNodeWithText("Signal").performScrollTo().performClick()
        assertEquals(listOf(WHATSAPP to false, "org.thoughtcrime.securesms" to true), toggled)
    }

    @Test
    fun backAndRunSetupCallBack() {
        var back = 0
        var setup = 0
        compose.setContent { settings(SettingsUiState(apps = SIX_APPS), onBack = { back++ }, onRunSetup = { setup++ }) }
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Run setup again"))
        compose.onNodeWithText("Run setup again").performClick()
        assertEquals(listOf(1, 1), listOf(back, setup))
    }

    // --- Cleanup --------------------------------------------------------------------------------

    @Test
    fun settings_cleanup() {
        compose.setContent { settings(SettingsUiState(hasKey = true, apps = SIX_APPS, autoDeleteDays = 30, readChats = 4)) }
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Run setup again"))
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/settings_cleanup.png")
    }

    @Test
    fun autoDeleteChoicesShowTheCurrentOneAndCallBackWithDays() {
        val chosen = mutableListOf<Int>()
        compose.setContent { settings(SettingsUiState(autoDeleteDays = 0), onSetAutoDeleteDays = { chosen += it }) }
        compose.onNodeWithText("Off").performScrollTo().assertIsSelected()
        compose.onNodeWithText("30 days").assertIsNotSelected().performClick()
        compose.onNodeWithText("90 days").performClick()
        assertEquals(listOf(30, 90), chosen)
    }

    @Test
    fun clearReadChatsAsksFirst() {
        var cleared = 0
        compose.setContent { settings(SettingsUiState(readChats = 2), onClearReadChats = { cleared++ }) }
        compose.onNodeWithText("Clear read chats (2)").performScrollTo().performClick()
        compose.onNodeWithText("Delete 2 read chats?").assertExists()
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithText("Delete 2 read chats?").assertDoesNotExist()
        assertEquals(0, cleared)
        compose.onNodeWithText("Clear read chats (2)").performClick()
        compose.onNodeWithText("Delete").performClick()
        assertEquals(1, cleared)
    }

    @Test
    fun clearReadChatsIsDisabledWhenThereAreNone() {
        compose.setContent { settings(SettingsUiState(readChats = 0, cleanupMessage = "Deleted 3 chats")) }
        compose.onNodeWithText("Clear read chats (0)").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Deleted 3 chats").assertExists()
    }

    // --- Helpers ------------------------------------------------------------------------------

    private fun captureOnboarding(name: String, steps: OnboardingSteps, dark: Boolean = true) {
        compose.setContent { onboarding(steps, dark = dark) }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    private fun captureSettings(name: String, state: SettingsUiState) {
        compose.setContent { settings(state) }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    @Composable
    private fun onboarding(
        steps: OnboardingSteps,
        onOpen: (OnboardingStep) -> Unit = {},
        onToggle: (OnboardingStep, Boolean) -> Unit = { _, _ -> },
        onDone: () -> Unit = {},
        dark: Boolean = true,
    ) = OneInboxTheme(darkTheme = dark) {
        OnboardingContent(steps = steps, onOpen = onOpen, onToggle = onToggle, onDone = onDone)
    }

    @Composable
    private fun settings(
        state: SettingsUiState,
        onBack: () -> Unit = {},
        onSaveKey: (String, () -> Unit) -> Unit = { _, _ -> },
        onClearKey: () -> Unit = {},
        onSetAllowed: (String, Boolean) -> Unit = { _, _ -> },
        onRunSetup: () -> Unit = {},
        onSetAutoDeleteDays: (Int) -> Unit = {},
        onClearReadChats: () -> Unit = {},
    ) = OneInboxTheme(darkTheme = true) {
        SettingsContent(
            state = state,
            icon = ::iconFor,
            onBack = onBack,
            onSaveKey = onSaveKey,
            onClearKey = onClearKey,
            onSetAllowed = onSetAllowed,
            onRunSetup = onRunSetup,
            onSetAutoDeleteDays = onSetAutoDeleteDays,
            onClearReadChats = onClearReadChats,
        )
    }

    private fun iconFor(pkg: String): ImageBitmap? = when (pkg) {
        WHATSAPP -> appIcon(Color(0xFF25D366))
        TELEGRAM -> appIcon(Color(0xFF2AABEE))
        MESSENGER -> appIcon(Color(0xFF7B61FF))
        LINE -> appIcon(Color(0xFF06C755))
        else -> null // Signal and WeChat fall back to the letter tile
    }

    private fun appIcon(color: Color): ImageBitmap {
        val bitmap = ImageBitmap(96, 96)
        val canvas = Canvas(bitmap)
        canvas.drawRoundRect(0f, 0f, 96f, 96f, 24f, 24f, Paint().apply { this.color = color })
        canvas.drawCircle(Offset(48f, 48f), 22f, Paint().apply { this.color = Color.White })
        return bitmap
    }

    private companion object {
        val FRESH = OnboardingSteps(
            access = false,
            batteryOk = false,
            batteryConfirmed = false,
            autostart = false,
            recents = false,
        )
        const val RESTRICTED_HINT = "Greyed out? Open App info → ⋮ → Allow restricted settings, then try again."
        const val WHATSAPP = "com.whatsapp"
        const val TELEGRAM = "org.telegram.messenger"
        const val MESSENGER = "com.facebook.orca"
        const val LINE = "jp.naver.line.android"
        val SIX_APPS = listOf(
            AppToggle(LINE, "LINE", true),
            AppToggle(MESSENGER, "Messenger", true),
            AppToggle("org.thoughtcrime.securesms", "Signal", false),
            AppToggle(TELEGRAM, "Telegram", true),
            AppToggle("com.tencent.mm", "WeChat", false),
            AppToggle(WHATSAPP, "WhatsApp", true),
        )
    }
}
