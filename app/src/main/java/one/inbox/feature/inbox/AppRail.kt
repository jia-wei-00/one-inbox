package one.inbox.feature.inbox

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.dp
import one.inbox.ui.components.AppTile

/** Width of the app strip. AppTile draws its bar 10dp left of the 48dp tile, so 68dp leaves room. */
val RailWidth = 68.dp

/**
 * The vertical strip of app tiles: "All" first, then one tile per app with its real icon (or a
 * letter tile). Scrolls when there are more apps than fit. The vertical padding and spacing leave
 * room for the badges and selection outlines that AppTile draws outside its bounds.
 */
@Composable
fun AppRail(
    apps: List<RailApp>,
    selected: String?,
    totalUnread: Int,
    icon: (String) -> ImageBitmap?,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier.width(RailWidth).fillMaxHeight(),
        // Edge-to-edge (Android 15+): the last tile must scroll clear of the navigation bar.
        contentPadding = PaddingValues(
            top = 12.dp,
            bottom = 24.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item(key = "all") {
            AppTile(
                label = "All",
                icon = null,
                selected = selected == null,
                unread = totalUnread,
                onClick = { onSelect(null) },
                glyph = "All",
            )
        }
        items(apps, key = { it.packageName }) { app ->
            AppTile(
                label = app.label,
                icon = icon(app.packageName),
                selected = selected == app.packageName,
                unread = app.unreadCount,
                onClick = { onSelect(app.packageName) },
            )
        }
    }
}
