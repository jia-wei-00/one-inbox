package one.inbox.ui.theme

import android.provider.Settings
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/** Motion is off only when the system animator duration scale is 0 ("Remove animations"). */
fun motionEnabled(animatorScale: Float): Boolean = animatorScale != 0f

/**
 * Whether springs should run. Reads `Settings.Global.ANIMATOR_DURATION_SCALE` (default 1f);
 * pass the result to [ExpressiveMotion]'s specs so motion snaps when it's false.
 */
@Composable
fun rememberMotionEnabled(): Boolean {
    val resolver = LocalContext.current.contentResolver
    return remember(resolver) {
        motionEnabled(Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f))
    }
}

/**
 * The Material 3 Expressive motion scheme. material3 1.4.0 keeps `MotionScheme.expressive()`
 * internal, so these mirror its method names and use the same spring values as its
 * `ExpressiveMotionTokens`. "Spatial" specs move or resize things (may overshoot); "effects"
 * specs fade or recolor (no overshoot). Every spec becomes `snap()` when [enabled] is false.
 */
object ExpressiveMotion {
    fun <T> fastSpatialSpec(enabled: Boolean = true): FiniteAnimationSpec<T> =
        if (enabled) spring(dampingRatio = 0.6f, stiffness = 800f) else snap()

    fun <T> defaultSpatialSpec(enabled: Boolean = true): FiniteAnimationSpec<T> =
        if (enabled) spring(dampingRatio = 0.8f, stiffness = 380f) else snap()

    fun <T> slowSpatialSpec(enabled: Boolean = true): FiniteAnimationSpec<T> =
        if (enabled) spring(dampingRatio = 0.8f, stiffness = 200f) else snap()

    fun <T> fastEffectsSpec(enabled: Boolean = true): FiniteAnimationSpec<T> =
        if (enabled) spring(dampingRatio = 1f, stiffness = 3800f) else snap()

    fun <T> defaultEffectsSpec(enabled: Boolean = true): FiniteAnimationSpec<T> =
        if (enabled) spring(dampingRatio = 1f, stiffness = 1600f) else snap()

    fun <T> slowEffectsSpec(enabled: Boolean = true): FiniteAnimationSpec<T> =
        if (enabled) spring(dampingRatio = 1f, stiffness = 800f) else snap()
}
