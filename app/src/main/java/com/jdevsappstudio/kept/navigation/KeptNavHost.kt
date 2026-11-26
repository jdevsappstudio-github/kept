package com.jdevsappstudio.kept.navigation

import com.jdevsappstudio.kept.feature.settings.AboutScreen
import com.jdevsappstudio.kept.feature.settings.AppearanceRoot
import com.jdevsappstudio.kept.feature.settings.LicencesScreen
import com.jdevsappstudio.kept.feature.settings.PrivacyRoot
import com.jdevsappstudio.kept.feature.settings.SettingsRoot
import com.jdevsappstudio.kept.feature.settings.UnsupportedRoot
import com.jdevsappstudio.kept.feature.onboarding.ModelRoot
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.dialog
import androidx.compose.ui.window.DialogProperties
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavOptionsBuilder
import androidx.navigation.toRoute
import com.jdevsappstudio.kept.feature.quota.CloudOutRoot
import com.jdevsappstudio.kept.feature.add.AddRoot
import com.jdevsappstudio.kept.feature.ask.AskRoot
import com.jdevsappstudio.kept.feature.add.HowToRoot
import com.jdevsappstudio.kept.feature.add.ReadingRoot
import com.jdevsappstudio.kept.core.designsystem.KeptIconButton
import com.jdevsappstudio.kept.core.designsystem.VSpace
import com.jdevsappstudio.kept.core.presentation.KeptSnackbarHost
import com.jdevsappstudio.kept.feature.collections.CollectionNameRoot
import com.jdevsappstudio.kept.feature.collections.CollectionRoot
import com.jdevsappstudio.kept.feature.home.HomeRoot
import com.jdevsappstudio.kept.feature.library.detail.SaveDetailRoot
import com.jdevsappstudio.kept.feature.library.edit.EditSaveRoot
import com.jdevsappstudio.kept.feature.library.list.SaveListRoot
import com.jdevsappstudio.kept.feature.library.shelf.ShelfRoot
import com.jdevsappstudio.kept.feature.needlook.NeedLookRoot
import com.jdevsappstudio.kept.feature.search.SearchRoot
import com.jdevsappstudio.kept.feature.sent.SentRoot
import com.jdevsappstudio.kept.feature.library.shelves.ShelvesRoot
import kotlin.reflect.KClass

/**
 * The app's one NavHost. Tabs (Home / Search / Ask) keep their own back stacks; the bar also shows on the
 * list screens below Home (k-list, k-shelf, k-collection show it in the doc) and hides on detail and sheets.
 * [openSave]: a save id from the share sheet ('Open it'); consumed once.
 */
@Composable
fun KeptNavHost(openSave: OpenRequest?, onSaveOpened: () -> Unit, nav: NavHostController = rememberNavController()) {
    val entry by nav.currentBackStackEntryAsState()
    val destination = entry?.destination

    LaunchedEffect(openSave) {
        if (openSave != null) {
            nav.navigate(SaveDetailRoute(openSave.saveId)) { launchSingleTop = true }
            if (openSave.edit) nav.navigate(EditSaveRoute(longArrayOf(openSave.saveId)))
            onSaveOpened()
        }
    }

    Scaffold(
        bottomBar = { if (destination.showsBar()) KeptBottomBar(destination, nav) },
        snackbarHost = { KeptSnackbarHost() },
        containerColor = MaterialTheme.colorScheme.background,
    ) { inner ->
        // Bottom only is consumed, so a screen's imePadding() lifts its input just above the keyboard, not bar-height above it.
        val bottom = PaddingValues(bottom = inner.calculateBottomPadding())
        NavHost(nav, startDestination = HomeRoute, modifier = Modifier.padding(bottom).consumeWindowInsets(bottom)) {
            composable<HomeRoute> {
                HomeRoot(
                    onOpenList = { type -> nav.navigate(SaveListRoute(type?.wire)) },
                    onOpenSave = { nav.navigate(SaveDetailRoute(it)) },
                    onOpenShelf = { nav.navigate(ShelfRoute(it)) },
                    onOpenShelves = { nav.navigate(ShelvesRoute) },
                    onOpenCollection = { nav.navigate(CollectionRoute(it)) },
                    onNewCollection = { nav.navigate(CollectionNameRoute()) },
                    onAdd = { nav.navigate(AddRoute()) },
                    onPick = { nav.navigate(AddRoute(pick = true)) },
                    onSettings = { nav.navigate(SettingsRoute) },
                    onNeedLook = { nav.navigate(NeedLookRoute) },
                    onModel = { nav.navigate(ModelRoute) },
                    onUnsupported = { nav.navigate(UnsupportedRoute) },
                )
            }
            composable<SearchRoute> {
                SearchRoot(
                    onBack = { nav.popBackStack() },
                    onOpenSave = { nav.navigate(SaveDetailRoute(it)) },
                    onOpenShelf = { nav.navigate(ShelfRoute(it)) },
                    onOpenNeedsLook = { nav.navigate(NeedLookRoute) },
                    onOpenAsk = { nav.navigate(AskRoute(question = it)) },
                )
            }
            composable<AskRoute> { entry ->
                val granted by entry.savedStateHandle.getStateFlow(KEY_ANSWERS_GRANTED, false).collectAsState()
                AskRoot(
                    onOpenSave = { nav.navigate(SaveDetailRoute(it)) },
                    onOpenSent = { nav.navigate(SentRoute(it)) },
                    onOpenCloudOut = { nav.navigate(CloudOutRoute()) },
                    answersGranted = granted,
                    onGrantConsumed = { entry.savedStateHandle[KEY_ANSWERS_GRANTED] = false },
                    onClose = { nav.popBackStack() },
                )
            }
            composable<SaveListRoute> {
                SaveListRoot(
                    onBack = { nav.popBackStack() },
                    onSearch = { type -> nav.navigate(SearchRoute(type = type?.wire)) },
                    onOpenSave = { nav.navigate(SaveDetailRoute(it)) },
                    onOpenEdit = { nav.navigate(EditSaveRoute(it.toLongArray())) },
                )
            }
            composable<ShelfRoute> {
                ShelfRoot(
                    onBack = { nav.popBackStack() },
                    onSearch = { q -> nav.navigate(SearchRoute(q = q)) },
                    onOpenSave = { nav.navigate(SaveDetailRoute(it)) },
                )
            }
            composable<ShelvesRoute> {
                ShelvesRoot(onBack = { nav.popBackStack() }, onOpenShelf = { nav.navigate(ShelfRoute(it)) })
            }
            composable<SaveDetailRoute> { entry ->
                val granted by entry.savedStateHandle.getStateFlow(KEY_ANSWERS_GRANTED, false).collectAsState()
                SaveDetailRoot(
                    onBack = { nav.popBackStack() },
                    onEdit = { nav.navigate(EditSaveRoute(longArrayOf(it))) },
                    onOpenCloudOut = { nav.navigate(CloudOutRoute()) },
                    onOpenSent = { nav.navigate(SentRoute(it)) },
                    answersGranted = granted,
                    onGrantConsumed = { entry.savedStateHandle[KEY_ANSWERS_GRANTED] = false },
                )
            }
            // k-organize: a sheet over the screen it was opened from, so it lives in a dialog destination.
            dialog<EditSaveRoute>(
                dialogProperties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
            ) { entry ->
                val created by entry.savedStateHandle.getStateFlow<Long?>(KEY_CREATED_COLLECTION, null).collectAsState()
                EditSaveRoot(
                    onClose = { nav.popBackStack() },
                    onNewCollection = { nav.navigate(CollectionNameRoute()) },
                    createdCollection = created,
                    onCreatedConsumed = { entry.savedStateHandle[KEY_CREATED_COLLECTION] = null },
                )
            }
            // k-cloud-out: a sheet over Ask / a save's detail. After a paid-out ad the caller's held question or fill-in goes.
            dialog<CloudOutRoute>(
                dialogProperties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
            ) {
                CloudOutRoot(
                    onGranted = {
                        nav.previousBackStackEntry?.savedStateHandle?.set(KEY_ANSWERS_GRANTED, true)
                        nav.popBackStack()
                    },
                    onClose = { nav.popBackStack() },
                )
            }
            composable<CollectionRoute> { entry ->
                val id = entry.toRoute<CollectionRoute>().collectionId
                CollectionRoot(
                    collectionId = id,
                    onBack = { nav.popBackStack() },
                    onOpenSave = { nav.navigate(SaveDetailRoute(it)) },
                    onAddSaves = { nav.navigate(SearchRoute(pickInto = it)) },
                    onRename = { nav.navigate(CollectionNameRoute(renameCollectionId = it)) },
                )
            }
            // k-newcol: a sheet over home, the edit sheet or a collection.
            dialog<CollectionNameRoute>(
                dialogProperties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
            ) {
                CollectionNameRoot(
                    onClose = { nav.popBackStack() },
                    onCreated = { id ->
                        val caller = nav.previousBackStackEntry
                        if (caller?.destination?.hasRoute<EditSaveRoute>() == true) {
                            // The edit sheet ticks it.
                            caller.savedStateHandle[KEY_CREATED_COLLECTION] = id
                            nav.popBackStack()
                        } else {
                            // Home / onboarding: open the new, empty collection in the sheet's place.
                            nav.navigate(CollectionRoute(id)) { popUpTo<CollectionNameRoute> { inclusive = true } }
                        }
                    },
                )
            }
            // k-addmenu: a sheet over Home, so a dialog destination; whatever it opens replaces it on the stack.
            dialog<AddRoute>(
                dialogProperties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
            ) { entry ->
                val replace: NavOptionsBuilder.() -> Unit = { popUpTo<AddRoute> { inclusive = true } }
                AddRoot(
                    pickNow = entry.toRoute<AddRoute>().pick,
                    onClose = { nav.popBackStack() },
                    onOpenReading = { nav.navigate(ReadingRoute(it), replace) },
                    onOpenSave = { nav.navigate(SaveDetailRoute(it), replace) },
                    onOpenHowTo = { nav.navigate(HowToRoute, replace) },
                )
            }
            composable<ReadingRoute> {
                ReadingRoot(
                    onBack = { nav.popBackStack() },
                    onOpenSave = { nav.navigate(SaveDetailRoute(it)) },
                    onOpenEdit = { nav.navigate(EditSaveRoute(longArrayOf(it))) },
                    onOpenNeedLook = { nav.navigate(NeedLookRoute) },
                )
            }
            composable<HowToRoute> { HowToRoot(onBack = { nav.popBackStack() }) }
            composable<SettingsRoute> {
                SettingsRoot(
                    onBack = { nav.popBackStack() },
                    onAppearance = { nav.navigate(AppearanceRoute) },
                    onPrivacy = { nav.navigate(PrivacyRoute) },
                    onAbout = { nav.navigate(AboutRoute) },
                )
            }
            composable<AppearanceRoute> { AppearanceRoot(onBack = { nav.popBackStack() }) }
            composable<PrivacyRoute> {
                PrivacyRoot(
                    onBack = { nav.popBackStack() },
                    onSent = { nav.navigate(SentRoute()) },
                    onUnsupported = { nav.navigate(UnsupportedRoute) },
                    // H69: after Delete everything, the empty Home (its snackbar says so).
                    onDeleted = { nav.popBackStack(HomeRoute, inclusive = false) },
                    onModel = { nav.navigate(ModelRoute) },
                )
            }
            composable<UnsupportedRoute> { UnsupportedRoot(onBack = { nav.popBackStack() }) }
            composable<ModelRoute> {
                ModelRoot(
                    onBack = { nav.popBackStack() },
                    onReadWithCloud = { nav.navigate(UnsupportedRoute) { popUpTo<ModelRoute> { inclusive = true } } },
                )
            }
            composable<AboutRoute> { AboutScreen(onBack = { nav.popBackStack() }, onLicences = { nav.navigate(LicencesRoute) }) }
            composable<LicencesRoute> { LicencesScreen(onBack = { nav.popBackStack() }) }
            composable<NeedLookRoute> {
                NeedLookRoot(
                    onBack = { nav.popBackStack() },
                    onOpenSave = { nav.navigate(SaveDetailRoute(it)) },
                    onByHand = { nav.navigate(EditSaveRoute(longArrayOf(it))) },
                )
            }
            composable<SentRoute> { SentRoot(onBack = { nav.popBackStack() }) }
        }
    }
}

/** k-cloud-out tells Ask / a save's detail that an ad paid out, so the held question or fill-in goes (previous entry). */
const val KEY_ANSWERS_GRANTED = "answersGranted"

/** k-newcol hands a new collection's id back to the edit sheet under this key (set on the previous entry). */
const val KEY_CREATED_COLLECTION = "createdCollection"

private val BAR_ROUTES: List<KClass<*>> = listOf(
    HomeRoute::class, SearchRoute::class, AskRoute::class,
    SaveListRoute::class, ShelfRoute::class, ShelvesRoute::class, CollectionRoute::class, ReadingRoute::class, NeedLookRoute::class,
)

private fun NavDestination?.showsBar(): Boolean = this != null && BAR_ROUTES.any { hasRoute(it) }

private data class Tab(val label: String, val icon: ImageVector, val route: Any, val type: KClass<*>)

private val TABS = listOf(
    Tab("Home", Icons.Outlined.Home, HomeRoute, HomeRoute::class),
    Tab("Search", Icons.Outlined.Search, SearchRoute(), SearchRoute::class),
    Tab("Ask", Icons.Outlined.ChatBubbleOutline, AskRoute(), AskRoute::class),
)

@Composable
private fun KeptBottomBar(destination: NavDestination?, nav: NavHostController) {
    // List screens under Home count as the Home tab.
    val current = TABS.firstOrNull { destination?.hasRoute(it.type) == true } ?: TABS[0]
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
        TABS.forEach { tab ->
            NavigationBarItem(
                selected = tab == current,
                onClick = {
                    if (tab == current && tab.type == HomeRoute::class) {
                        nav.popBackStack(HomeRoute, inclusive = false)
                    } else {
                        nav.navigate(tab.route) {
                            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                },
                icon = { Icon(tab.icon, contentDescription = null) },
                label = { Text(tab.label, style = MaterialTheme.typography.labelMedium) },
                colors = NavigationBarItemDefaults.colors(
                    indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        }
    }
}

