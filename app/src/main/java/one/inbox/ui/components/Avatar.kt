package one.inbox.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import one.inbox.ui.theme.AvatarPairs
import one.inbox.ui.theme.SenderColors

/** Index into [AvatarPairs] for a conversation key; stable across runs. */
fun avatarPairIndex(key: String): Int = Math.floorMod(key.hashCode(), AvatarPairs.size)

/** Index into [SenderColors] for a group sender name; stable across runs. */
fun senderColorIndex(sender: String): Int = Math.floorMod(sender.hashCode(), SenderColors.size)

/** First code point of the trimmed title, uppercased; "?" when the title is blank. */
fun initialsOf(title: String): String {
    val trimmed = title.trim()
    if (trimmed.isEmpty()) return "?"
    val first = String(Character.toChars(trimmed.codePointAt(0)))
    return first.uppercase()
}

// Proportions from the mockup at the 46dp reference size.
private val ReferenceSize = 46.dp
private val BadgeSize = 18.dp
private val BadgeCorner = 6.dp
private val BadgeRing = 2.dp
private val BadgeOverhang = 3.dp

/**
 * A circular avatar with initials in one of the six pastel pairs. When [appIcon] is set, the
 * source app's icon sits at the bottom-right as a rounded badge ringed in the surface color.
 * The badge overhangs the avatar's bounds slightly (as in the mockup); it is not clipped.
 */
@Composable
fun Avatar(
    title: String,
    colorKey: String,
    appIcon: ImageBitmap?,
    size: Dp = 46.dp,
    modifier: Modifier = Modifier,
) {
    val (container, content) = AvatarPairs[avatarPairIndex(colorKey)]
    val scale = size / ReferenceSize
    val fontSize = with(LocalDensity.current) { (size * 0.36f).toSp() }
    Box(modifier.size(size)) {
        Box(
            Modifier.matchParentSize().clip(CircleShape).background(container),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                initialsOf(title),
                color = content,
                fontSize = fontSize,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
        }
        if (appIcon != null) {
            val ring = BadgeRing * scale
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = BadgeOverhang * scale, y = BadgeOverhang * scale)
                    .testTag("avatar-badge")
                    .size(BadgeSize * scale + ring * 2)
                    .clip(RoundedCornerShape(BadgeCorner * scale + ring))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(ring),
            ) {
                Image(
                    appIcon,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize().clip(RoundedCornerShape(BadgeCorner * scale)),
                )
            }
        }
    }
}
