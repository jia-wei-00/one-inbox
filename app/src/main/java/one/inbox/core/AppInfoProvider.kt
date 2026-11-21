package one.inbox.core

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.app.NotificationManagerCompat
import androidx.core.graphics.drawable.toBitmap
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

interface AppInfoProvider {
    fun label(pkg: String): String
    fun icon(pkg: String): ImageBitmap?
    fun isLaunchable(pkg: String): Boolean
    fun launchIntent(pkg: String): Intent?
}

/**
 * PackageManager-backed app info. App-scoped, so every screen shares one icon cache: each package's
 * icon is decoded once, at [ICON_SIZE_DP] (the largest size any screen draws it), and a package
 * with no icon is remembered as such.
 */
@Singleton
class PackageAppInfoProvider(
    private val context: Context,
    private val loadIcon: (String) -> Drawable?,
) : AppInfoProvider {
    @Inject constructor(@ApplicationContext context: Context) : this(context, { pkg ->
        try {
            context.packageManager.getApplicationIcon(pkg)
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }
    })

    override fun label(pkg: String): String {
        ChatApps.displayName(pkg)?.let { return it }
        val pm = context.packageManager
        return try {
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
        } catch (_: PackageManager.NameNotFoundException) {
            pkg
        }
    }

    override fun isLaunchable(pkg: String): Boolean = launchIntent(pkg) != null

    override fun launchIntent(pkg: String): Intent? =
        context.packageManager.getLaunchIntentForPackage(pkg)

    private val iconSizePx = (ICON_SIZE_DP * context.resources.displayMetrics.density).roundToInt()

    /** LruCache can't hold null, so a missing icon is cached as an empty [CachedIcon]. */
    private class CachedIcon(val bitmap: ImageBitmap?)

    private val icons = LruCache<String, CachedIcon>(ICON_CACHE_SIZE)

    override fun icon(pkg: String): ImageBitmap? {
        icons.get(pkg)?.let { return it.bitmap }
        val bitmap = loadIcon(pkg)?.toBitmap(iconSizePx, iconSizePx)?.asImageBitmap()
        icons.put(pkg, CachedIcon(bitmap))
        return bitmap
    }

    private companion object {
        const val ICON_SIZE_DP = 48
        const val ICON_CACHE_SIZE = 64
    }
}

interface AccessChecker {
    fun hasNotificationAccess(): Boolean
}

class NotificationAccessChecker @Inject constructor(
    @ApplicationContext private val context: Context,
) : AccessChecker {
    override fun hasNotificationAccess(): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class AppInfoModule {
    @Binds abstract fun appInfo(impl: PackageAppInfoProvider): AppInfoProvider
    @Binds abstract fun accessChecker(impl: NotificationAccessChecker): AccessChecker
}
