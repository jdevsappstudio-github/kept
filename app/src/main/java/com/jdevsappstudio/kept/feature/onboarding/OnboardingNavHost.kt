package com.jdevsappstudio.kept.feature.onboarding

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.jdevsappstudio.kept.core.presentation.ItemActionLauncher
import com.jdevsappstudio.kept.core.presentation.KeptSnackbarHost
import com.jdevsappstudio.kept.core.presentation.ObserveAsEvents
import com.jdevsappstudio.kept.feature.sent.SentRoot
import com.jdevsappstudio.kept.feature.settings.PrivacyRoot
import com.jdevsappstudio.kept.feature.settings.UnsupportedRoot
import com.jdevsappstudio.kept.navigation.ObAppsRoute
import com.jdevsappstudio.kept.navigation.ObCardRoute
import com.jdevsappstudio.kept.navigation.ObFirstRoute
import com.jdevsappstudio.kept.navigation.ObTypesRoute
import com.jdevsappstudio.kept.navigation.OpenRequest
import com.jdevsappstudio.kept.navigation.PrivacyRoute
import com.jdevsappstudio.kept.navigation.PromiseRoute
import com.jdevsappstudio.kept.navigation.SentRoute
import com.jdevsappstudio.kept.navigation.UnsupportedRoute
import java.util.UUID
import org.koin.androidx.compose.koinViewModel

/**
 * The first run (flows 'First run', H70), shown by MainActivity instead of the app until onboarding is done.
 * One [OnboardingViewModel] per run ([run] key), so a debug replay starts clean. [onFinish] gets the save to open, if any,
 * before the done flag flips and MainActivity swaps to the app.
 */
@Composable
fun OnboardingNavHost(onFinish: (OpenRequest?) -> Unit, nav: NavHostController = rememberNavController()) {
    val run = rememberSaveable { UUID.randomUUID().toString() }
    val viewModel: OnboardingViewModel = koinViewModel(key = "onboarding-$run")
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        viewModel.onAction(OnboardingAction.OnPicked(uri?.toString()))
    }
    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            OnboardingEvent.ToUnsupported -> nav.navigate(UnsupportedRoute)
            OnboardingEvent.ToTypes -> nav.navigate(ObTypesRoute) { launchSingleTop = true }
            OnboardingEvent.ToApps -> nav.navigate(ObAppsRoute) { launchSingleTop = true }
            OnboardingEvent.ToFirst -> nav.navigate(ObFirstRoute) { launchSingleTop = true }
            OnboardingEvent.LaunchPicker -> picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            is OnboardingEvent.Launch -> ItemActionLauncher.launch(context, event.target)
            is OnboardingEvent.Finish -> onFinish(event.open)
        }
    }
    val onAction: (OnboardingAction) -> Unit = { action ->
        when (action) {
            OnboardingAction.OnHowKeptUsesAi -> nav.navigate(PrivacyRoute)
            OnboardingAction.OnBack -> nav.popBackStack()
            is OnboardingAction.OnOpenApp -> action.app.pkg
                ?.let { context.packageManager.getLaunchIntentForPackage(it) }
                ?.let { context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            else -> viewModel.onAction(action)
        }
    }

    Scaffold(snackbarHost = { KeptSnackbarHost() }, containerColor = MaterialTheme.colorScheme.background) { inner ->
        NavHost(nav, startDestination = PromiseRoute, modifier = Modifier.padding(PaddingValues(bottom = inner.calculateBottomPadding()))) {
            composable<PromiseRoute> { PromiseScreen(state, onAction) }
            composable<ObTypesRoute> {
                // B1 has no back arrow: system back returns to the promise (or k-unsupported on a no-Nano phone).
                TypesScreen(state, onAction)
            }
            composable<ObAppsRoute> { AppsScreen(state, onAction) }
            composable<ObFirstRoute> {
                val installed = remember(context) { { pkg: String -> context.packageManager.getLaunchIntentForPackage(pkg) != null } }
                val openable = remember(state.apps) { OnboardingRules.appToOpen(state.apps, installed) }
                // A first save (picked here, or shared from the B2 app meanwhile) → the first card, in this step's place.
                LaunchedEffect(state.firstSave?.id) {
                    if (state.firstSave != null) nav.navigate(ObCardRoute) { popUpTo<ObFirstRoute> { inclusive = true } }
                }
                FirstSaveScreen(state, openable, onAction)
            }
            composable<ObCardRoute> {
                BackHandler { onAction(OnboardingAction.OnGoToKept) }
                FirstCardScreen(state, onAction)
            }
            // How Kept uses AI: the real Privacy page.
            composable<PrivacyRoute> {
                PrivacyRoot(
                    onBack = { nav.popBackStack() },
                    onSent = { nav.navigate(SentRoute()) },
                    onUnsupported = { nav.navigate(UnsupportedRoute) },
                    onDeleted = { nav.popBackStack() },
                )
            }
            composable<SentRoute> { SentRoot(onBack = { nav.popBackStack() }) }
            // k-unsupported's choice comes before any question (review-product #1); either answer goes on to B1.
            composable<UnsupportedRoute> {
                UnsupportedRoot(
                    onBack = { nav.popBackStack() },
                    onDone = { nav.navigate(ObTypesRoute) { popUpTo<UnsupportedRoute> { inclusive = true } } },
                )
            }
        }
    }
}
