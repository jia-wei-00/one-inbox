package one.inbox.core

object ChatApps {
    /** Package name to display name for the chat apps captured by default. */
    val DEFAULT: Map<String, String> = linkedMapOf(
        "com.whatsapp" to "WhatsApp",
        "com.whatsapp.w4b" to "WhatsApp Business",
        "com.facebook.orca" to "Messenger",
        "com.instagram.android" to "Instagram",
        "org.telegram.messenger" to "Telegram",
        "com.discord" to "Discord",
        "com.linkedin.android" to "LinkedIn",
        "com.twitter.android" to "X",
    )

    /** Package that `adb shell cmd notification post` notifications come from. */
    const val DEBUG_TEST_PACKAGE = "com.android.shell"

    private const val DEBUG_TEST_NAME = "ADB test"

    fun defaultAllowlist(debug: Boolean): Set<String> =
        if (debug) DEFAULT.keys + DEBUG_TEST_PACKAGE else DEFAULT.keys.toSet()

    fun displayName(packageName: String): String? =
        if (packageName == DEBUG_TEST_PACKAGE) DEBUG_TEST_NAME else DEFAULT[packageName]
}
