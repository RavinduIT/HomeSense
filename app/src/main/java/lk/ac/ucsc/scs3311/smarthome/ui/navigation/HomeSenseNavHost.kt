package lk.ac.ucsc.scs3311.smarthome.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import lk.ac.ucsc.scs3311.smarthome.ui.device.DeviceControlSheet
import lk.ac.ucsc.scs3311.smarthome.ui.floors.FloorsScreen
import lk.ac.ucsc.scs3311.smarthome.ui.plan.PlanScreen

/** Route names in one place, so a typo is a compile error rather than a blank screen. */
object Routes {
    const val FLOORS = "floors"
    const val PLAN = "plan/{floorId}"

    fun plan(floorId: String) = "plan/$floorId"

    const val ARG_FLOOR_ID = "floorId"
}

@Composable
fun HomeSenseNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
) {
    NavHost(
        navController = navController,
        startDestination = Routes.FLOORS,
        modifier = modifier,
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
    }
}
