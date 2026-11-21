package one.inbox.ui

import androidx.compose.animation.core.SnapSpec
import androidx.compose.animation.core.SpringSpec
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import one.inbox.ui.components.avatarPairIndex
import one.inbox.ui.components.initialsOf
import one.inbox.ui.components.senderColorIndex
import one.inbox.ui.theme.AvatarPairs
import one.inbox.ui.theme.DoneGreenDark
import one.inbox.ui.theme.DoneGreenLight
import one.inbox.ui.theme.ExpressiveMotion
import one.inbox.ui.theme.SenderColors
import one.inbox.ui.theme.VioletNightDark
import one.inbox.ui.theme.VioletNightLight
import one.inbox.ui.theme.doneGreen
import one.inbox.ui.theme.isDark
import one.inbox.ui.theme.senderColor
import one.inbox.ui.theme.motionEnabled
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class DesignSystemTest {

    @Test
    fun avatarPairIndexIsStableAndInRange() {
        assertEquals(avatarPairIndex("com.whatsapp|Alex Tan"), avatarPairIndex("com.whatsapp|Alex Tan"))
        val random = Random(42)
        repeat(200) {
            val key = buildString { repeat(random.nextInt(0, 24)) { append(Char(random.nextInt(0x20, 0x7FF))) } }
            val index = avatarPairIndex(key)
            assertTrue("index $index for '$key' out of range", index in 0..5)
            assertEquals(index, avatarPairIndex(key))
            assertTrue(senderColorIndex(key) in 0..5)
        }
        assertEquals(6, AvatarPairs.size)
        assertEquals(6, SenderColors.size)
    }

    @Test
    fun initialsOfHandlesLatinCjkEmojiAndBlank() {
        assertEquals("A", initialsOf("Alex Tan"))
        assertEquals("A", initialsOf("  alex"))
        assertEquals("陈", initialsOf("陈明"))
        assertEquals("😀", initialsOf("😀 party"))
        assertEquals("?", initialsOf("  "))
        assertEquals("?", initialsOf(""))
    }

    @Test
    fun motionEnabledOnlyFalseAtZero() {
        assertFalse(motionEnabled(0f))
        assertTrue(motionEnabled(0.5f))
        assertTrue(motionEnabled(1f))
    }

    @Test
    fun expressiveMotionSnapsWhenDisabled() {
        assertTrue(ExpressiveMotion.defaultSpatialSpec<Float>(enabled = false) is SnapSpec)
        val spring = ExpressiveMotion.defaultSpatialSpec<Float>() as SpringSpec
        assertEquals(0.8f, spring.dampingRatio)
        assertEquals(380f, spring.stiffness)
        assertTrue(ExpressiveMotion.fastEffectsSpec<Float>(enabled = false) is SnapSpec)
    }

    @Test
    fun darkSchemeMatchesSpecTokens() {
        assertEquals(Color(0xFFCDB8FF), VioletNightDark.primary)
        assertEquals(Color(0xFF4B2F92), VioletNightDark.primaryContainer)
        assertEquals(Color(0xFF6D2A1A), VioletNightDark.tertiaryContainer)
        assertEquals(Color(0xFF1F4A6B), VioletNightDark.secondaryContainer)
        assertEquals(Color(0xFF141218), VioletNightDark.background)
        assertEquals(Color(0xFF1D1B22), VioletNightDark.surface)
        assertEquals(Color(0xFF26232D), VioletNightDark.surfaceContainerHigh)
        assertEquals(Color(0xFF2F2B38), VioletNightDark.surfaceContainerHighest)
        assertEquals(Color(0xFFE8E0F0), VioletNightDark.onSurface)
        assertEquals(Color(0xFFB3AAC2), VioletNightDark.onSurfaceVariant)
        assertEquals(Color(0xFF331A6B), VioletNightDark.onPrimary)
        assertEquals(Color(0xFFEBDDFF), VioletNightDark.onPrimaryContainer)
        assertEquals(Color(0xFFFFB4A0), VioletNightDark.tertiary)
        assertEquals(Color(0xFFFFDBD1), VioletNightDark.onTertiaryContainer)
        assertEquals(Color(0xFFA8D3FF), VioletNightDark.secondary)
        assertEquals(Color(0xFFD1E6FF), VioletNightDark.onSecondaryContainer)
        assertEquals(Color(0xFF948F99), VioletNightDark.outline)
        assertEquals(Color(0xFFFFB4AB), VioletNightDark.error)
        assertEquals(Color(0xFF93000A), VioletNightDark.errorContainer)
        assertEquals(Color(0xFFFFDAD6), VioletNightDark.onErrorContainer)
    }

    private fun contrast(a: Color, b: Color): Float {
        val la = a.luminance() + 0.05f
        val lb = b.luminance() + 0.05f
        return maxOf(la, lb) / minOf(la, lb)
    }

    @Test
    fun doneGreenAndSenderColorsFollowTheScheme() {
        assertTrue(VioletNightDark.isDark)
        assertFalse(VioletNightLight.isDark)
        assertEquals(DoneGreenDark, VioletNightDark.doneGreen)
        assertEquals(DoneGreenLight, VioletNightLight.doneGreen)
        for (scheme in listOf(VioletNightDark, VioletNightLight)) {
            val green = scheme.doneGreen
            assertTrue("done green on ${scheme.surfaceContainerHigh}", contrast(green, scheme.surfaceContainerHigh) >= 4.5f)
            // Sender names sit on incoming bubbles (surfaceContainerHigh).
            repeat(SenderColors.size) { i ->
                val c = scheme.senderColor(i)
                assertTrue("sender $i: ${contrast(c, scheme.surfaceContainerHigh)}", contrast(c, scheme.surfaceContainerHigh) >= 4.5f)
            }
        }
        assertEquals(SenderColors[0], VioletNightDark.senderColor(0))
        assertEquals(AvatarPairs[0].second, VioletNightLight.senderColor(0))
    }
}
