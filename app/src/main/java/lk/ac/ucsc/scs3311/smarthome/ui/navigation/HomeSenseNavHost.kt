package lk.ac.ucsc.scs3311.smarthome.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import lk.ac.ucsc.scs3311.smarthome.ui.account.AccountScreen
import lk.ac.ucsc.scs3311.smarthome.ui.alerts.AlertsScreen
import lk.ac.ucsc.scs3311.smarthome.ui.alerts.AlertsViewModel
import lk.ac.ucsc.scs3311.smarthome.ui.device.DeviceControlSheet
import lk.ac.ucsc.scs3311.smarthome.ui.floors.FloorsScreen
import lk.ac.ucsc.scs3311.smarthome.ui.plan.PlanScreen
import lk.ac.ucsc.scs3311.smarthome.ui.report.ReportScreen

/** Route names in one place, so a typo is a compile error rather than a blank screen. */
object Routes {
    const val FLOORS = "floors"
    const val PLAN = "plan/{floorId}"
    const val ALERTS = "alerts"
    const val REPORT = "report"
    const val ACCOUNT = "account"

    fun plan(floorId: String) = "plan/$floorId"

    const val ARG_FLOOR_ID = "floorId"
}

private data class TopLevelDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

private val topLevelDestinations = listOf(
    TopLevelDestination(Routes.FLOORS, "Floors", Icons.Default.Layers),
    TopLevelDestination(Routes.ALERTS, "Alerts", Icons.Default.Notifications),
    TopLevelDestination(Routes.REPORT, "Usage", Icons.Default.Insights),
    TopLevelDestination(Routes.ACCOUNT, "Account", Icons.Default.AccountCircle),
)

@Composable
fun HomeSenseNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    alertsViewModel: AlertsViewModel = viewModel(factory = AlertsViewModel.Factory),
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val alertsState by alertsViewModel.uiState.collectAsStateWithLifecycle()

    // The plan screen is a detail view: it fills the window and keeps its own
    // back arrow, so the bottom bar would only crowd the floor plan.
    val showBottomBar = topLevelDestinations.any { destination ->
        currentDestination?.hierarchy?.any { it.route == destination.route } == true
    }

    Scaffold(
        modifier = modifier,
        bottomBar = {
            if (!showBottomBar) return@Scaffold

            NavigationBar {
                topLevelDestinations.forEach { destination ->
                    val selected =
                        currentDestination?.hierarchy?.any { it.route == destination.route } == true

                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(destination.route) {
                                // Standard bottom-nav behaviour: one entry per
                                // tab, state preserved when switching back.
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = {
                            if (destination.route == Routes.ALERTS &&
                                alertsState.unacknowledgedCount > 0
                            ) {
                                BadgedBox(
                                    badge = { Badge { Text("${alertsState.unacknowledgedCount}") } },
                                ) {
                                    Icon(destination.icon, contentDescription = destination.label)
                                }
                            } else {
                                Icon(destination.icon, contentDescription = destination.label)
                            }
                        },
                        label = { Text(destination.label) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.FLOORS,
            modifier = Modifier.padding(padding),
        ) {
            composable(Routes.FLOORS) {
                FloorsScreen(
                    onOpenFloor = { floorId -> navController.navigate(Routes.plan(floorId)) },
                )
            }

            composable(
                route = Routes.PLAN,
                arguments = listOf(navArgument(Routes.ARG_FLOOR_ID) { type = NavType.StringType }),
            ) { entry ->
                val floorId = entry.arguments?.getString(Routes.ARG_FLOOR_ID).orEmpty()
                PlanScreen(
                    floorId = floorId,
                    onBack = { navController.popBackStack() },
                    deviceSheet = { deviceId, onDismiss ->
                        DeviceControlSheet(deviceId = deviceId, onDismiss = onDismiss)
                    },
                )
            }

            composable(Routes.ALERTS) { AlertsScreen(viewModel = alertsViewModel) }

            composable(Routes.REPORT) { ReportScreen() }

            composable(Routes.ACCOUNT) { AccountScreen() }
        }
    }
}
