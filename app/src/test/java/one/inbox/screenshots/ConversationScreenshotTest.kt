package one.inbox.screenshots

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import com.github.takahirom.roborazzi.captureRoboImage
import one.inbox.data.db.MessageEntity
import one.inbox.feature.conversation.ConversationContent
import one.inbox.feature.conversation.ConversationUiState
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
class ConversationScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun conversation_group() = capture("conversation_group", groupState(), waIcon)

    @Test
    fun conversation_direct() = capture("conversation_direct", directState(), null)

    @Test
    fun conversation_noOpenApp() = capture("conversation_noOpenApp", directState().copy(canOpenApp = false), null)

    @Test
    fun headerShowsPeopleForGroupsOnly() {
        val state = mutableStateOf(groupState())
        compose.setContent { OneInboxTheme(darkTheme = true) { content(state.value) } }
        compose.onNodeWithText("WhatsApp · 3 people").assertExists()
        state.value = directState()
        compose.onNodeWithText("Messenger").assertExists()
        compose.onNodeWithText("people", substring = true).assertDoesNotExist()
    }

    @Test
    fun dayChipsAreHeadings() {
        compose.setContent { OneInboxTheme(darkTheme = true) { content(groupState()) } }
        compose.onNode(hasText("Today") and isHeading()).assertExists()
    }

    @Test
    fun backAndOpenAppCallBack() {
        var back = 0
        var open = 0
        compose.setContent {
            OneInboxTheme(darkTheme = true) { content(groupState(), onBack = { back++ }, onOpenApp = { open++ }) }
        }
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Open WhatsApp ↗").performClick()
        assertEquals(1, back)
        assertEquals(1, open)
    }

    @Test
    fun noOpenButtonWhenAppCannotBeLaunched() {
        compose.setContent { OneInboxTheme(darkTheme = true) { content(groupState().copy(canOpenApp = false)) } }
        compose.onNodeWithText("Open", substring = true).assertDoesNotExist()
    }

    @Test
    fun opensAtTheNewestMessage() {
        compose.setContent { OneInboxTheme(darkTheme = true) { content(longState(40)) } }
        compose.onNodeWithText("Message 40").assertIsDisplayed()
        compose.onNodeWithText("Message 1").assertIsNotDisplayed()
    }

    @Test
    fun followsNewMessagesWhenAtTheBottom() {
        val state = mutableStateOf(longState(40))
        compose.setContent { OneInboxTheme(darkTheme = true) { content(state.value) } }
        state.value = longState(41)
        compose.waitForIdle()
        compose.onNodeWithText("Message 41").assertIsDisplayed()
    }

    @Test
    fun staysPutOnNewMessagesWhenScrolledUp() {
        val state = mutableStateOf(longState(40))
        compose.setContent { OneInboxTheme(darkTheme = true) { content(state.value) } }
        compose.onNodeWithTag("conversation-list").performTouchInput { swipeDown() }
        compose.waitForIdle()
        compose.onNodeWithText("Message 40").assertIsNotDisplayed()
        state.value = longState(41)
        compose.waitForIdle()
        compose.onNodeWithText("Message 41").assertIsNotDisplayed()
    }

    private fun capture(name: String, state: ConversationUiState, icon: ImageBitmap?) {
        compose.setContent { OneInboxTheme(darkTheme = true) { content(state, icon) } }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    @androidx.compose.runtime.Composable
    private fun content(
        state: ConversationUiState,
        icon: ImageBitmap? = null,
        onBack: () -> Unit = {},
        onOpenApp: () -> Unit = {},
    ) = ConversationContent(
        state = state,
        appIcon = icon,
        onBack = onBack,
        onOpenApp = onOpenApp,
        motionEnabled = false,
        today = TODAY,
    )

    private var nextId = 1L

    private fun msg(sender: String, text: String, at: Long, me: Boolean = false, key: String = "wa:fam") =
        MessageEntity(
            id = nextId,
            dedupeKey = "d${nextId++}",
            conversationKey = key,
            packageName = WHATSAPP,
            sender = sender,
            isFromMe = me,
            text = text,
            timestamp = at,
            unread = false,
        )

    private fun groupState(): ConversationUiState {
        val yesterday = TODAY.minusDays(1)
        val today = TODAY
        val messages = listOf(
            msg("Dad", "Landed safely ✈️ The hotel is lovely, will send photos tomorrow", at(yesterday, 21, 2)),
            msg("Lily", "陈明说明天见 😊", at(yesterday, 21, 10)),
            msg("You", "Have a great trip!", at(yesterday, 21, 15), me = true),
            msg("Mom", "Sunday dinner at 7? Bring the cake 🎂", at(today, 14, 38)),
            msg("Mom", "The chocolate one from the bakery near the station", at(today, 14, 39)),
            msg("Dad", "Photos from the trip, check them out", at(today, 14, 40)),
            msg("You", "I'll be there! 🙌", at(today, 14, 41), me = true),
            msg("You", "Should I bring anything else?", at(today, 14, 41), me = true),
            msg("Mom", "Great, don't be late this time 😄", at(today, 14, 44)),
            msg("Lily", "明天见 😊", at(today, 14, 45)),
            msg("Lily", "我也带一些水果过来，大家喜欢草莓吗？🍓", at(today, 14, 46)),
        )
        return ConversationUiState(
            appName = "WhatsApp",
            title = "Family group",
            isGroup = true,
            messages = messages,
            packageName = WHATSAPP,
            peopleCount = 3,
            canOpenApp = true,
            conversationKey = "wa:fam",
        )
    }

    private fun directState(): ConversationUiState {
        val today = TODAY
        val key = "ms:alex"
        val messages = listOf(
            msg("Alex Tan", "Hey! Free for a call this week?", at(today.minusDays(3), 18, 20), key = key),
            msg("You", "Sure, Thursday evening works", at(today.minusDays(3), 18, 32), me = true, key = key),
            msg("Alex Tan", "Perfect 👍", at(today.minusDays(3), 18, 33), key = key),
            msg("Alex Tan", "Still on for tonight?", at(today, 17, 5), key = key),
            msg(
                "Alex Tan",
                "I booked the place near the river, the one with the long tables and the good dumplings. " +
                    "Table is under my name at 8.",
                at(today, 17, 6),
                key = key,
            ),
            msg("You", "Yes! See you there", at(today, 17, 30), me = true, key = key),
        )
        return ConversationUiState(
            appName = "Messenger",
            title = "Alex Tan",
            isGroup = false,
            messages = messages,
            packageName = "com.facebook.orca",
            peopleCount = 0,
            canOpenApp = true,
            conversationKey = key,
        )
    }

    private fun longState(count: Int): ConversationUiState {
        val start = at(TODAY, 8, 0)
        val messages = (1..count).map { i ->
            MessageEntity(i.toLong(), "d$i", "wa:long", WHATSAPP, "Sam", i % 3 == 0, "Message $i", start + i * 600_000L, false)
        }
        return ConversationUiState(
            appName = "WhatsApp",
            title = "Sam",
            messages = messages,
            packageName = WHATSAPP,
            canOpenApp = true,
            conversationKey = "wa:long",
        )
    }

    private fun at(date: LocalDate, hour: Int, minute: Int): Long =
        date.atTime(LocalTime.of(hour, minute)).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private val waIcon: ImageBitmap by lazy {
        val bitmap = ImageBitmap(96, 96)
        val canvas = Canvas(bitmap)
        val green = Color(0xFF25D366)
        canvas.drawRoundRect(0f, 0f, 96f, 96f, 24f, 24f, Paint().apply { color = green })
        canvas.drawCircle(Offset(48f, 48f), 22f, Paint().apply { color = Color.White })
        canvas.drawRoundRect(36f, 36f, 60f, 60f, 6f, 6f, Paint().apply { color = green })
        bitmap
    }

    private companion object {
        const val WHATSAPP = "com.whatsapp"

        /** Pinned, so the day chips ("Today", "Yesterday", "Nov 19") never drift with the clock. */
        val TODAY: LocalDate = LocalDate.of(2026, 11, 22)
    }
}
