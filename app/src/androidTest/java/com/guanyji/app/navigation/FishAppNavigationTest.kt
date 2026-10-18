package com.guanyji.app.navigation

import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.espresso.Espresso
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FishAppNavigationTest {
    @get:Rule
    val composeRule = createComposeRule()
    private lateinit var navController: androidx.navigation.NavHostController

    private fun launchAt(route: String) {
        composeRule.setContent {
            val controller = androidx.navigation.compose.rememberNavController()
            SideEffect { navController = controller }
            FishAppContent(controller)
        }
        composeRule.waitForIdle()
        composeRule.runOnIdle { navController.navigate(route) }
    }

    @Test
    fun appOpensOnOverviewWithoutChangingTabs() {
        composeRule.setContent { FishApp() }
        composeRule.onNodeWithTag("destination-overview").assertIsSelected()
        composeRule.onNodeWithTag("dashboard-title").assertExists()
    }

    @Test
    fun switchingTabsShowsRealEmptyStatesAndReturningKeepsOverviewAvailable() {
        composeRule.setContent { FishApp() }
        composeRule.onNodeWithTag("destination-fish").performClick()
        composeRule.onNodeWithTag("destination-fish").assertIsSelected()
        composeRule.onNodeWithTag("fish-list-screen").assertExists()

        composeRule.onNodeWithTag("destination-care").performClick()
        composeRule.onNodeWithTag("destination-care").assertIsSelected()
        composeRule.onNodeWithText("今日养护").assertExists()

        composeRule.onNodeWithTag("destination-tanks").performClick()
        composeRule.onNodeWithTag("destination-tanks").assertIsSelected()
        composeRule.onNodeWithTag("tank-list-screen").assertExists()

        composeRule.onNodeWithTag("destination-profile").performClick()
        composeRule.onNodeWithTag("destination-profile").assertIsSelected()
        composeRule.onNodeWithText("展示单位").assertExists()

        composeRule.onNodeWithTag("destination-overview").performClick()
        composeRule.onNodeWithTag("destination-overview").assertIsSelected()

        repeat(6) {
            composeRule.onNodeWithTag("destination-tanks").performClick()
            composeRule.onNodeWithTag("destination-tanks").assertIsSelected()
            composeRule.onNodeWithTag("destination-overview").performClick()
            composeRule.onNodeWithTag("destination-overview").assertIsSelected()
        }
    }

    @Test
    fun topLevelNavigationAlwaysReturnsFromShortcutAndDetailRoutes() {
        composeRule.setContent { FishApp() }
        val routes = listOf("overview", "fish", "tanks", "care", "profile")
        repeat(6) {
            routes.forEach { route ->
                composeRule.onNodeWithTag("destination-$route").performClick()
                composeRule.onNodeWithTag("destination-$route").assertIsSelected()
                when (route) {
                    "overview" -> composeRule.onNodeWithTag("dashboard-title").assertExists()
                    "fish" -> composeRule.onNodeWithTag("fish-list-screen").assertExists()
                    "tanks" -> composeRule.onNodeWithTag("tank-list-screen").assertExists()
                    "care" -> composeRule.onNodeWithText("今日养护").assertExists()
                    "profile" -> composeRule.onNodeWithText("展示单位").assertExists()
                }
            }
        }

        // Overview's tank shortcut is a top-level route; selecting Overview must return to its root.
        composeRule.onNodeWithTag("destination-overview").performClick()
        composeRule.onNodeWithTag("destination-tanks").performClick()
        composeRule.onNodeWithTag("destination-tanks").assertIsSelected()
        composeRule.onNodeWithTag("destination-overview").performClick()
        composeRule.onNodeWithTag("destination-overview").assertIsSelected()

        // Check the remaining top-level entries from the shortcut destination.
        composeRule.onNodeWithTag("destination-fish").performClick()
        composeRule.onNodeWithTag("destination-fish").assertIsSelected()
        composeRule.onNodeWithTag("destination-care").performClick()
        composeRule.onNodeWithTag("destination-care").assertIsSelected()
        composeRule.onNodeWithTag("destination-profile").performClick()
        composeRule.onNodeWithTag("destination-profile").assertIsSelected()
        composeRule.onNodeWithTag("destination-overview").performClick()
        composeRule.onNodeWithTag("destination-overview").assertIsSelected()
        composeRule.onNodeWithTag("dashboard-title").assertExists()
    }

    @Test
    fun overviewTankShortcutOpensTheTankRootAndBottomOverviewReturnsHome() {
        composeRule.setContent { FishApp() }
        composeRule.onNodeWithText("鱼缸管理").performClick()
        composeRule.onNodeWithTag("tank-list-screen").assertExists()
        composeRule.onNodeWithTag("destination-overview").performClick()
        composeRule.onNodeWithTag("dashboard-title").assertExists()

    }

    @Test
    fun feedingIsStandaloneAndDoesNotPretendOverviewIsSelected() {
        composeRule.setContent { FishApp() }
        composeRule.onNodeWithText("喂食记录").performClick()
        composeRule.onNodeWithTag("feeding-screen-title").assertExists()
        composeRule.onNodeWithTag("destination-overview").assertDoesNotExist()
        composeRule.onNodeWithTag("destination-fish").assertDoesNotExist()
        composeRule.onNodeWithTag("destination-tanks").assertDoesNotExist()
        composeRule.onNodeWithTag("destination-care").assertDoesNotExist()
        composeRule.onNodeWithTag("destination-profile").assertDoesNotExist()
    }

    @Test
    fun invalidFishAndTankIdsShowRecoverableNotFoundContent() {
        launchAt("fish/detail/missing-navigation-test-id")
        composeRule.onNodeWithTag("fish-not-found").assertExists()
        composeRule.onNodeWithTag("destination-fish").assertDoesNotExist()
        composeRule.onNodeWithText("返回").performClick()
        composeRule.onNodeWithTag("destination-overview").assertIsSelected()
        composeRule.onNodeWithTag("destination-fish").performClick()
        composeRule.onNodeWithTag("fish-list-screen").assertExists()
        composeRule.onNodeWithTag("destination-fish").assertIsSelected()

        composeRule.runOnIdle { navController.navigate("tank/detail/missing-navigation-test-id") }
        composeRule.onNodeWithTag("tank-not-found").assertExists()
        composeRule.onNodeWithTag("destination-tanks").assertDoesNotExist()
        composeRule.onNodeWithText("返回").performClick()
        composeRule.onNodeWithTag("destination-overview").assertIsSelected()
        composeRule.onNodeWithTag("destination-tanks").performClick()
        composeRule.onNodeWithTag("tank-list-screen").assertExists()
        composeRule.onNodeWithTag("destination-tanks").assertIsSelected()
    }

    @Test
    fun invalidTankIdHidesBottomBarAndReturnsToTankList() {
        launchAt("tank/detail/missing-navigation-test-id")
        composeRule.onNodeWithTag("tank-not-found").assertExists()
        composeRule.onNodeWithTag("destination-tanks").assertDoesNotExist()
        composeRule.onNodeWithText("返回").performClick()
        composeRule.onNodeWithTag("tank-list-screen").assertExists()
        composeRule.onNodeWithTag("destination-tanks").assertIsSelected()
    }

    @Test
    fun recentRecordsRouteShowsCareHistory() {
        launchAt(careRoute("history"))
        composeRule.onNodeWithTag("care-history-title").assertExists()
        composeRule.onNodeWithTag("destination-care").assertIsSelected()
    }

    @Test
    fun unknownShortcutRouteShowsRecoverableFallbackInsteadOfThrowing() {
        composeRule.setContent {
            val controller = androidx.navigation.compose.rememberNavController()
            SideEffect { navController = controller }
            FishAppContent(controller)
        }
        composeRule.waitForIdle()
        composeRule.runOnIdle { navigateToShortcut(navController, "not-a-registered-route") }
        composeRule.onNodeWithText("无法识别这个页面").assertExists()
        composeRule.onNodeWithTag("destination-overview").assertDoesNotExist()
        composeRule.onNodeWithText("返回").performClick()
        composeRule.onNodeWithTag("dashboard-title").assertExists()
    }

    @Test
    fun detailPagesHideBottomBarAndTheirOwnBackButtonReturnsToRoot() {
        launchAt("fish/detail/not-found-for-test")
        composeRule.onNodeWithTag("destination-fish").assertDoesNotExist()
        composeRule.onNodeWithText("返回").performClick()
        composeRule.onNodeWithTag("destination-overview").assertIsSelected()
        composeRule.onNodeWithTag("destination-fish").performClick()
        composeRule.onNodeWithTag("fish-list-screen").assertExists()
        composeRule.onNodeWithTag("destination-fish").assertIsSelected()

        composeRule.runOnIdle { navController.navigate("tank/detail/not-found-for-test") }
        composeRule.onNodeWithTag("destination-tanks").assertDoesNotExist()
        composeRule.onNodeWithText("返回").performClick()
        composeRule.onNodeWithTag("destination-overview").assertIsSelected()
        composeRule.onNodeWithTag("destination-tanks").performClick()
        composeRule.onNodeWithTag("tank-list-screen").assertExists()
        composeRule.onNodeWithTag("destination-tanks").assertIsSelected()
    }

    @Test
    fun systemBackFromDetailReturnsToPreviousTopLevelAndRestoresBottomBar() {
        launchAt("fish/detail/not-found-for-test")
        composeRule.onNodeWithTag("destination-overview").assertDoesNotExist()
        Espresso.pressBack()
        composeRule.onNodeWithTag("destination-overview").assertIsSelected()
        composeRule.onNodeWithTag("dashboard-title").assertExists()
    }

    @Test
    fun navHostDestinationsMatchTheRegisteredRouteCatalog() {
        var graphRoutes by mutableStateOf(emptySet<String>())
        composeRule.setContent {
            val navController = androidx.navigation.compose.rememberNavController()
            SideEffect {
                this@FishAppNavigationTest.navController = navController
            }
            FishAppContent(navController)
        }
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            graphRoutes = (0 until navController.graph.nodes.size())
                .mapNotNull { navController.graph.nodes.valueAt(it).route }
                .toSet()
        }
        val expected = registeredAppRoutes.map { route ->
            if (route == AppDestination.Care.route) "care?view={view}" else route
        }.toSet()
        assertEquals(expected, graphRoutes)
    }

    @Test
    fun invalidFishRecordSectionAndTankActionShowRecoverableErrors() {
        launchAt("fish/records/missing/invalid")
        composeRule.onNodeWithText("未知的鱼只记录类型").assertExists()
        composeRule.onNodeWithText("返回").performClick()

        composeRule.runOnIdle { navController.navigate("tank/action/missing/invalid") }
        composeRule.onNodeWithText("未知的鱼缸操作").assertExists()
        composeRule.onNodeWithText("返回").performClick()
    }

    @Test
    fun profileStatisticsAndAboutPagesReturnToTheirActualPreviousPage() {
        launchAt(AppDestination.Profile.route)
        composeRule.onNodeWithTag("profile-screen").assertExists()
        composeRule.onNodeWithTag("destination-profile").performClick()
        composeRule.onNodeWithText("统计报表").performClick()
        composeRule.onNodeWithTag("statistics-screen").assertExists()
        composeRule.runOnIdle { navController.popBackStack() }
        composeRule.onNodeWithTag("profile-screen").assertExists()
        composeRule.onNodeWithTag("destination-profile").assertIsSelected()

        composeRule.onNodeWithText("关于").performClick()
        composeRule.onNodeWithText("观鱼记 · 第一阶段本地版").assertExists()
    }
}
