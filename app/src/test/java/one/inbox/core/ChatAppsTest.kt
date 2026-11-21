package one.inbox.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatAppsTest {
    @Test fun defaultsCoverTheOwnersApps() {
        assertEquals(
            setOf("com.whatsapp", "com.whatsapp.w4b", "com.facebook.orca", "com.instagram.android",
                  "org.telegram.messenger", "com.discord", "com.linkedin.android", "com.twitter.android"),
            ChatApps.DEFAULT.keys)
        assertEquals("WhatsApp", ChatApps.displayName("com.whatsapp"))
        assertEquals("X", ChatApps.displayName("com.twitter.android"))
        assertNull(ChatApps.displayName("com.example.unknown"))
    }

    @Test fun debugAllowlistAddsShellForAdbTests() {
        assertTrue(ChatApps.DEBUG_TEST_PACKAGE in ChatApps.defaultAllowlist(debug = true))
        assertFalse(ChatApps.DEBUG_TEST_PACKAGE in ChatApps.defaultAllowlist(debug = false))
    }
}
