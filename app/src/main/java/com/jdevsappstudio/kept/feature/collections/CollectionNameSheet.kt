package com.jdevsappstudio.kept.feature.collections

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jdevsappstudio.kept.core.designsystem.ButtonShape
import com.jdevsappstudio.kept.core.designsystem.KeptTheme
import com.jdevsappstudio.kept.core.designsystem.PillShape
import com.jdevsappstudio.kept.core.presentation.ObserveAsEvents
import com.jdevsappstudio.kept.data.library.CollectionNames
import org.koin.androidx.compose.koinViewModel

/**
 * k-newcol: New collection / Rename, a bottom sheet in a dialog destination. Where Created goes is the caller's call
 * ([onCreated]): home opens the new, empty collection; the edit sheet ticks it.
 */
@Composable
fun CollectionNameRoot(
    onClose: () -> Unit,
    onCreated: (Long) -> Unit,
    viewModel: CollectionNameViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            is CollectionNameEvent.Created -> onCreated(event.id)
            CollectionNameEvent.Close -> onClose()
        }
    }
    BackHandler { viewModel.onAction(CollectionNameAction.OnCancel) }
    CollectionNameSheet(state, viewModel::onAction)
}

@Composable
fun CollectionNameSheet(state: CollectionNameState, onAction: (CollectionNameAction) -> Unit) {
    val cancel = { onAction(CollectionNameAction.OnCancel) }
    Box(
        Modifier.fillMaxSize()
            .background(Color.Black.copy(alpha = 0.4f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClickLabel = "Close", onClick = cancel)
            .statusBarsPadding(),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(MaterialTheme.colorScheme.background)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {} // taps inside stay inside
                .imePadding()
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                Modifier.align(Alignment.CenterHorizontally).size(width = 40.dp, height = 4.dp)
                    .clip(PillShape).background(MaterialTheme.colorScheme.outlineVariant),
            )
            Text(
                if (state.rename) "Rename" else "New collection",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(top = 12.dp).semantics { heading() },
            )
            if (!state.loading) NameField(state, onAction)
            if (!state.rename) {
                Text(CollectionCopy.NEW_LINE, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = cancel, shape = ButtonShape, modifier = Modifier.weight(1f).heightIn(min = 52.dp)) {
                    Text("Cancel", style = MaterialTheme.typography.labelLarge)
                }
                Button(
                    onClick = { onAction(CollectionNameAction.OnSubmit) },
                    enabled = state.canSubmit,
                    shape = ButtonShape,
                    modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                ) {
                    Text(if (state.rename) "Save" else "Create", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
private fun NameField(state: CollectionNameState, onAction: (CollectionNameAction) -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    OutlinedTextField(
        value = state.name,
        onValueChange = { onAction(CollectionNameAction.OnNameChange(it)) },
        label = { Text("Name") },
        placeholder = { Text("Goa trip, Gift ideas…") },
        isError = state.takenBy != null,
        supportingText = {
            val taken = state.takenBy
            Text(if (taken != null) "You already have $taken" else "${state.name.length} / ${CollectionNames.MAX_LENGTH}")
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onAction(CollectionNameAction.OnSubmit) }),
        modifier = Modifier.fillMaxWidth().focusRequester(focus),
    )
}

@Preview
@Composable
private fun CollectionNameSheetPreview() {
    KeptTheme { CollectionNameSheet(CollectionNameState(name = "Weekend brunch")) {} }
}

@Preview
@Composable
private fun CollectionNameSheetTakenPreview() {
    KeptTheme { CollectionNameSheet(CollectionNameState(name = "goa trip", takenBy = "Goa trip")) {} }
}
