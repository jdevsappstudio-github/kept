package com.jdevsappstudio.kept.feature.add

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jdevsappstudio.kept.core.designsystem.ButtonShape
import com.jdevsappstudio.kept.core.designsystem.CardShape
import com.jdevsappstudio.kept.core.designsystem.KeptChip
import com.jdevsappstudio.kept.core.designsystem.KeptMark
import com.jdevsappstudio.kept.core.designsystem.KeptTheme
import com.jdevsappstudio.kept.core.designsystem.KeptTopBar
import com.jdevsappstudio.kept.core.designsystem.PillShape
import com.jdevsappstudio.kept.core.designsystem.card
import org.koin.androidx.compose.koinViewModel

@Composable
fun HowToRoot(onBack: () -> Unit, viewModel: HowToViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    HowToScreen(state) { action ->
        when (action) {
            HowToAction.OnBack -> onBack()
            HowToAction.OnOpenAppClick -> openApp(context, state.app.packageName)
            else -> viewModel.onAction(action)
        }
    }
}

/** The app if it is installed, else its Play page (k-howto note). */
private fun openApp(context: Context, packageName: String) {
    val launch = context.packageManager.getLaunchIntentForPackage(packageName)
    val intents = listOfNotNull(
        launch,
        Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName")),
        Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$packageName")),
    )
    for (intent in intents) {
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        } catch (_: ActivityNotFoundException) {
            // try the next one
        }
    }
}

/** k-howto "Share from an app" (H33, H63). */
@Composable
fun HowToScreen(state: HowToState, onAction: (HowToAction) -> Unit) {
    val app = state.app
    val pager = rememberPagerState { app.steps.size }
    LaunchedEffect(state.selected) { pager.scrollToPage(0) }
    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        KeptTopBar("Share from an app", onBack = { onAction(HowToAction.OnBack) })
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HowToContent.apps.forEachIndexed { i, a ->
                KeptChip(a.name, selected = i == state.selected, onClick = { onAction(HowToAction.OnAppClick(i)) })
            }
        }
        Column(
            Modifier.fillMaxWidth().card(MaterialTheme.colorScheme.surface, 1.dp).padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            HorizontalPager(pager, modifier = Modifier.fillMaxWidth()) { page ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier.fillMaxWidth().height(240.dp).clip(RoundedCornerShape(14.dp))
                            .semantics { contentDescription = "Step ${page + 1} of ${app.steps.size}: ${app.steps[page]}" },
                    ) {
                        when (page) {
                            0 -> PostDrawing(app.control)
                            1 -> SheetDrawing()
                            else -> TargetsDrawing()
                        }
                    }
                    Text(
                        "${page + 1} · ${app.steps[page]}",
                        style = MaterialTheme.typography.titleMedium.copy(fontStyle = FontStyle.Italic),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }
            Dots(count = app.steps.size, current = pager.currentPage)
        }
        Button(
            onClick = { onAction(HowToAction.OnOpenAppClick) },
            shape = ButtonShape,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        ) {
            Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null)
            Spacer(Modifier.width(10.dp))
            Text("Open ${app.name}", style = MaterialTheme.typography.labelLarge)
        }
        Text(HowToContent.PIN_TIP, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Dots(count: Int, current: Int) {
    Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        repeat(count) { i ->
            Box(
                Modifier.height(6.dp).width(if (i == current) 18.dp else 6.dp).clip(PillShape)
                    .background(if (i == current) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant),
            )
        }
    }
}

private val PostBrush = Brush.verticalGradient(listOf(Color(0xFFC98B55), Color(0xFF8A5634), Color(0xFF3A2418)))

/** Card 1: a post with the side controls; the app's share control is ringed. */
@Composable
private fun PostDrawing(control: ShareControl) {
    Box(Modifier.fillMaxSize().background(PostBrush)) {
        Ringable(Icons.Outlined.MoreVert, ringed = control == ShareControl.MENU, Modifier.align(Alignment.TopEnd).padding(10.dp))
        Column(
            Modifier.align(Alignment.CenterEnd).padding(end = 10.dp, top = 40.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Ringable(Icons.Outlined.FavoriteBorder, ringed = false)
            Ringable(Icons.Outlined.ChatBubbleOutline, ringed = false)
            Ringable(Icons.AutoMirrored.Outlined.Send, ringed = control == ShareControl.ARROW)
        }
    }
}

@Composable
private fun Ringable(icon: ImageVector, ringed: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier.size(44.dp).then(if (ringed) Modifier.border(2.5.dp, Color.White, CircleShape) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
    }
}

/** Card 2: the app's own share menu, with its Share row ringed. */
@Composable
private fun SheetDrawing() {
    Box(Modifier.fillMaxSize().background(PostBrush)) {
        Panel {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Line(0.4f)
                Row(
                    Modifier.fillMaxWidth().border(2.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.AutoMirrored.Outlined.Send, contentDescription = null, tint = Color(0xFF2A2A2A), modifier = Modifier.size(18.dp))
                    Text("Share", style = MaterialTheme.typography.labelLarge, color = Color(0xFF2A2A2A), modifier = Modifier.padding(start = 10.dp))
                }
                Line(0.7f)
                Line(0.55f)
            }
        }
    }
}

/** Card 3: Android's share sheet, with the Kept tile ringed. */
@Composable
private fun TargetsDrawing() {
    Box(Modifier.fillMaxSize().background(PostBrush)) {
        Panel {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Line(0.35f)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    repeat(3) { AppDot() }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.border(2.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(14.dp)).padding(4.dp)) {
                            KeptMark(40.dp)
                        }
                        Text("Kept", style = MaterialTheme.typography.labelMedium, color = Color(0xFF2A2A2A))
                    }
                }
            }
        }
    }
}

@Composable
private fun BoxScope.Panel(content: @Composable () -> Unit) {
    Box(
        Modifier.align(Alignment.BottomCenter).fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp)).background(Color(0xFFF7F4F0)),
    ) { content() }
}

@Composable
private fun Line(fraction: Float) {
    Box(Modifier.fillMaxWidth(fraction).height(10.dp).clip(PillShape).background(Color(0x1F000000)))
}

@Composable
private fun AppDot() {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.padding(4.dp).size(40.dp).clip(CardShape).background(Color(0x26000000)))
        Box(Modifier.width(30.dp).height(8.dp).clip(PillShape).background(Color(0x1F000000)))
    }
}

@Preview(showBackground = true, heightDp = 760)
@Composable
private fun HowToScreenPreview() {
    KeptTheme(dark = false) { HowToScreen(HowToState()) {} }
}
