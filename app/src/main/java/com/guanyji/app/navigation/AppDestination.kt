package com.guanyji.app.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.ui.graphics.vector.ImageVector
import com.guanyji.app.feature.overview.FishIcons

enum class AppDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val title: String,
    val description: String,
) {
    Overview("overview", "总览", Icons.Outlined.Home, "今日概览", "添加鱼缸后，这里会汇总你的养护安排。"),
    Fish("fish", "我的鱼", Icons.Outlined.Favorite, "我的鱼", "鱼只档案会保存在本机，目前还没有记录。"),
    Tanks("tanks", "鱼缸", Icons.Outlined.Home, "鱼缸", "创建鱼缸后，可在这里查看环境与养护记录。"),
    Care("care", "养护", Icons.Outlined.DateRange, "养护计划", "你的本地养护任务会显示在这里。"),
    Profile("profile", "我的", Icons.Outlined.Person, "我的", "统计、设置、备份与关于。"),
}

internal fun AppDestination.matchesRoute(route: String?): Boolean = route?.let(::topLevelOwner) == this

internal val registeredAppRoutes = listOf(
    AppDestination.Overview.route,
    AppDestination.Fish.route,
    AppDestination.Tanks.route,
    AppDestination.Care.route,
    "care?view={view}",
    AppDestination.Profile.route,
    "tank/create",
    "fish/create?tankId={tankId}",
    "fish/edit/{fishId}",
    "fish/detail/{fishId}",
    "fish/records/{fishId}/{section}",
    "tank/edit/{tankId}",
    "tank/detail/{tankId}",
    "tank/records/{tankId}/{section}",
    "tank/action/{tankId}/{action}",
    "tank/action/{tankId}/change/{changeId}",
    "feeding?tankId={tankId}&fishId={fishId}",
    "statistics",
    "settings?section={section}",
    "about",
    "about/{section}",
    "navigation/unknown",
)

internal fun topLevelOwner(routePattern: String): AppDestination? = when (routePattern) {
    "overview" -> AppDestination.Overview
    "fish", "fish/create?tankId={tankId}", "fish/edit/{fishId}", "fish/detail/{fishId}", "fish/records/{fishId}/{section}" -> AppDestination.Fish
    "tanks", "tank/create", "tank/edit/{tankId}", "tank/detail/{tankId}", "tank/records/{tankId}/{section}", "tank/action/{tankId}/{action}", "tank/action/{tankId}/change/{changeId}" -> AppDestination.Tanks
    "care", "care?view={view}", "care?view=" -> AppDestination.Care
    "profile", "statistics", "settings?section={section}", "about", "about/{section}" -> AppDestination.Profile
    "feeding?tankId={tankId}&fishId={fishId}", "navigation/unknown" -> null
    else -> null
}

/**
 * 沉浸式页面：隐藏 App 底部导航，由页面自己的头图返回键和记录坞承担导航与动作。
 * 覆盖详情页、二级记录页、统计页，以及水质/换水录入页；返回依靠页内返回键与系统返回。
 */
internal val immersiveDetailRoutes = setOf(
    "fish/detail/{fishId}",
    "fish/records/{fishId}/{section}",
    "tank/detail/{tankId}",
    "tank/records/{tankId}/{section}",
    "tank/action/{tankId}/change/{changeId}",
    "statistics",
)

internal fun showsBottomBar(routePattern: String?, action: String? = null): Boolean =
    routePattern != null && topLevelOwner(routePattern) != null && routePattern !in immersiveDetailRoutes &&
        !(routePattern == "tank/action/{tankId}/{action}" && action in setOf("water", "change"))

internal fun isKnownRoute(routePattern: String): Boolean = routePattern in registeredAppRoutes

private fun encodeRoutePart(value: String): String = java.net.URLEncoder.encode(value, Charsets.UTF_8.name())
    .replace("+", "%20")

internal fun feedingRoute(tankId: String? = null, fishId: String? = null): String = buildList {
    tankId?.let { add("tankId=${encodeRoutePart(it)}") }
    fishId?.let { add("fishId=${encodeRoutePart(it)}") }
}.joinToString(prefix = "feeding?", separator = "&").removeSuffix("?")

internal fun fishCreateRoute(tankId: String? = null): String =
    tankId?.let { "fish/create?tankId=${encodeRoutePart(it)}" } ?: "fish/create"

internal fun careRoute(view: String? = null): String =
    view?.let { "care?view=${encodeRoutePart(it)}" } ?: "care"

internal fun fishRecordsRoute(fishId: String, section: String): String =
    "fish/records/${encodeRoutePart(fishId)}/${encodeRoutePart(section)}"

/**
 * 底部导航使用的水族主题图标。
 * 保留 [AppDestination.icon] 作为原有字段与默认图标，仅底部导航切换到统一线条风格的图标。
 */
internal fun AppDestination.navIcon(): ImageVector = when (this) {
    AppDestination.Overview -> Icons.Outlined.Home
    AppDestination.Fish -> FishIcons.Fish
    AppDestination.Tanks -> FishIcons.Aquarium
    AppDestination.Care -> FishIcons.Calendar
    AppDestination.Profile -> Icons.Outlined.Person
}
