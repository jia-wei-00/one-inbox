package one.inbox.core

import androidx.annotation.DrawableRes
import one.inbox.R

/** Bundled logos for the default chat platforms, shown even when the app isn't installed. */
object BrandLogos {
    private val byPackage: Map<String, Int> = mapOf(
        "com.whatsapp" to R.drawable.logo_whatsapp,
        "com.whatsapp.w4b" to R.drawable.logo_whatsapp,
        "com.facebook.orca" to R.drawable.logo_messenger,
        "com.instagram.android" to R.drawable.logo_instagram,
        "org.telegram.messenger" to R.drawable.logo_telegram,
        "com.discord" to R.drawable.logo_discord,
        "com.linkedin.android" to R.drawable.logo_linkedin,
        "com.twitter.android" to R.drawable.logo_x,
    )

    @DrawableRes
    fun forPackage(pkg: String): Int? = byPackage[pkg]
}
