package one.inbox.screenshots

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.Density
import com.github.takahirom.roborazzi.captureRoboImage
import one.inbox.capture.CaptureHealth
import one.inbox.data.db.ConversationRow
import one.inbox.feature.inbox.InboxContent
import one.inbox.feature.inbox.InboxUiState
import one.inbox.feature.inbox.RailApp
import one.inbox.ui.theme.OneInboxTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h780dp-night-xxhdpi")
class InboxScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun inbox_railOpen() = capture("inbox_railOpen", baseState())

    @Test
    fun inbox_railHidden() {
        val base = baseState()
        capture(
            "inbox_railHidden",
            base.copy(
                sidebarOpen = false,
                selected = WHATSAPP,
                conversations = base.conversations.filter { it.packageName == WHATSAPP },
            ),
        )
    }

    @Test
    fun inbox_health() = capture("inbox_health", baseState().copy(health = CaptureHealth.NoAccess))

    @Test
    fun inbox_longCjkAndPreview() {
        val preview = "Dad: " + "We land at nine, can someone pick us up from the airport?"
        check(preview.length >= 60)
        val long = ConversationRow(
            key = "wa:long",
            packageName = WHATSAPP,
            title = "陈明的家庭群组（大家庭）",
            isGroup = true,
            lastMessageAt = today(15, 2),
            preview = preview.take(60),
            unreadCount = 128,
        )
        val longLatin = ConversationRow(
            key = "tg:long",
            packageName = TELEGRAM,
            title = "Neighbourhood residents association committee (Block C)",
            isGroup = true,
            lastMessageAt = today(14, 58),
            preview = "Priya: the lift on level 12 will be serviced on Monday morning, sorry",
            unreadCount = 4,
        )
        val base = baseState()
        capture("inbox_longCjkAndPreview", base.copy(conversations = listOf(long, longLatin) + base.conversations))
    }

    @Test
    fun inbox_manyApps() {
        val extra = listOf(
            RailApp("org.thoughtcrime.securesms", "Signal", 9),
            RailApp("jp.naver.line.android", "LINE", 0),
            RailApp("com.viber.voip", "Viber", 0),
            RailApp("com.tencent.mm", "WeChat", 120),
        )
        val rail = baseState().rail + extra
        capture(
            "inbox_manyApps",
            baseState().copy(
                rail = rail,
                selected = "com.viber.voip",
                totalUnread = rail.sumOf { it.unreadCount },
                appsWithUnread = rail.count { it.unreadCount > 0 },
            ),
        )
    }

    @Test
    fun inbox_empty() = capture("inbox_empty", InboxUiState(loaded = true))

    /** Cold start: nothing is known yet, so no fake empty inbox, no subtitle, no rail. */
    @Test
    fun inbox_loading() = capture("inbox_loading", InboxUiState())

    @Test
    fun noEmptyStateBeforeLoad() {
        compose.setContent { OneInboxTheme(darkTheme = true) { Content(InboxUiState()) } }
        compose.onNodeWithText("No messages yet").assertDoesNotExist()
        compose.onNodeWithText("All caught up").assertDoesNotExist()
    }

    @Test
    fun inbox_fontScale130() = capture("inbox_fontScale130", baseState(), fontScale = 1.3f)

    @Test
    fun filterChipReopensRail() {
        var toggles = 0
        compose.setContent {
            OneInboxTheme(darkTheme = true) {
                Content(baseState().copy(sidebarOpen = false, selected = WHATSAPP), onToggleSidebar = { toggles++ })
            }
        }
        compose.onNodeWithText("WhatsApp").performClick()
        assertEquals(1, toggles)
    }

    @Test
    fun menuButtonDescribesRailState() {
        val state = mutableStateOf(baseState())
        compose.setContent {
            OneInboxTheme(darkTheme = true) { Content(state.value) }
        }
        compose.onNodeWithContentDescription("Hide apps").assertExists()
        state.value = state.value.copy(sidebarOpen = false)
        compose.onNodeWithContentDescription("Show apps").assertExists()
        compose.onNodeWithText("All apps").assertExists()
    }

    @Test
    fun inboxTitleIsAHeading() {
        compose.setContent { OneInboxTheme(darkTheme = true) { Content(baseState()) } }
        compose.onNode(hasText("Inbox") and isHeading()).assertExists()
    }

    @Test
    fun fabOpensCatchUp() {
        var opened = 0
        compose.setContent {
            OneInboxTheme(darkTheme = true) { Content(baseState(), onCatchUp = { opened++ }) }
        }
        compose.onNodeWithContentDescription("Catch up, 7 unread").performClick()
        assertEquals(1, opened)
    }

    @Test
    fun fabCollapsesWhileScrollingDownAndExpandsOnScrollUp() {
        val base = baseState()
        val many = (1..30).map { i ->
            ConversationRow("k$i", WHATSAPP, "Chat $i", false, today(9, i), "Message $i", 0)
        }
        compose.setContent {
            OneInboxTheme(darkTheme = true) { Content(base.copy(conversations = many)) }
        }
        val fabText = "Catch up · 7"
        compose.onNodeWithText(fabText, useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("inbox-list").performTouchInput { swipeUp() }
        compose.onNodeWithText(fabText, useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithContentDescription("Catch up, 7 unread").assertExists()
        compose.onNodeWithTag("inbox-list").performTouchInput { swipeDown(startY = centerY, endY = centerY + 200f) }
        compose.onNodeWithText(fabText, useUnmergedTree = true).assertExists()
    }

    @Test
    fun fabStaysExpandedWhenShortListIsDragged() {
        val base = baseState()
        compose.setContent {
            OneInboxTheme(darkTheme = true) { Content(base.copy(conversations = base.conversations.take(3))) }
        }
        val fabText = "Catch up · 7"
        compose.onNodeWithTag("inbox-list").performTouchInput { swipeUp() }
        compose.onNodeWithText(fabText, useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("inbox-list").performTouchInput { swipeDown() }
        compose.onNodeWithTag("inbox-list").performTouchInput { swipeUp() }
        compose.onNodeWithText(fabText, useUnmergedTree = true).assertExists()
    }

    private fun capture(name: String, state: InboxUiState, fontScale: Float = 1f) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                OneInboxTheme(darkTheme = true) { Content(state) }
            }
        }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    @Composable
    private fun Content(
        state: InboxUiState,
        onToggleSidebar: () -> Unit = {},
        onCatchUp: () -> Unit = {},
    ) {
        InboxContent(
            state = state,
            icon = { icons[it] },
            label = { pkg -> state.rail.firstOrNull { it.packageName == pkg }?.label ?: pkg },
            onSelect = {},
            onToggleSidebar = onToggleSidebar,
            onOpenConversation = {},
            onCatchUp = onCatchUp,
            onSettings = {},
            onFixHealth = {},
            motionEnabled = false,
        )
    }

    private val icons: Map<String, ImageBitmap> = mapOf(
        WHATSAPP to fakeIcon(Color(0xFF25D366)),
        MESSENGER to fakeIcon(Color(0xFF0099FF)),
        INSTAGRAM to fakeIcon(Color(0xFFEE2A7B)),
        TELEGRAM to fakeIcon(Color(0xFF2AABEE)),
        "org.thoughtcrime.securesms" to fakeIcon(Color(0xFF3A76F0)),
        "com.tencent.mm" to fakeIcon(Color(0xFF07C160)),
        // Discord, LinkedIn, LINE and Viber have no icon: letter tiles and no avatar badge.
    )

    private fun baseState(): InboxUiState {
        val rail = listOf(
            RailApp(WHATSAPP, "WhatsApp", 4),
            RailApp(MESSENGER, "Messenger", 2),
            RailApp(INSTAGRAM, "Instagram", 0),
            RailApp(TELEGRAM, "Telegram", 1),
            RailApp(DISCORD, "Discord", 0),
            RailApp(LINKEDIN, "LinkedIn", 0),
        )
        val past = LocalDate.of(2026, 11, 20)
        val rows = listOf(
            ConversationRow("wa:mom", WHATSAPP, "Mom", false, today(14, 41), "Sunday dinner? Bring the cake 🎂", 3),
            ConversationRow("wa:fam", WHATSAPP, "Family group", true, today(13, 15), "Dad: Photos from the trip", 1),
            ConversationRow("ms:alex", MESSENGER, "Alex Tan", false, today(12, 2), "Free for a call this week?", 2),
            ConversationRow("tg:team", TELEGRAM, "Team launch", true, today(11, 30), "Sam: launch moved to Friday", 1),
            ConversationRow("ig:jason", INSTAGRAM, "Jason", false, at(past, 21, 4), "You: see you then 👋", 0),
            ConversationRow("dc:chen", DISCORD, "陈明", false, at(past, 18, 30), "明天见 😊", 0),
        )
        return InboxUiState(
            rail = rail,
            selected = null,
            conversations = rows,
            health = CaptureHealth.Ok,
            sidebarOpen = true,
            totalUnread = rail.sumOf { it.unreadCount },
            appsWithUnread = rail.count { it.unreadCount > 0 },
            loaded = true,
        )
    }

    private fun today(hour: Int, minute: Int): Long = at(LocalDate.now(), hour, minute)

    private fun at(date: LocalDate, hour: Int, minute: Int): Long =
        date.atTime(LocalTime.of(hour, minute)).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun fakeIcon(color: Color): ImageBitmap {
        val bitmap = ImageBitmap(96, 96)
        val canvas = Canvas(bitmap)
        canvas.drawRoundRect(0f, 0f, 96f, 96f, 24f, 24f, Paint().apply { this.color = color })
        canvas.drawCircle(Offset(48f, 48f), 22f, Paint().apply { this.color = Color.White })
        canvas.drawRoundRect(36f, 36f, 60f, 60f, 6f, 6f, Paint().apply { this.color = color })
        return bitmap
    }

    private companion object {
        const val WHATSAPP = "com.whatsapp"
        const val MESSENGER = "com.facebook.orca"
        const val INSTAGRAM = "com.instagram.android"
        const val TELEGRAM = "org.telegram.messenger"
        const val DISCORD = "com.discord"
        const val LINKEDIN = "com.linkedin.android"
    }
}
