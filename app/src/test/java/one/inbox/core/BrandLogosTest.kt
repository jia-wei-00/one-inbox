package one.inbox.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class BrandLogosTest {
    @Test
    fun mapsAllDefaultsAndNothingElse() {
        ChatApps.DEFAULT.keys.forEach { assertNotNull(it, BrandLogos.forPackage(it)) }
        assertEquals(BrandLogos.forPackage("com.whatsapp"), BrandLogos.forPackage("com.whatsapp.w4b"))
        assertNull(BrandLogos.forPackage("com.example.other"))
    }
}
