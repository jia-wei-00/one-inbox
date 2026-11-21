package one.inbox.core

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.math.roundToInt

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "xxhdpi")
class AppInfoProviderTest {
    private val context: Context = RuntimeEnvironment.getApplication()
    private val loads = mutableListOf<String>()

    private fun provider() = PackageAppInfoProvider(context) { pkg ->
        loads += pkg
        if (pkg == "com.whatsapp") BitmapDrawable(context.resources, Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)) else null
    }

    @Test
    fun iconsAreLoadedOncePerPackageAndMissingOnesAreCachedToo() {
        val provider = provider()
        val first = provider.icon("com.whatsapp")
        val second = provider.icon("com.whatsapp")
        assertNotNull(first)
        assertSame(first, second)
        assertNull(provider.icon("com.missing"))
        assertNull(provider.icon("com.missing"))
        assertEquals(listOf("com.whatsapp", "com.missing"), loads)
    }

    @Test
    fun iconsAreDecodedAt48dp() {
        val expected = (48 * context.resources.displayMetrics.density).roundToInt()
        val icon = provider().icon("com.whatsapp")!!
        assertEquals(expected, icon.width)
        assertEquals(expected, icon.height)
        // xxhdpi: 48dp is 144px, far below the 512px source.
        assertEquals(144, expected)
    }

    @Test
    fun theRealProviderIsAppScoped() {
        val annotated = PackageAppInfoProvider::class.java.isAnnotationPresent(javax.inject.Singleton::class.java)
        assertEquals(true, annotated)
    }
}
