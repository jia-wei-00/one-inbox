package one.inbox.screenshots

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import com.github.takahirom.roborazzi.captureRoboImage
import one.inbox.feature.catchup.CatchUpContent
import one.inbox.feature.catchup.CatchUpUiState
import one.inbox.feature.catchup.ItemLabel
import one.inbox.feature.catchup.MSG_NO_API_KEY
import one.inbox.summary.CatchUp
import one.inbox.summary.CatchUpItem
import one.inbox.summary.StoredCatchUp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h780dp-night-xxhdpi")
class CatchUpScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun catchup_full() = capture("catchup_full", fullState())

    @Test
    fun catchup_lowExpanded() = capture("catchup_lowExpanded", fullState()) {
        compose.onNodeWithContentDescription("Expand").performClick()
    }

    @Test
    fun catchup_firstRun() = capture("catchup_firstRun", CatchUpUiState())

    // The test harness pauses infinite animations, so the spinner is captured at its first frame.
    @Test
    fun catchup_running() = capture("catchup_running", fullState().copy(running = true))

    @Test
    fun catchup_noKey() = capture("catchup_noKey", CatchUpUiState(message = MSG_NO_API_KEY))

    @Test
    fun catchup_failed() = capture("catchup_failed", fullState().copy(message = "No connection"))

    @Test
    fun catchup_fontScale130() = capture("catchup_fontScale130", fullState(), fontScale = 1.3f)

    @Test
    fun lowPriorityTogglesOnTap() {
        compose.setContent { content(fullState()) }
        compose.onNodeWithText(LOW_SUMMARY_1, substring = true).assertDoesNotExist()
        compose.onNodeWithContentDescription("Expand").performClick()
        compose.onNodeWithText(LOW_SUMMARY_1, substring = true).assertExists()
        compose.onNodeWithContentDescription("Collapse").performClick()
        compose.onNodeWithText(LOW_SUMMARY_1, substring = true).assertDoesNotExist()
    }

    @Test
    fun itemTapOpensTheConversation() {
        val opened = mutableListOf<String>()
        compose.setContent { content(fullState(), onOpenConversation = { opened += it }) }
        compose.onNodeWithText("wants to set a call time", substring = true).performClick()
        assertEquals(listOf(ALEX), opened)
    }

    @Test
    fun actionsCallBack() {
        var back = 0
        var summarize = 0
        var markRead = 0
        compose.setContent {
            content(
                fullState(),
                onBack = { back++ },
                onSummarize = { summarize++ },
                onMarkAllRead = { markRead++ },
            )
        }
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Summarize again").performClick()
        compose.onNodeWithText("Mark all read").performClick()
        assertEquals(listOf(1, 1, 1), listOf(back, summarize, markRead))
    }

    @Test
    fun firstRunHasOnlySummarize() {
        compose.setContent { content(CatchUpUiState()) }
        compose.onNodeWithText("Summarize").assertExists()
        compose.onNodeWithText("Mark all read").assertDoesNotExist()
        compose.onNodeWithText("Summarize your unread messages").assertExists()
    }

    @Test
    fun runningDisablesSummarize() {
        compose.setContent { content(fullState().copy(running = true)) }
        compose.onNodeWithText("Summarizing…").assertIsNotEnabled()
    }

    @Test
    fun noKeyOffersSettings() {
        var settings = 0
        compose.setContent { content(CatchUpUiState(message = MSG_NO_API_KEY), onOpenSettings = { settings++ }) }
        compose.onNodeWithText(MSG_NO_API_KEY).assertExists()
        compose.onNodeWithText("Open Settings").performClick()
        assertEquals(1, settings)
    }

    @Test
    fun emptyBucketsAreNotShown() {
        val latest = fullState().latest!!
        val state = fullState().copy(latest = latest.copy(catchUp = latest.catchUp.copy(worthKnowing = emptyList())))
        compose.setContent { content(state) }
        compose.onNodeWithText("💬 Worth knowing").assertDoesNotExist()
        compose.onNodeWithText("⚠ Needs your reply").assertExists()
    }

    private fun capture(name: String, state: CatchUpUiState, fontScale: Float = 1f, before: () -> Unit = {}) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) { content(state) }
        }
        before()
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    @Composable
    private fun content(
        state: CatchUpUiState,
        onBack: () -> Unit = {},
        onSummarize: () -> Unit = {},
        onMarkAllRead: () -> Unit = {},
        onOpenConversation: (String) -> Unit = {},
        onOpenSettings: () -> Unit = {},
    ) = one.inbox.ui.theme.OneInboxTheme(darkTheme = true) {
        CatchUpContent(
            state = state,
            icon = ::iconFor,
            onBack = onBack,
            onSummarize = onSummarize,
            onMarkAllRead = onMarkAllRead,
            onOpenConversation = onOpenConversation,
            onOpenSettings = onOpenSettings,
            motionEnabled = false,
            now = NOW,
        )
    }

    private fun fullState(): CatchUpUiState {
        val low = (1..31).map { i ->
            CatchUpItem(key(if (i % 2 == 0) TELEGRAM else WHATSAPP, "Group $i"), if (i == 1) LOW_SUMMARY_1 else "Chatter about plans $i.")
        }
        val catchUp = CatchUp(
            needsReply = listOf(
                CatchUpItem(MOM, "is asking if you're coming to Sunday dinner, and to bring the cake."),
                CatchUpItem(ALEX, "wants to set a call time this week."),
            ),
            worthKnowing = listOf(
                CatchUpItem(TEAM, "Launch moved to Friday; the demo is still on Thursday."),
                CatchUpItem(FAMILY, "Dad shared trip photos."),
            ),
            lowPriority = low,
        )
        val labels = buildMap {
            put(MOM, ItemLabel("Mom", "WhatsApp"))
            put(ALEX, ItemLabel("Alex Tan", "Messenger"))
            put(TEAM, ItemLabel("Team launch", "Telegram"))
            put(FAMILY, ItemLabel(null, "WhatsApp")) // conversation row gone: falls back to the app name
            low.forEachIndexed { i, item -> put(item.conversationKey, ItemLabel("Group ${i + 1}", "WhatsApp")) }
        }
        return CatchUpUiState(
            latest = StoredCatchUp(catchUp, NOW - 5 * 60_000, emptyList(), unreadCount = 62, appCount = 6, droppedCount = 0),
            newSince = 3,
            labels = labels,
        )
    }

    private fun iconFor(conversationKey: String): ImageBitmap? = when (conversationKey.split("|")[1]) {
        WHATSAPP -> waIcon
        TELEGRAM -> tgIcon
        else -> null
    }

    private fun appIcon(color: Color): ImageBitmap {
        val bitmap = ImageBitmap(96, 96)
        val canvas = Canvas(bitmap)
        canvas.drawRoundRect(0f, 0f, 96f, 96f, 24f, 24f, Paint().apply { this.color = color })
        canvas.drawCircle(Offset(48f, 48f), 22f, Paint().apply { this.color = Color.White })
        return bitmap
    }

    private val waIcon by lazy { appIcon(Color(0xFF25D366)) }
    private val tgIcon by lazy { appIcon(Color(0xFF2AABEE)) }

    private companion object {
        const val NOW = 1_795_000_000_000L
        const val WHATSAPP = "com.whatsapp"
        const val TELEGRAM = "org.telegram.messenger"
        const val LOW_SUMMARY_1 = "Shared a meme in the group."
        fun key(pkg: String, rest: String) = "0|$pkg|$rest"
        val MOM = key(WHATSAPP, "Mom")
        val ALEX = key("com.facebook.orca", "Alex Tan")
        val TEAM = key(TELEGRAM, "Team launch")
        val FAMILY = key(WHATSAPP, "Family group")
    }
}
