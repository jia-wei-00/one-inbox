package one.inbox.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// Mockup proportions at the 48dp reference size.
private val ReferenceSize = 48.dp
private val TileCorner = 16.dp
private val OutlineWidth = 2.5.dp
private val OutlineGap = 2.dp
private val BarWidth = 4.dp
private val BarHeight = 26.dp
private val BarInset = 10.dp
private val BadgeRing = 2.dp

/**
 * A rounded-square app tile: the app icon, or [glyph] (when null, the label's first letter) on
 * `surfaceContainerHighest`.
 *
 * The layout bounds are the tile itself (at least 48dp for touch; exactly [size] when [onClick] is
 * null, for a tile that only illustrates a row which is itself the target). The selection outline (2dp
 * outside the tile), the selection bar (10dp left of the tile) and the unread badge (top-right)
 * are drawn outside those bounds, so the parent needs ~10dp of free space on the left and ~6dp
 * above/below — the 68dp-wide rail with a 48dp tile centered gives exactly that.
 */
@Composable
fun AppTile(
    label: String,
    icon: ImageBitmap?,
    selected: Boolean,
    unread: Int,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    glyph: String? = null,
) {
    val colors = MaterialTheme.colorScheme
    val scale = size / ReferenceSize
    val corner = TileCorner * scale
    val tileShape = RoundedCornerShape(corner)
    val description = "$label, $unread unread"
    val letterSize = with(LocalDensity.current) { (size * 0.36f).toSp() }

    Box(
        modifier
            .then(if (onClick != null) Modifier.minimumInteractiveComponentSize() else Modifier)
            .size(size)
            .drawBehind {
                if (!selected) return@drawBehind
                val stroke = OutlineWidth.toPx()
                val outset = OutlineGap.toPx() + stroke / 2
                drawRoundRect(
                    color = colors.primary,
                    topLeft = Offset(-outset, -outset),
                    size = Size(this.size.width + outset * 2, this.size.height + outset * 2),
                    cornerRadius = CornerRadius(corner.toPx() + outset),
                    style = Stroke(width = stroke),
                )
                val barHeight = BarHeight.toPx()
                drawRoundRect(
                    color = colors.primary,
                    topLeft = Offset(-BarInset.toPx(), (this.size.height - barHeight) / 2),
                    size = Size(BarWidth.toPx(), barHeight),
                    cornerRadius = CornerRadius(BarWidth.toPx()),
                )
            },
    ) {
        Box(
            Modifier
                .matchParentSize()
                .clip(tileShape)
                .background(colors.surfaceContainerHighest)
                .then(
                    if (onClick != null) {
                        // A filter tab: TalkBack announces it as a tab and whether it's the selected one.
                        Modifier.selectable(selected = selected, role = Role.Tab, onClick = onClick)
                    } else {
                        Modifier
                    },
                )
                .semantics { contentDescription = description },
            contentAlignment = Alignment.Center,
        ) {
            if (icon != null) {
                Image(
                    icon,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize(),
                )
            } else {
                Text(
                    glyph ?: initialsOf(label),
                    color = colors.onSurface,
                    fontSize = letterSize,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
            }
        }
        if (unread > 0) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 8.dp, y = (-7).dp)
                    .clearAndSetSemantics {}
                    .background(colors.background, CircleShape)
                    .padding(BadgeRing),
            ) {
                CountPill(unread)
            }
        }
    }
}
