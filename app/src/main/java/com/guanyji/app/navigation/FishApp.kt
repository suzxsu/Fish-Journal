package com.guanyji.app.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavHostController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.guanyji.app.feature.shell.PageShell
import com.guanyji.app.feature.tank.TankDetailScreen
import com.guanyji.app.feature.tank.TankFormScreen
import com.guanyji.app.feature.tank.TankListScreen
import com.guanyji.app.feature.tank.TankQuickFormScreen
import com.guanyji.app.feature.tank.TankRecordsPage
import com.guanyji.app.feature.tank.TankRecordsScreen
import com.guanyji.app.feature.fish.FishListScreen
import com.guanyji.app.feature.fish.FishFormScreen
import com.guanyji.app.feature.fish.FishDetailScreen
import com.guanyji.app.feature.fish.FishRecordsPage
import com.guanyji.app.feature.fish.FishRecordsScreen
import com.guanyji.app.feature.feeding.FeedingScreen
import com.guanyji.app.feature.care.CareScreen
import com.guanyji.app.feature.overview.OverviewScreen
import com.guanyji.app.feature.profile.ProfileScreen
import com.guanyji.app.feature.profile.SettingsScreen
import com.guanyji.app.feature.profile.AboutHelpScreen
import com.guanyji.app.feature.statistics.StatisticsScreen
import com.guanyji.app.data.local.CareTaskType
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton

internal fun navigateToShortcut(navController: NavHostController, route: String) {
    if (!isKnownRoute(route)) {
        navController.navigate("navigation/unknown") { launchSingleTop = true }
        return
    }
    val destination = topLevelOwner(route)
    if (destination == null) navController.navigate(route)
    else navigateToTopLevelRoot(navController, destination, route)
}

private fun isAtTopLevelRoot(route: String?, arguments: android.os.Bundle?, destination: AppDestination): Boolean =
    route == destination.route || (destination == AppDestination.Care && route == "care?view={view}" && arguments?.getString("view") == null)

private fun navigateToTopLevelRoot(navController: NavHostController, destination: AppDestination, route: String = destination.route) {
    navController.navigate(route) {
        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
    val rootPattern = if (destination == AppDestination.Care) "care?view={view}" else destination.route
    if (navController.currentBackStackEntry?.destination?.route != rootPattern &&
        !navController.popBackStack(rootPattern, inclusive = false)
    ) {
        navController.navigate(destination.route) {
            popUpTo(navController.graph.findStartDestination().id)
            launchSingleTop = true
        }
    }
}

private fun NavHostController.navigateBackTo(root: AppDestination) {
    if (!popBackStack()) navigate(root.route) {
        popUpTo(graph.findStartDestination().id) { inclusive = true }
        launchSingleTop = true
    }
}

@Composable
fun FishApp(openCareTaskId: String? = null, onCareTaskOpened: () -> Unit = {}) {
    FishAppContent(rememberNavController(), openCareTaskId, onCareTaskOpened)
}

@Composable
internal fun FishAppContent(navController: NavHostController, openCareTaskId: String? = null, onCareTaskOpened: () -> Unit = {}) {
    var overviewMessage by remember { mutableStateOf<String?>(null) }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    LaunchedEffect(openCareTaskId) {
        if (openCareTaskId != null) navController.navigate(careRoute()) { launchSingleTop = true }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            val active = topLevelOwner(currentRoute.orEmpty())
            if (active != null && showsBottomBar(currentRoute, backStackEntry?.arguments?.getString("action"))) DashboardBottomNavigation(current = active) { destination ->
                if (!isAtTopLevelRoot(currentRoute, backStackEntry?.arguments, destination)) {
                    navigateToTopLevelRoot(navController, destination)
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = AppDestination.Overview.route,
            modifier = Modifier.padding(innerPadding).consumeWindowInsets(innerPadding),
            enterTransition = { fadeIn(tween(180)) },
            exitTransition = { fadeOut(tween(140)) },
            popEnterTransition = { fadeIn(tween(180)) },
            popExitTransition = { fadeOut(tween(140)) },
        ) {
            AppDestination.entries.filter { it != AppDestination.Care }.forEach { destination ->
                composable(destination.route) {
                    if (destination == AppDestination.Fish) {
                        FishListScreen(
                            onCreate = { navController.navigate(fishCreateRoute()) },
                            onOpen = { navController.navigate("fish/detail/$it") },
                            onEdit = { navController.navigate("fish/edit/$it") },
                        )
                    } else if (destination == AppDestination.Tanks) {
                        TankListScreen(
                            onCreate = { navController.navigate("tank/create") },
                            onOpen = { navController.navigate("tank/detail/$it") },
                        )
                    } else if (destination == AppDestination.Profile) ProfileScreen(
                        onStatistics = { navController.navigate("statistics") },
                        onSettings = { section -> navController.navigate("settings?section=${section ?: "units"}") },
                        onAbout = { section -> navController.navigate("about/$section") },
                    )
                    else if (destination == AppDestination.Overview) OverviewScreen(
                        onAddTank = { navController.navigate("tank/create") },
                        onAddFish = { navController.navigate(fishCreateRoute()) },
                        onFeeding = { navController.navigate(feedingRoute()) },
                        onWater = { tankId -> if (tankId == null) navController.navigate(AppDestination.Tanks.route) else navController.navigate("tank/action/$tankId/water") },
                        onTank = { navController.navigate("tank/detail/$it") },
                        onFish = { navController.navigate("fish/detail/$it") },
                        onCare = { taskId -> navController.navigate(careRoute()) },
                        onMessage = { overviewMessage = it },
                        onNavigate = { route -> navigateToShortcut(navController, route) },
                        onRecordFeeding = { tankId -> navController.navigate(feedingRoute(tankId = tankId)) },
                        onRecordWater = { tankId -> navController.navigate("tank/action/$tankId/water") },
                        onRecordGrowth = { fishId -> navController.navigate(fishRecordsRoute(fishId, "growth")) },
                        onOpenRecords = { navController.navigate(careRoute("history")) },
                        onOpenTask = { task ->
                            when (task.type) {
                                CareTaskType.feeding -> navController.navigate(feedingRoute(tankId = task.tankId))
                                CareTaskType.water_change, CareTaskType.water_test, CareTaskType.salinity_test -> if (task.tankId == null) navController.navigate(AppDestination.Tanks.route) else navController.navigate("tank/action/${task.tankId}/water")
                                else -> navController.navigate(careRoute())
                            }
                        },
                    ) else PageShell(destination)
                }
            }
            composable("tank/create") {
                TankFormScreen(null, onBack = { navController.popBackStack() }, onSaved = { navController.navigate("tank/detail/$it") { popUpTo("tank/create") { inclusive = true } } })
            }
            composable("fish/create?tankId={tankId}", arguments = listOf(navArgument("tankId") { type = NavType.StringType; nullable = true; defaultValue = null })) { entry ->
                val tankId = entry.arguments?.getString("tankId")
                FishFormScreen(null, initialTankId = tankId, onBack = { navController.popBackStack() }, onSaved = { id ->
                    val createEntryId = navController.currentBackStackEntry?.destination?.id
                    navController.navigate("fish/detail/$id") {
                        if (createEntryId != null) popUpTo(createEntryId) { inclusive = true }
                        launchSingleTop = true
                    }
                })
            }
            composable("fish/edit/{fishId}") { entry ->
                val fishId = entry.arguments?.getString("fishId") ?: return@composable
                FishFormScreen(fishId, onBack = { navController.popBackStack() }, onSaved = { navController.popBackStack() })
            }
            composable("fish/detail/{fishId}") { entry ->
                val fishId = entry.arguments?.getString("fishId") ?: return@composable
                FishDetailScreen(
                    fishId = fishId,
                    onBack = { navController.navigateBackTo(AppDestination.Fish) },
                    onEdit = { navController.navigate("fish/edit/$fishId") },
                    onOpenRecords = { section -> navController.navigate(fishRecordsRoute(fishId, section)) },
                    onOpenTank = { tankId -> navController.navigate("tank/detail/$tankId") },
                    onRecordWater = { tankId -> navController.navigate("tank/action/$tankId/water") },
                )
            }
            composable("fish/records/{fishId}/{section}") { entry ->
                val fishId = entry.arguments?.getString("fishId") ?: return@composable
                val section = entry.arguments?.getString("section") ?: return@composable
                if (section !in setOf("growth", "feeding", "timeline")) InvalidDestinationScreen("未知的鱼只记录类型", onBack = { navController.popBackStack() })
                else FishRecordsPage(fishId, section, onBack = { navController.popBackStack() })
            }
            composable("tank/edit/{tankId}") { entry ->
                val tankId = entry.arguments?.getString("tankId") ?: return@composable
                TankFormScreen(tankId, onBack = { navController.popBackStack() }, onSaved = { navController.popBackStack() })
            }
            composable("tank/detail/{tankId}") { entry ->
                val tankId = entry.arguments?.getString("tankId") ?: return@composable
                TankDetailScreen(
                    tankId = tankId,
                    onBack = { navController.navigateBackTo(AppDestination.Tanks) },
                    onEdit = { navController.navigate("tank/edit/$tankId") },
                    onOpenFish = { fishId -> navController.navigate("fish/detail/$fishId") },
                    onOpenRecords = { section -> navController.navigate("tank/records/$tankId/$section") },
                    onQuickAction = { action, id ->
                        if (action == "fish") navController.navigate(fishCreateRoute(id))
                        else if (action.startsWith("change/")) navController.navigate("tank/action/$id/change/${action.substringAfter('/')}")
                        else navController.navigate("tank/action/$id/$action")
                    },
                )
            }
            composable("tank/records/{tankId}/{section}") { entry ->
                val tankId = entry.arguments?.getString("tankId") ?: return@composable
                val section = entry.arguments?.getString("section") ?: return@composable
                if (section !in setOf("summary", "water", "fish", "care", "activity")) {
                    InvalidDestinationScreen("未知的鱼缸记录类型", onBack = { navController.popBackStack() })
                } else {
                    val initialWaterTab = navController.previousBackStackEntry?.savedStateHandle?.get<String>("waterInitialTab")
                    if (initialWaterTab != null) navController.previousBackStackEntry?.savedStateHandle?.remove<String>("waterInitialTab")
                    TankRecordsPage(
                        tankId = tankId,
                        section = section,
                        onBack = { navController.popBackStack() },
                        onEdit = { navController.navigate("tank/edit/$tankId") },
                        onOpenFish = { fishId -> navController.navigate("fish/detail/$fishId") },
                        onEditFish = { fishId -> navController.navigate("fish/edit/$fishId") },
                        onRecordWaterTemplate = { testId ->
                            navController.currentBackStackEntry?.savedStateHandle?.set("waterTemplateId", testId)
                            navController.navigate("tank/action/$tankId/water")
                        },
                        onEditWaterChange = { changeId -> navController.navigate("tank/action/$tankId/change/$changeId") },
                        initialWaterTab = initialWaterTab,
                        onQuickAction = { action, id ->
                            if (action == "fish") navController.navigate(fishCreateRoute(id))
                            else if (action.startsWith("change/")) navController.navigate("tank/action/$id/change/${action.substringAfter('/')}")
                            else navController.navigate("tank/action/$id/$action")
                        },
                    )
                }
            }
            composable("tank/action/{tankId}/change/{changeId}") { entry ->
                val tankId = entry.arguments?.getString("tankId").orEmpty()
                val changeId = entry.arguments?.getString("changeId").orEmpty()
                TankQuickFormScreen(tankId, "change", editId = changeId, onBack = { navController.popBackStack() })
            }
            composable("tank/action/{tankId}/{action}") { entry ->
                val tankId = entry.arguments?.getString("tankId").orEmpty()
                val action = entry.arguments?.getString("action").orEmpty()
                if (action !in setOf("feeding", "water", "task", "fish", "change")) {
                    InvalidDestinationScreen("未知的鱼缸操作", onBack = { navController.popBackStack() })
                } else if (action == "feeding") FeedingScreen(tankId, null, onBack = { navController.popBackStack() }) else {
                        val templateId = navController.previousBackStackEntry?.savedStateHandle?.get<String>("waterTemplateId")
                        if (templateId != null) navController.previousBackStackEntry?.savedStateHandle?.remove<String>("waterTemplateId")
                        TankQuickFormScreen(tankId, action, onBack = { navController.popBackStack() }, onOpenHistory = { navController.navigate("tank/records/$tankId/water") }, templateTestId = templateId)
                    }
            }
            composable("feeding?tankId={tankId}&fishId={fishId}", arguments = listOf(
                navArgument("tankId") { type = NavType.StringType; nullable = true; defaultValue = null },
                navArgument("fishId") { type = NavType.StringType; nullable = true; defaultValue = null },
            )) { entry ->
                val selectedTank = entry.arguments?.getString("tankId")
                val selectedFish = entry.arguments?.getString("fishId")
                FeedingScreen(selectedTank, selectedFish, onBack = { navController.popBackStack() })
            }
            composable("statistics") {
                StatisticsScreen(
                    onBack = { navController.popBackStack() },
                    onAddRecord = { action, tankId ->
                        if (action == "growth") navController.navigate(AppDestination.Fish.route)
                        else if (tankId == null) navController.navigate(AppDestination.Tanks.route)
                        else if (action == "feeding") navController.navigate("tank/action/$tankId/feeding")
                        else navController.navigate("tank/action/$tankId/$action")
                    },
                    onOpenRecords = { kind, tankId ->
                        if (tankId == null) navController.navigate(AppDestination.Tanks.route)
                        else if (kind == "feeding") navController.navigate("tank/records/$tankId/activity")
                        else {
                            if (kind == "change") navController.currentBackStackEntry?.savedStateHandle?.set("waterInitialTab", "Change")
                            navController.navigate("tank/records/$tankId/water")
                        }
                    },
                )
            }
            composable("settings?section={section}", arguments = listOf(navArgument("section") { type = NavType.StringType; nullable = true; defaultValue = null })) { entry ->
                SettingsScreen(onBack = { navController.popBackStack() }, onAbout = { navController.navigate("about/help") }, initialSection = entry.arguments?.getString("section"))
            }
            composable("care?view={view}", arguments = listOf(navArgument("view") { type = NavType.StringType; nullable = true; defaultValue = null })) { entry ->
                val view = entry.arguments?.getString("view")
                if (view != null && view != "history") InvalidDestinationScreen("未知的养护视图", onBack = { if (!navController.popBackStack()) navController.navigate(AppDestination.Overview.route) })
                else CareScreen(openCareTaskId, onCareTaskOpened, initialView = view)
            }
            composable("about") { AboutHelpScreen(onBack = { navController.popBackStack() }) }
            composable("about/{section}") { entry -> AboutHelpScreen(onBack = { navController.popBackStack() }, section = entry.arguments?.getString("section") ?: "about") }
            composable("navigation/unknown") { InvalidDestinationScreen("无法识别这个页面", onBack = { navController.navigateBackTo(AppDestination.Overview) }) }
        }
    }
    overviewMessage?.let { message -> AlertDialog(onDismissRequest = { overviewMessage = null }, text = { Text(message) }, confirmButton = { TextButton(onClick = { overviewMessage = null }) { Text("知道了") } }) }
}

@Composable
private fun InvalidDestinationScreen(message: String, onBack: () -> Unit) {
    androidx.compose.foundation.layout.Column(
        modifier = Modifier.padding(24.dp),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp),
    ) {
        Text(message)
        TextButton(onClick = onBack) { Text("返回") }
    }
}
