package one.inbox.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val OneInboxShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

/** Default type scale; only `headlineLarge` is emphasized, for the hero titles (Inbox, Catch-up, Set up). */
private val OneInboxTypography = Typography().let { base ->
    base.copy(
        headlineLarge = base.headlineLarge.copy(
            fontWeight = FontWeight.Bold,
            fontSize = 32.sp,
            letterSpacing = (-0.5).sp,
        ),
    )
}

/**
 * Palette A "Violet night" on every Android version: no dynamic color, so both phones look the same.
 *
 * material3 1.4.0 keeps `MaterialExpressiveTheme` and `MotionScheme` internal, so this uses
 * `MaterialTheme`; expressive springs come from [ExpressiveMotion] instead of `MaterialTheme.motionScheme`.
 */
@Composable
fun OneInboxTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) VioletNightDark else VioletNightLight,
        shapes = OneInboxShapes,
        typography = OneInboxTypography,
        content = content,
    )
}
