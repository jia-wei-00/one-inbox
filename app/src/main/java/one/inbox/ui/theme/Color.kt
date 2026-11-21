package one.inbox.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Palette A, "Violet night". The dark scheme uses the spec's token table verbatim; the roles the
 * table doesn't name are filled from the same violet / blue / coral seeds.
 */
val VioletNightDark: ColorScheme = darkColorScheme(
    primary = Color(0xFFCDB8FF),
    onPrimary = Color(0xFF331A6B),
    primaryContainer = Color(0xFF4B2F92),
    onPrimaryContainer = Color(0xFFEBDDFF),
    inversePrimary = Color(0xFF6447B5),
    secondary = Color(0xFFA8D3FF),
    onSecondary = Color(0xFF003352),
    secondaryContainer = Color(0xFF1F4A6B),
    onSecondaryContainer = Color(0xFFD1E6FF),
    tertiary = Color(0xFFFFB4A0),
    onTertiary = Color(0xFF561F0F),
    tertiaryContainer = Color(0xFF6D2A1A),
    onTertiaryContainer = Color(0xFFFFDBD1),
    background = Color(0xFF141218),
    onBackground = Color(0xFFE8E0F0),
    surface = Color(0xFF1D1B22),
    onSurface = Color(0xFFE8E0F0),
    surfaceVariant = Color(0xFF2F2B38),
    onSurfaceVariant = Color(0xFFB3AAC2),
    surfaceTint = Color(0xFFCDB8FF),
    inverseSurface = Color(0xFFE8E0F0),
    inverseOnSurface = Color(0xFF322F37),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFF948F99),
    outlineVariant = Color(0xFF49454F),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF3B3842),
    surfaceDim = Color(0xFF141218),
    surfaceContainerLowest = Color(0xFF0F0D13),
    surfaceContainerLow = Color(0xFF1D1B22),
    surfaceContainer = Color(0xFF211F27),
    surfaceContainerHigh = Color(0xFF26232D),
    surfaceContainerHighest = Color(0xFF2F2B38),
)

/** Light counterpart derived from the same seeds (violet primary, blue secondary, coral tertiary). */
val VioletNightLight: ColorScheme = lightColorScheme(
    primary = Color(0xFF6447B5),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFEBDDFF),
    onPrimaryContainer = Color(0xFF21005E),
    inversePrimary = Color(0xFFCDB8FF),
    secondary = Color(0xFF2F628C),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD1E6FF),
    onSecondaryContainer = Color(0xFF001D33),
    tertiary = Color(0xFF8F4C38),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDBD1),
    onTertiaryContainer = Color(0xFF3A0B01),
    background = Color(0xFFFEF7FF),
    onBackground = Color(0xFF1D1B22),
    surface = Color(0xFFFEF7FF),
    onSurface = Color(0xFF1D1B22),
    surfaceVariant = Color(0xFFE7E0EC),
    onSurfaceVariant = Color(0xFF49454F),
    surfaceTint = Color(0xFF6447B5),
    inverseSurface = Color(0xFF322F37),
    inverseOnSurface = Color(0xFFF5EFF7),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF93000A),
    outline = Color(0xFF7A757F),
    outlineVariant = Color(0xFFCAC4D0),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFFEF7FF),
    surfaceDim = Color(0xFFDED8E1),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF8F1FA),
    surfaceContainer = Color(0xFFF2ECF4),
    surfaceContainerHigh = Color(0xFFECE6EE),
    surfaceContainerHighest = Color(0xFFE6E0E9),
)

/** Avatar (container, content) pairs, in spec order. Chosen with [one.inbox.ui.components.avatarPairIndex]. */
val AvatarPairs: List<Pair<Color, Color>> = listOf(
    Color(0xFFFFD9E2) to Color(0xFF6E2240),
    Color(0xFFD3F5D6) to Color(0xFF1D5A2A),
    Color(0xFFD6E3FF) to Color(0xFF1B3D7A),
    Color(0xFFFFE0B8) to Color(0xFF6B3D00),
    Color(0xFFE9DDFF) to Color(0xFF47307A),
    Color(0xFFC9F1EF) to Color(0xFF0F4D49),
)

/** Group sender name colors, in spec order. Chosen with [one.inbox.ui.components.senderColorIndex]. */
val SenderColors: List<Color> = listOf(
    Color(0xFFFFB1C8),
    Color(0xFF9FE0A8),
    Color(0xFFA8C8FF),
    Color(0xFFFFD08A),
    Color(0xFFD7BCFF),
    Color(0xFF8EE6DE),
)

/** The "Done ✓" green used for completed setup steps. */
val DoneGreen: Color = Color(0xFF9FE0A8)
