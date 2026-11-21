package one.inbox.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * The app's one tonal button style: `surfaceContainerHighest` / `onSurface`, so secondary actions
 * read the same on every screen (Catch-up "Mark all read", Onboarding "Open").
 */
@Composable
fun OneInboxTonalButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    content: @Composable RowScope.() -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = colors.surfaceContainerHighest,
            contentColor = colors.onSurface,
        ),
        contentPadding = contentPadding,
        content = content,
    )
}
