package com.spotterkanji.app.nav

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.spotterkanji.app.review.ReviewScreen
import com.spotterkanji.app.saved.ListDetailScreen
import com.spotterkanji.app.saved.ListDetailViewModel
import com.spotterkanji.app.saved.SavedScreen
import com.spotterkanji.app.saved.SavedViewModel
import com.spotterkanji.domain.user.SavedListId

/**
 * The app shell: three destinations under one bottom bar (D-36, D-90).
 *
 * ### What back does
 *
 * Nothing here handles the system back gesture, and that is the decision rather
 * than an omission (D-90). Scan is the start destination, so back on it exits
 * the app exactly as Android expects of any start destination. Back from Saved
 * or Review returns to Scan, because [popUpTo] keeps the start destination at
 * the bottom of the stack. There is no back control on the camera to compete
 * with the gesture, which is precisely what D-85 got wrong and D-90 fixed.
 *
 * ### Why the destinations are not `saveState = true`
 *
 * Switching tabs recreates the destination rather than restoring its scroll
 * position. That is fine, and deliberate: all three read from the database
 * through `Flow`s, so what they show is derived, not typed. There is no
 * half-finished input to lose, and restoring a stale scroll offset onto a list
 * that has changed underneath is worse than starting at the top.
 */
@Composable
internal fun SpotterApp(
    scanContent: @Composable (bottomBarHeight: androidx.compose.ui.unit.Dp) -> Unit,
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val route = backStackEntry?.destination?.route
    // List detail is not a bar destination, but it belongs to Saved, so Saved
    // stays lit while it is open. Otherwise opening a list would appear to leave
    // the section it is part of.
    val current = SpotterDestination.forRoute(route)
        ?: SpotterDestination.Saved.takeIf { route?.startsWith(LIST_ROUTE_PREFIX) == true }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        bottomBar = {
            SpotterBottomBar(
                current = current,
                onSelect = { destination -> navController.navigateToTab(destination) },
            )
        },
        // The Scaffold applies no window insets of its own. Every destination
        // wants them differently — the viewfinder reaches the screen edges while
        // Saved must clear the status bar — so each one is handed the padding and
        // decides. A Scaffold-wide inset would letterbox the camera.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = SpotterDestination.start.route,
            modifier = Modifier.fillMaxSize(),
        ) {
            composable(SpotterDestination.Scan.route) {
                // The camera gets only the bar's height, not the full padding:
                // it draws under the status bar on purpose (D-33) and applies
                // insets to its controls rather than to the photograph.
                scanContent(innerPadding.calculateBottomPadding())
            }

            composable(SpotterDestination.Saved.route) {
                val viewModel: SavedViewModel = viewModel()
                val state by viewModel.state.collectAsStateWithLifecycle()
                val dialogOpen by viewModel.newListDialogOpen.collectAsStateWithLifecycle()
                SavedScreen(
                    state = state,
                    newListDialogOpen = dialogOpen,
                    onNewListRequested = viewModel::onNewListRequested,
                    onNewListDismissed = viewModel::onNewListDismissed,
                    onNewListConfirmed = viewModel::onNewListConfirmed,
                    onRenamed = viewModel::onRenamed,
                    onDeleted = viewModel::onDeleted,
                    onListOpened = { id -> navController.navigate(listRoute(id)) },
                    contentPadding = innerPadding.withSystemBars(),
                )
            }

            composable(SpotterDestination.Review.route) {
                ReviewScreen(contentPadding = innerPadding.withSystemBars())
            }

            composable(LIST_ROUTE) { entry ->
                val id = entry.arguments?.getString(LIST_ARG).orEmpty()
                val viewModel: ListDetailViewModel = viewModel()
                LaunchedEffect(id) { viewModel.open(SavedListId(id)) }
                val state by viewModel.state.collectAsStateWithLifecycle()
                ListDetailScreen(
                    // Falls back to an empty title rather than crashing when the
                    // list has been deleted from under this screen.
                    listName = state.name.orEmpty(),
                    words = state.words,
                    onBack = { navController.popBackStack() },
                    onRemove = viewModel::onRemove,
                    onUndoRemove = viewModel::onUndoRemove,
                    contentPadding = innerPadding.withSystemBars(),
                )
            }
        }
    }
}

/**
 * Moving between tabs, rather than stacking them.
 *
 * Without [popUpTo], tapping Scan → Saved → Scan → Saved leaves four entries on
 * the stack and takes four back presses to leave the app. Popping to the start
 * destination keeps the stack one deep, so back from any tab goes to Scan and
 * back from Scan exits — the behaviour D-90 describes.
 *
 * [launchSingleTop] stops a second tap on the current tab pushing a duplicate.
 */
private fun NavHostController.navigateToTab(destination: SpotterDestination) {
    navigate(destination.route) {
        popUpTo(SpotterDestination.start.route) { inclusive = false }
        launchSingleTop = true
    }
}

/**
 * Adds the status-bar inset to the Scaffold's padding.
 *
 * The Scaffold is told to apply no insets so the camera can reach the screen
 * edges, which means every other destination has to ask for them back. Content
 * would otherwise start underneath the clock.
 */
@Composable
private fun PaddingValues.withSystemBars(): PaddingValues {
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    return PaddingValues(
        top = top,
        bottom = calculateBottomPadding(),
    )
}

private const val LIST_ARG = "listId"
private const val LIST_ROUTE_PREFIX = "list/"
private const val LIST_ROUTE = "$LIST_ROUTE_PREFIX{$LIST_ARG}"

private fun listRoute(id: SavedListId) = "$LIST_ROUTE_PREFIX${id.value}"
