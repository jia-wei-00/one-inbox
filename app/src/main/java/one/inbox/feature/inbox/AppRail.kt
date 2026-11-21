package one.inbox.feature.inbox

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Badge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.dp

@Composable
fun AppRail(
    apps: List<RailApp>,
    selected: String?,
    icon: (String) -> ImageBitmap?,
    onCatchUp: () -> Unit,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier.width(64.dp).fillMaxHeight().background(MaterialTheme.colorScheme.surfaceVariant),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item { RailItem("✨", false, 0, onCatchUp, "Catch-up") }
        item { RailItem("📥", selected == null, apps.sumOf { it.unreadCount }, { onSelect(null) }, "All") }
        items(apps, key = { it.packageName }) { app ->
            val bitmap = remember(app.packageName) { icon(app.packageName) }
            RailItem(
                glyph = null,
                selected = selected == app.packageName,
                badge = app.unreadCount,
                onClick = { onSelect(app.packageName) },
                description = app.label,
                bitmap = bitmap,
            )
        }
    }
}

@Composable
private fun RailItem(
    glyph: String?,
    selected: Boolean,
    badge: Int,
    onClick: () -> Unit,
    description: String,
    bitmap: ImageBitmap? = null,
) {
    Box(Modifier.padding(vertical = 6.dp).clickable(onClick = onClick)) {
        val bg = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(bg), contentAlignment = Alignment.Center) {
            if (bitmap != null) {
                Image(bitmap, description, Modifier.size(30.dp).clip(CircleShape))
            } else {
                Text(glyph ?: description.take(1).uppercase())
            }
        }
        if (badge > 0) {
            Badge(
                modifier = Modifier.align(Alignment.TopEnd),
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) { Text(if (badge > 99) "99+" else badge.toString(), color = MaterialTheme.colorScheme.onPrimary) }
        }
    }
}
