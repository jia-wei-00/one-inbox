package one.inbox.core

import android.content.Context
import android.content.pm.PackageManager
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

interface AppInfoProvider {
    fun label(pkg: String): String
    fun icon(pkg: String): ImageBitmap?
    fun isLaunchable(pkg: String): Boolean
}

class PackageAppInfoProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) : AppInfoProvider {
    override fun label(pkg: String): String {
        ChatApps.displayName(pkg)?.let { return it }
        val pm = context.packageManager
        return try {
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
        } catch (_: PackageManager.NameNotFoundException) {
            pkg
        }
    }

    override fun isLaunchable(pkg: String): Boolean =
        context.packageManager.getLaunchIntentForPackage(pkg) != null

    override fun icon(pkg: String): ImageBitmap? = try {
        context.packageManager.getApplicationIcon(pkg).toBitmap().asImageBitmap()
    } catch (_: PackageManager.NameNotFoundException) {
        null
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
