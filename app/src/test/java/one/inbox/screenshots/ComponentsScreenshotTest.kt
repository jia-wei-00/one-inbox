package one.inbox.screenshots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import one.inbox.ui.components.AppTile
import one.inbox.ui.components.Avatar
import one.inbox.ui.components.avatarPairIndex
import one.inbox.ui.components.CountPill
import one.inbox.ui.components.SectionCard
import one.inbox.ui.theme.OneInboxTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h780dp-night-xxhdpi")
class ComponentsScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun components_gallery() {
        val green = fakeIcon(Color(0xFF25D366))
        val blue = fakeIcon(Color(0xFF2AABEE))
        val titles = listOf("Alex Tan", "陈明", "Mom", "Bella", "Dad", "😀 party")
        compose.setContent {
            OneInboxTheme(darkTheme = true) {
                Column(
                    Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Label("Avatars: 6 pairs, then with app badge, then 38dp / 32dp")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        titles.forEachIndexed { i, t -> Avatar(t, colorKeyFor(i), appIcon = null) }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        titles.forEachIndexed { i, t ->
                            Avatar(t, colorKeyFor(i), appIcon = if (i % 2 == 0) green else blue)
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Avatar("Lily", colorKeyFor(0), appIcon = green, size = 38.dp)
                        Avatar("Jason", colorKeyFor(2), appIcon = blue, size = 32.dp)
                        Avatar("Mei", colorKeyFor(4), appIcon = green, size = 32.dp)
                    }
                    Label("AppTiles: All, icon, icon selected + 3, letter selected, letter + 120")
                    Row(
                        Modifier.padding(start = 10.dp, top = 6.dp, bottom = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AppTile("All", icon = null, selected = false, unread = 0, onClick = {})
                        AppTile("WhatsApp", icon = green, selected = false, unread = 0, onClick = {})
                        AppTile("Telegram", icon = blue, selected = true, unread = 3, onClick = {})
                        AppTile("Signal", icon = null, selected = true, unread = 0, onClick = {})
                        AppTile("Line", icon = null, selected = false, unread = 120, onClick = {})
                    }
                    Label("CountPill: 1, 42, 120")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CountPill(1)
                        CountPill(42)
                        CountPill(120)
                    }
                    val cs = MaterialTheme.colorScheme
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DemoCard("⚠ Needs your reply", cs.tertiaryContainer, cs.onTertiaryContainer, Modifier.weight(1f))
                        DemoCard("💬 Worth knowing", cs.secondaryContainer, cs.onSecondaryContainer, Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DemoCard("Primary", cs.primaryContainer, cs.onPrimaryContainer, Modifier.weight(1f))
                        DemoCard("Error", cs.errorContainer, cs.onErrorContainer, Modifier.weight(1f))
                    }
                    SectionCard(
                        containerColor = cs.surfaceContainerHigh,
                        contentColor = cs.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth(),
                        title = "🔕 Low priority · 4",
                        trailing = { Icon(Icons.Filled.ExpandMore, contentDescription = "Expand") },
                    ) { Text("Collapsed card with a trailing chevron.") }
                }
            }
        }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/components_gallery.png")
    }

    @Test
    fun avatarWithoutAppIconHasNoBadge() {
        compose.setContent {
            OneInboxTheme(darkTheme = true) { Avatar("Alex Tan", "k", appIcon = null) }
        }
        compose.onNodeWithTag("avatar-badge").assertDoesNotExist()
    }

    @Test
    fun avatarWithAppIconHasBadge() {
        val icon = fakeIcon(Color(0xFF25D366))
        compose.setContent {
            OneInboxTheme(darkTheme = true) { Avatar("Alex Tan", "k", appIcon = icon) }
        }
        compose.onNodeWithTag("avatar-badge").assertExists()
    }

    @Composable
    private fun Label(text: String) {
        Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }

    @Composable
    private fun DemoCard(title: String, container: Color, content: Color, modifier: Modifier) {
        SectionCard(containerColor = container, contentColor = content, modifier = modifier, title = title) {
            Text("Body text on its paired role.", style = MaterialTheme.typography.bodyMedium)
        }
    }

    /** Finds a key whose avatarPairIndex is [index], so the gallery shows every pair in spec order. */
    private fun colorKeyFor(index: Int): String =
        generateSequence(0) { it + 1 }.map { "key-$it" }
            .first { avatarPairIndex(it) == index }

    private fun fakeIcon(color: Color): ImageBitmap {
        val bitmap = ImageBitmap(96, 96)
        val canvas = Canvas(bitmap)
        canvas.drawRoundRect(0f, 0f, 96f, 96f, 24f, 24f, Paint().apply { this.color = color })
        canvas.drawCircle(Offset(48f, 48f), 22f, Paint().apply { this.color = Color.White })
        canvas.drawRoundRect(36f, 36f, 60f, 60f, 6f, 6f, Paint().apply { this.color = color })
        return bitmap
    }
}
