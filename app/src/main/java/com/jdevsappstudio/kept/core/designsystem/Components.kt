package com.jdevsappstudio.kept.core.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.LocalOffer
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.outlined.TipsAndUpdates
import androidx.compose.material.icons.outlined.Article
import androidx.compose.material.icons.outlined.StickyNote2
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.jdevsappstudio.kept.R
import com.jdevsappstudio.kept.core.model.ReadBy
import com.jdevsappstudio.kept.core.model.SaveType
import java.io.File

/** Card and tile corner (design doc: 18 px cards, 14 px buttons, pills). */
val CardShape = RoundedCornerShape(18.dp)
val ButtonShape = RoundedCornerShape(14.dp)
val PillShape = RoundedCornerShape(50)

/** The launcher mark on its dark ground, for the app bar (k-home 'Kept'). */
@Composable
fun KeptMark(size: Dp = 36.dp) {
    Box(
        Modifier.size(size).clip(RoundedCornerShape(size / 4)).background(Color(0xFF1D1A1F)),
        contentAlignment = Alignment.Center,
    ) {
        Image(painterResource(R.drawable.ic_launcher_foreground), contentDescription = null, modifier = Modifier.size(size * 1.5f))
    }
}

/** The white square icon button of the app bars (+, gear, search, back, ⋮). */
@Composable
fun KeptIconButton(icon: ImageVector, contentDescription: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        shape = ButtonShape,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 1.dp,
        modifier = modifier.size(48.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = contentDescription, tint = MaterialTheme.colorScheme.onSurface)
        }
    }
}

/** Filter pill: 'All 12', 'Recipe 12'. Selected = plum ring on a light plum ground. */
@Composable
fun KeptChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = PillShape,
        color = if (selected) scheme.primary.copy(alpha = 0.08f) else scheme.surface.copy(alpha = 0f),
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) scheme.primary else scheme.outline),
        modifier = modifier.heightIn(min = 44.dp),
    ) {
        Box(Modifier.padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = scheme.onSurface, maxLines = 1)
        }
    }
}

/** Small tag pill on cards ('air-fryer', 'Goa'). [onDark] for text over a picture. */
@Composable
fun TagPill(text: String, modifier: Modifier = Modifier, icon: ImageVector? = null, onDark: Boolean = false) {
    val fg = if (onDark) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier
            .clip(PillShape)
            .then(if (onDark) Modifier.background(Color.Black.copy(alpha = 0.35f)) else Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, PillShape))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(12.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** The routing chip (H3): 'On this phone' (green) or 'Cloud' (blue). Nothing for hand-tagged or unread saves. */
@Composable
fun RouteBadge(readBy: ReadBy?, modifier: Modifier = Modifier) {
    val c = Kept.colors
    val (label, icon, fg, bg) = when (readBy) {
        ReadBy.DEVICE -> Quad("On this phone", Icons.Outlined.PhoneAndroid, c.device, c.deviceBg)
        ReadBy.CLOUD -> Quad("Cloud", Icons.Outlined.Cloud, c.cloud, c.cloudBg)
        else -> return
    }
    Row(
        modifier.clip(PillShape).background(bg).padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(12.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = fg, maxLines = 1)
    }
}

private data class Quad(val label: String, val icon: ImageVector, val fg: Color, val bg: Color)

/** 'SHELVES' ··· 'See all 6'. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, action: String? = null, onAction: () -> Unit = {}) {
    Row(modifier.fillMaxWidth().heightIn(min = 32.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text.uppercase(), style = MaterialTheme.typography.labelSmall, color = Kept.colors.text3, modifier = Modifier.weight(1f))
        if (action != null) {
            Text(
                action,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clip(PillShape).clickable(role = Role.Button, onClick = onAction).padding(8.dp),
            )
        }
    }
}

/** A save's picture (thumb or original) or, without one, a tinted square. */
@Composable
fun SaveThumb(path: String?, type: SaveType?, modifier: Modifier = Modifier, corner: Dp = 10.dp) {
    val shape = RoundedCornerShape(corner)
    if (path != null) {
        AsyncImage(
            model = File(path),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier.clip(shape).background(typeTint(type)),
        )
    } else {
        Box(modifier.clip(shape).background(typeTint(type)), contentAlignment = Alignment.Center) {
            Icon(typeIcon(type), contentDescription = null, tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(20.dp))
        }
    }
}

/** Up to 3 small squares on shelf and collection tiles; empty slots stay blank. */
@Composable
fun PreviewRow(previews: List<String>, count: Int, type: SaveType? = null, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        val shown = previews.take(3)
        shown.forEach { SaveThumb(it, type, Modifier.size(36.dp)) }
        repeat((minOf(count, 3) - shown.size).coerceAtLeast(0)) { SaveThumb(null, type, Modifier.size(36.dp)) }
    }
}

fun typeIcon(type: SaveType?): ImageVector = when (type) {
    SaveType.RECIPE -> Icons.Outlined.Restaurant
    SaveType.PLACE -> Icons.Outlined.Place
    SaveType.PRODUCT -> Icons.Outlined.ShoppingBag
    SaveType.MOVIE_SHOW -> Icons.Outlined.Movie
    SaveType.VIDEO -> Icons.Outlined.PlayCircle
    SaveType.READ -> Icons.Outlined.Article
    SaveType.EVENT -> Icons.Outlined.Event
    SaveType.TIP -> Icons.Outlined.TipsAndUpdates
    SaveType.APP -> Icons.Outlined.Apps
    SaveType.IDEA -> Icons.Outlined.Lightbulb
    SaveType.OTHER -> Icons.Outlined.StickyNote2
    null -> Icons.Outlined.LocalOffer
}

/** Muted ground per type for picture-less saves (B · Rich tints). */
fun typeTint(type: SaveType?): Color = when (type) {
    SaveType.RECIPE -> Color(0xFFC9773F)
    SaveType.PLACE -> Color(0xFF6E9A7A)
    SaveType.PRODUCT -> Color(0xFFA48BC4)
    SaveType.MOVIE_SHOW -> Color(0xFF4F5F8F)
    SaveType.VIDEO -> Color(0xFF8F5F7F)
    SaveType.READ -> Color(0xFF7A8597)
    SaveType.EVENT -> Color(0xFFB0705F)
    SaveType.TIP -> Color(0xFFB59A4A)
    SaveType.APP -> Color(0xFF5F8F95)
    SaveType.IDEA -> Color(0xFFC29A3F)
    SaveType.OTHER, null -> Color(0xFF8C8490)
}

/** Shelf tile grounds: one muted tint per shelf, picked from its kind so it stays put. */
@Composable
fun shelfGround(kind: String): Color {
    val dark = MaterialTheme.colorScheme.background.luminanceLow()
    val light = listOf(Color(0xFFE4E7F2), Color(0xFFEDE6F3), Color(0xFFF3E4EE), Color(0xFFE3EEE6), Color(0xFFF5ECD9))
    val darkOnes = listOf(Color(0xFF1F2333), Color(0xFF2A2233), Color(0xFF33222D), Color(0xFF1C2A22), Color(0xFF2E2717))
    val i = (kind.hashCode() and 0x7fffffff) % light.size
    return if (dark) darkOnes[i] else light[i]
}

private fun Color.luminanceLow(): Boolean = (red * 0.299f + green * 0.587f + blue * 0.114f) < 0.5f

/** A dashed-look hint card (k-home-early shelves, 'New collection'). */
@Composable
fun HintCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    Box(
        modifier
            .clip(CardShape)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f), CardShape)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(16.dp),
    ) { content() }
}

@Composable
fun VSpace(h: Dp) = Spacer(Modifier.height(h))

@Composable
fun HSpace(w: Dp) = Spacer(Modifier.width(w))

/** Cards: surface + soft shadow (B · Rich). */
fun Modifier.card(color: Color, elevation: Dp = 2.dp): Modifier =
    shadow(elevation, CardShape, clip = false).clip(CardShape).background(color)
