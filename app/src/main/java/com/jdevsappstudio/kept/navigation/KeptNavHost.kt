package com.jdevsappstudio.kept.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import com.jdevsappstudio.kept.feature.add.AddRoot
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
import com.jdevsappstudio.kept.feature.search.SearchRoot
import com.jdevsappstudio.kept.feature.library.shelves.ShelvesRoot
import kotlin.reflect.KClass

/**
 * The app's one NavHost. Tabs (Home / Search / Ask) keep their own back stacks; the bar also shows on the
 * list screens below Home (k-list, k-shelf, k-collection show it in the doc) and hides on detail and sheets.
 * [openSave]: a save id from the share sheet ('Open it'); consumed once.
 */
@Composable
fun KeptNavHost(openSave: Long?, onSaveOpened: () -> Unit, nav: NavHostController = rememberNavController()) {
    val entry by nav.currentBackStackEntryAsState()
    val destination = entry?.destination

    LaunchedEffect(openSave) {
        if (openSave != null) {
            nav.navigate(SaveDetailRoute(openSave)) { launchSingleTop = true }
            onSaveOpened()
        }
    }

    Scaffold(
        bottomBar = { if (destination.showsBar()) KeptBottomBar(destination, nav) },
        snackbarHost = { KeptSnackbarHost() },
        containerColor = MaterialTheme.colorScheme.background,
    ) { inner ->
        NavHost(nav, startDestination = HomeRoute, modifier = Modifier.padding(bottom = inner.calculateBottomPadding())) {
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
                )
            }
            // Screens below are filled in by the next UI-sweep chunks (H51); until then each is a titled stub.
            composable<SearchRoute> {
                SearchRoot(
                    onBack = { nav.popBackStack() },
                    onOpenSave = { nav.navigate(SaveDetailRoute(it)) },
                    onOpenShelf = { nav.navigate(ShelfRoute(it)) },
                    onOpenNeedsLook = { nav.navigate(NeedLookRoute) },
                    onOpenAsk = { nav.navigate(AskRoute(question = it)) },
                )
            }
            composable<AskRoute> { Stub("Ask", nav, back = false) }
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
            composable<SaveDetailRoute> {
                SaveDetailRoot(
                    onBack = { nav.popBackStack() },
                    onEdit = { nav.navigate(EditSaveRoute(longArrayOf(it))) },
                    onOpenCloudOut = { nav.navigate(CloudOutRoute) },
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
            composable<CloudOutRoute> { Stub("Out of cloud answers", nav) }
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
            composable<SettingsRoute> { Stub("Settings", nav) }
            composable<NeedLookRoute> { Stub("Needs a look", nav) }
        }
    }
}

/** k-newcol hands a new collection's id back to the edit sheet under this key (set on the previous entry). */
const val KEY_CREATED_COLLECTION = "createdCollection"

private val BAR_ROUTES: List<KClass<*>> = listOf(
    HomeRoute::class, SearchRoute::class, AskRoute::class,
    SaveListRoute::class, ShelfRoute::class, ShelvesRoute::class, CollectionRoute::class, ReadingRoute::class,
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

/** Placeholder for a screen not built yet in the sweep. */
@Composable
private fun Stub(title: String, nav: NavHostController, back: Boolean = true) {
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(16.dp)) {
        if (back) KeptIconButton(Icons.AutoMirrored.Outlined.ArrowBack, "Back", onClick = { nav.popBackStack() })
        VSpace(16.dp)
        Text(title, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onBackground)
        Box(Modifier.padding(top = 8.dp)) {
            Text("Coming in the UI sweep.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
