package com.jdevsappstudio.kept.feature.library.shelves

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DragHandle
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jdevsappstudio.kept.core.designsystem.CardShape
import com.jdevsappstudio.kept.core.designsystem.KeptTopBar
import com.jdevsappstudio.kept.core.designsystem.card
import com.jdevsappstudio.kept.core.designsystem.shelfGround
import com.jdevsappstudio.kept.core.model.Shelf
import org.koin.androidx.compose.koinViewModel

private val ROW_HEIGHT = 68.dp

@Composable
fun ShelvesRoot(onBack: () -> Unit, onOpenShelf: (String) -> Unit, viewModel: ShelvesViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ShelvesScreen(state) { action ->
        when (action) {
            ShelvesAction.OnBackClick -> onBack()
            is ShelvesAction.OnShelfClick -> onOpenShelf(action.kind)
            else -> viewModel.onAction(action)
        }
    }
}

@Composable
fun ShelvesScreen(state: ShelvesState, onAction: (ShelvesAction) -> Unit) {
    // Local order while dragging; reset whenever the stored order (or the set of shelves) changes.
    val order = remember(state.shelves.map { it.kind }) { mutableStateListOf<String>().apply { addAll(state.shelves.map { it.kind }) } }
    val byKind = state.shelves.associateBy { it.kind }
    var dragging by remember { mutableStateOf<String?>(null) }
    var offset by remember { mutableFloatStateOf(0f) }
    val rowPx = with(LocalDensity.current) { ROW_HEIGHT.toPx() }
    val onActionNow by rememberUpdatedState(onAction)

    fun move(from: Int, to: Int) {
        if (from == to || to !in order.indices) return
        order.add(to, order.removeAt(from))
    }
    fun commit() = onActionNow(ShelvesAction.OnReorder(order.toList()))

    Column(
        Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        KeptTopBar("All shelves", onBack = { onAction(ShelvesAction.OnBackClick) })
        Text(
            "Kept makes a shelf when 3 or more saves share a kind. Home shows the first 4 that are on.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (order.isNotEmpty()) {
            Column(Modifier.fillMaxWidth().card(MaterialTheme.colorScheme.surface, 1.dp)) {
                order.forEachIndexed { i, kind ->
                    val shelf = byKind[kind] ?: return@forEachIndexed
                    key(kind) {
                        val lifted = dragging == kind
                        Column(
                            Modifier
                                .zIndex(if (lifted) 1f else 0f)
                                .graphicsLayer { translationY = if (lifted) offset else 0f }
                                .then(if (lifted) Modifier.shadow(6.dp, RoundedCornerShape(12.dp)) else Modifier)
                                .background(MaterialTheme.colorScheme.surface),
                        ) {
                            if (i > 0 && !lifted) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            ShelfRow(
                                shelf = shelf,
                                index = i,
                                last = order.lastIndex,
                                onAction = onAction,
                                onMove = { to -> move(order.indexOf(kind), to); commit() },
                                handle = Modifier.pointerInput(kind) {
                                    detectVerticalDragGestures(
                                        onDragStart = { dragging = kind; offset = 0f },
                                        onDragEnd = { dragging = null; offset = 0f; commit() },
                                        onDragCancel = { dragging = null; offset = 0f },
                                    ) { change, dy ->
                                        change.consume()
                                        offset += dy
                                        val at = order.indexOf(kind)
                                        if (offset > rowPx / 2 && at < order.lastIndex) { move(at, at + 1); offset -= rowPx }
                                        else if (offset < -rowPx / 2 && at > 0) { move(at, at - 1); offset += rowPx }
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
        Text(
            "Drag ≡ to change the order on home. Hidden shelves still fill up and show in search.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ShelfRow(
    shelf: Shelf,
    index: Int,
    last: Int,
    onAction: (ShelvesAction) -> Unit,
    onMove: (Int) -> Unit,
    handle: Modifier,
) {
    val count = if (shelf.countsItems) "${shelf.entries} items" else "${shelf.saves} saves"
    Row(
        Modifier.fillMaxWidth().height(ROW_HEIGHT).padding(end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            handle.size(48.dp).semantics { contentDescription = "Drag to reorder ${shelf.title}" },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.DragHandle, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(
            Modifier.weight(1f).fillMaxSize()
                .clickable(role = Role.Button, onClickLabel = "Open") { onAction(ShelvesAction.OnShelfClick(shelf.kind)) }
                .semantics(mergeDescendants = true) {
                    customActions = listOfNotNull(
                        if (index > 0) CustomAccessibilityAction("Move up") { onMove(index - 1); true } else null,
                        if (index < last) CustomAccessibilityAction("Move down") { onMove(index + 1); true } else null,
                    )
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(40.dp).clip(CardShape).background(shelfGround(shelf.kind)), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.Layers, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(20.dp))
            }
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(shelf.title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
                Text(count, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(
            checked = !shelf.hidden,
            onCheckedChange = { onAction(ShelvesAction.OnShowOnHome(shelf.kind, it)) },
            modifier = Modifier.semantics { contentDescription = "Show ${shelf.title} on home" },
        )
    }
}
