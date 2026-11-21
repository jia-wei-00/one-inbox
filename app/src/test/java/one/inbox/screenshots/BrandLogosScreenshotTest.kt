package one.inbox.screenshots

import android.content.Context
import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import one.inbox.core.BrandLogos
import one.inbox.ui.components.AppTile
import one.inbox.ui.components.Avatar
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
class BrandLogosScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private val packages = listOf(
        "WhatsApp" to "com.whatsapp",
        "Messenger" to "com.facebook.orca",
        "Instagram" to "com.instagram.android",
        "Telegram" to "org.telegram.messenger",
        "Discord" to "com.discord",
        "LinkedIn" to "com.linkedin.android",
        "X" to "com.twitter.android",
    )

    private fun logo(pkg: String): ImageBitmap {
        val context = ApplicationProvider.getApplicationContext<Context>()
        @DrawableRes val id = BrandLogos.forPackage(pkg)!!
        val px = (48 * context.resources.displayMetrics.density).toInt()
        return ContextCompat.getDrawable(context, id)!!.toBitmap(px, px).asImageBitmap()
    }

    @Test
    fun brandLogos_gallery() {
        compose.setContent {
            OneInboxTheme(darkTheme = true) {
                Column(
                    Modifier.background(MaterialTheme.colorScheme.background).padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        packages.take(4).forEach { (name, pkg) -> AppTile(name, logo(pkg), false, 0, null) }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        packages.drop(4).forEach { (name, pkg) -> AppTile(name, logo(pkg), false, 0, null) }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        packages.forEachIndexed { i, (name, pkg) -> Avatar(name, "k$i", appIcon = logo(pkg), size = 38.dp) }
                    }
                }
            }
        }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/brandLogos_gallery.png")
    }
}
