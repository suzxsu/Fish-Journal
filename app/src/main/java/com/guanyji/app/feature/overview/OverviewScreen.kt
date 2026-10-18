package com.guanyji.app.feature.overview

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.request.ImageRequest
import com.guanyji.app.R
import com.guanyji.app.core.designsystem.AquariumCardShell
import com.guanyji.app.core.designsystem.AquariumTypeBadge
import com.guanyji.app.data.local.*
import com.guanyji.app.feature.photo.ManagedPhotoImage
import com.guanyji.app.ui.theme.AquariumCardColors
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val ScreenPadding = 16.dp
private val HeaderHeight = 128.dp

@Composable
fun OverviewScreen(
    onAddTank: () -> Unit,
    onAddFish: () -> Unit,
    onFeeding: () -> Unit,
    onWater: (String?) -> Unit,
    onTank: (String) -> Unit,
    onFish: (String) -> Unit,
    onCare: (String?) -> Unit,
    onMessage: (String) -> Unit,
    onNavigate: (String) -> Unit = {},
    onRecordFeeding: (String?) -> Unit = { onFeeding() },
    onRecordWater: (String) -> Unit = { onWater(it) },
    onRecordGrowth: (String) -> Unit = {},
    onOpenRecords: () -> Unit = { onCare(null) },
    onOpenTask: (CareTask) -> Unit = {},
    vm: OverviewViewModel = viewModel(factory = OverviewViewModel.factory(LocalContext.current.applicationContext as Application)),
) {
    val overview by vm.state.collectAsStateWithLifecycle()
    val tanks = overview.tanks
    val tasks = overview.tasks
    val fishRows = overview.fish
    val fish = fishRows.filter { it.fish.status in setOf(FishStatus.active, FishStatus.quarantine) }
        .sortedByDescending { it.lastMeasuredAt ?: it.fish.addedAt }
    val fishByTank = remember(fishRows) {
        fishRows.filter { it.fish.status in setOf(FishStatus.active, FishStatus.quarantine) }.groupingBy { it.fish.tankId }.eachCount()
    }
    val completedTaskIds = remember(overview.careRecords) { overview.careRecords.mapNotNullTo(mutableSetOf()) { it.taskId } }
    val openTasks = tasks.filter { it.status in setOf(CareTaskStatus.pending, CareTaskStatus.overdue) }
    val recentEntries = remember(overview, fishRows, tanks) { buildRecentEntries(overview, fishRows, tanks) }

    var completing by remember { mutableStateOf<CareTask?>(null) }
    var saving by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    // 头部文案与今日清单：头部只保留"日期 / 问候 / 一行统计"，四宫格的统计数字全部并进这一行。
    val calendar = remember { Calendar.getInstance() }
    val dateLabel = remember(calendar) { SimpleDateFormat("M月d日 · EEE", Locale.getDefault()).format(calendar.time) }
    val greeting = remember(calendar) { greetingFor(calendar.get(Calendar.HOUR_OF_DAY)) }
    val statsLine = remember(tanks.size, fish.size, overview.todayFeedings) {
        if (tanks.isEmpty()) "还没有鱼缸 · 从建一个鱼缸开始"
        else "${tanks.size} 个鱼缸 · ${fish.size} 条鱼 · 今日喂食 ${overview.todayFeedings} 次"
    }
    // 待办优先、已完成的排在后面，最多显示三条。
    val shownTasks = remember(tasks, completedTaskIds) {
        val open = tasks.filter { it.status != CareTaskStatus.completed && it.id !in completedTaskIds }.sortedBy { it.plannedAt }
        val done = tasks.filter { it.status == CareTaskStatus.completed || it.id in completedTaskIds }.sortedByDescending { it.plannedAt }
        (open + done).take(3)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DashboardTokens.PageBackground)
            .verticalScroll(androidx.compose.foundation.rememberScrollState())
            .padding(bottom = 24.dp),
    ) {
        OverviewHeader(
            dateLabel = dateLabel,
            greeting = greeting,
            stats = statsLine,
            badgeCount = openTasks.size,
            onNotifications = { onCare(null) },
        )

        Column(
            modifier = Modifier.padding(horizontal = ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(DashboardTokens.SectionGap),
        ) {
            TodayPlanCard(
                tasks = shownTasks,
                openCount = openTasks.size,
                completedIds = completedTaskIds,
                tanks = tanks,
                fishRows = fishRows,
                onOpenTask = onOpenTask,
                onCompleteTask = { task -> completing = task },
                onOpenAll = { onCare(null) },
                onRecordFeeding = { onRecordFeeding(null) },
                onRecordWater = {
                    val target = tanks.firstOrNull()?.tank?.id
                    if (target == null) onMessage("请先添加鱼缸，再记录对应鱼缸的水质。") else onRecordWater(target)
                },
                onAddTank = onAddTank,
            )

            // 我的鱼缸：从两列网格改成横滑，首屏只占一条带子，鱼缸多也不会把页面撑长。
            if (tanks.isNotEmpty()) {
                DashboardSection(
                    title = "我的鱼缸",
                    icon = FishIcons.Aquarium,
                    actionLabel = "查看全部",
                    onAction = { onNavigate("tanks") },
                ) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(end = 4.dp),
                    ) {
                        items(tanks, key = { it.tank.id }) { row ->
                            val photoFlow = remember(row.tank.photoId) { vm.photo(row.tank.photoId) }
                            val photo by (photoFlow?.collectAsStateWithLifecycle() ?: remember { mutableStateOf(null) })
                            val tankTests by remember(row.tank.id) { vm.waterTests(row.tank.id) }.collectAsStateWithLifecycle()
                            AquariumCard(
                                data = AquariumCardData(
                                    tank = row.tank,
                                    photo = photo,
                                    metrics = aquariumMetrics(tankTests.firstOrNull()),
                                    fishCount = fishByTank[row.tank.id] ?: 0,
                                ),
                                onClick = { onTank(row.tank.id) },
                                modifier = Modifier.width(184.dp),
                            )
                        }
                        item(key = "add-tank") { AddTankCard(onAddTank) }
                    }
                }
            }

            // 最近动态：单列三行；没有记录时整块不渲染，不再放一张"还没有记录"的空卡。
            if (recentEntries.isNotEmpty()) {
                DashboardSection(
                    title = "最近动态",
                    icon = Icons.Outlined.Check,
                    actionLabel = "全部记录",
                    onAction = onOpenRecords,
                ) {
                    Column {
                        recentEntries.take(3).forEachIndexed { index, entry ->
                            if (index > 0) ActivityDivider()
                            ActivityRow(
                                entry = entry,
                                onClick = if (entry.kind == RecordKind.growth && entry.fishId != null) {
                                    { onRecordGrowth(entry.fishId) }
                                } else null,
                            )
                        }
                    }
                }
            }
        }
    }

    completing?.let { task -> AlertDialog(
        onDismissRequest = { if (!saving) completing = null },
        title = { Text("完成${task.displayTitleForOverview()}") },
        text = { Text("确认已完成这项任务？此操作会写入养护记录。") },
        confirmButton = { TextButton(enabled = !saving, onClick = { saving = true; vm.completeTask(task, { saving = false; completing = null }, { saving = false; message = it }) }) { Text(if (saving) "保存中…" else "确认完成") } },
        dismissButton = { TextButton(enabled = !saving, onClick = { completing = null }) { Text("取消") } },
    ) }
    message?.let { text -> AlertDialog(onDismissRequest = { message = null }, text = { Text(text) }, confirmButton = { TextButton(onClick = { message = null }) { Text("知道了") } }) }
}

/* ------------------------------- 顶部主题 Header ------------------------------- */

/**
 * 压缩后的头部：日期 + 问候 + 一行统计（鱼缸数 / 鱼只数 / 今日喂食）+ 提醒铃铛。
 * 原来单独占一块的四宫格被删掉，它的四个统计数字全部并进这一行。
 */
@Composable
private fun OverviewHeader(dateLabel: String, greeting: String, stats: String, badgeCount: Int, onNotifications: () -> Unit) {
    val statusBarInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    Box(modifier = Modifier.fillMaxWidth().height(HeaderHeight + statusBarInset), contentAlignment = Alignment.TopStart) {
        SubcomposeAsyncImage(
            model = ImageRequest.Builder(LocalContext.current).data(R.drawable.dashboard_header_reef).crossfade(320).build(),
            contentDescription = "水族主题背景：珊瑚礁中的小丑鱼",
            contentScale = ContentScale.Crop,
            alignment = Alignment.TopCenter,
            modifier = Modifier.matchParentSize(),
            loading = { ReefFallback() },
            error = { ReefFallback() },
            success = { SubcomposeAsyncImageContent() },
        )
        Box(
            modifier = Modifier.matchParentSize().background(
                Brush.verticalGradient(
                    0.00f to Color(0xBD06182A),
                    0.34f to Color(0x8506182A),
                    0.62f to Color(0x29081C30),
                    0.88f to Color(0xB8F2F7FB),
                    1.00f to DashboardTokens.PageBackground,
                ),
            ),
        )
        Column(
            modifier = Modifier.fillMaxWidth().padding(start = ScreenPadding + 2.dp, end = 14.dp, top = statusBarInset + 10.dp),
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            // 海报是真实照片，纯靠遮罩在浅色珊瑚上仍不够稳，额外给文字压一层投影。
            val heroShadow = TextStyle(shadow = Shadow(color = Color(0x66000000), offset = Offset(0f, 1.5f), blurRadius = 3f))
            Text(
                text = dateLabel,
                color = Color(0xF2FFFFFF),
                fontSize = 11.5.sp,
                lineHeight = 15.sp,
                style = heroShadow,
                maxLines = 1,
            )
            Text(
                text = greeting,
                color = DashboardTokens.OnHero,
                fontSize = 25.sp,
                lineHeight = 31.sp,
                fontWeight = FontWeight.Bold,
                style = heroShadow,
                maxLines = 1,
                modifier = Modifier.testTag("dashboard-title"),
            )
            Text(
                text = stats,
                color = Color(0xF5FFFFFF),
                fontSize = 12.sp,
                lineHeight = 16.sp,
                fontWeight = FontWeight.Medium,
                style = heroShadow,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = statusBarInset + 20.dp, end = ScreenPadding)
                .size(44.dp)
                .clip(CircleShape)
                .background(Color(0x29FFFFFF))
                .border(1.dp, Color(0x57FFFFFF), CircleShape)
                .clickable(onClickLabel = "查看养护提醒", onClick = onNotifications)
                .testTag("dashboard-notifications"),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.Notifications, contentDescription = "养护提醒", tint = Color.White, modifier = Modifier.size(22.dp))
            if (badgeCount > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 7.dp, end = 8.dp)
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(DashboardTokens.AlertRed)
                        .border(1.5.dp, Color.White, CircleShape)
                        .testTag("dashboard-notification-badge"),
                )
            }
        }
    }
}

private fun greetingFor(hour: Int): String = when (hour) {
    in 5..8 -> "早上好"
    in 9..11 -> "上午好"
    in 12..13 -> "中午好"
    in 14..17 -> "下午好"
    in 18..22 -> "晚上好"
    else -> "夜深了"
}

/* ---------------------------- 今天要做的（主卡） ---------------------------- */

/**
 * 一屏的第一块，也是唯一的"行动区"：待办清单 + 两个即时记录入口。
 * 原四宫格与底部「快捷记录」的职能都收在这里，页面上不再有第二组入口。
 */
@Composable
private fun TodayPlanCard(
    tasks: List<CareTask>,
    openCount: Int,
    completedIds: Set<String>,
    tanks: List<TankSummary>,
    fishRows: List<FishSummary>,
    onOpenTask: (CareTask) -> Unit,
    onCompleteTask: (CareTask) -> Unit,
    onOpenAll: () -> Unit,
    onRecordFeeding: () -> Unit,
    onRecordWater: () -> Unit,
    onAddTank: () -> Unit,
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth().offset(y = (-18).dp).dashboardCardBorder(3.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = DashboardTokens.SurfaceCard),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 3.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("今天要做的", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = DashboardTokens.TitleInk, maxLines = 1)
                if (openCount > 0) {
                    Text(
                        text = "$openCount 项待办",
                        fontSize = 11.sp,
                        lineHeight = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = DashboardTokens.DeepBlue,
                        maxLines = 1,
                        modifier = Modifier
                            .clip(DashboardTokens.BadgeShape)
                            .background(DashboardTokens.OceanTint)
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
                Spacer(Modifier.weight(1f))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(DashboardTokens.BadgeShape)
                        .clickable(onClickLabel = "查看全部养护任务", onClick = onOpenAll)
                        .padding(start = 6.dp, end = 2.dp, top = 4.dp, bottom = 4.dp),
                ) {
                    Text("全部", fontSize = 12.sp, color = DashboardTokens.SecondaryInk, maxLines = 1)
                    Icon(FishIcons.ArrowRight, contentDescription = null, tint = DashboardTokens.TertiaryInk, modifier = Modifier.size(15.dp))
                }
            }

            if (tasks.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(Modifier.size(40.dp).clip(CircleShape).background(DashboardTokens.OceanTint), contentAlignment = Alignment.Center) {
                        Icon(FishIcons.Calendar, contentDescription = null, tint = DashboardTokens.OceanBlue, modifier = Modifier.size(20.dp))
                    }
                    Text("还没有养护安排", fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = DashboardTokens.TitleInk)
                    Text(
                        text = if (tanks.isEmpty()) "建好鱼缸后，喂食、换水和水质检测会按你设的频率自动排进这里。" else "任务会按养护计划自动排进这里。",
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        color = DashboardTokens.SecondaryInk,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 8.dp),
                    )
                    if (tanks.isEmpty()) {
                        Text(
                            text = "添加鱼缸",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(DashboardTokens.DeepBlue)
                                .clickable(onClickLabel = "添加鱼缸", onClick = onAddTank)
                                .padding(vertical = 12.dp),
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            } else {
                Column(Modifier.padding(top = 4.dp)) {
                    tasks.forEachIndexed { index, task ->
                        if (index > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(DashboardTokens.Track))
                        val completed = task.status == CareTaskStatus.completed || task.id in completedIds
                        OverviewTaskRow(
                            task = task,
                            targetName = taskTargetName(task, tanks, fishRows),
                            completed = completed,
                            onOpen = { onOpenTask(task) },
                            onComplete = { if (!completed) onCompleteTask(task) },
                        )
                    }
                }
            }

            if (tanks.isNotEmpty()) {
                Box(Modifier.fillMaxWidth().padding(top = 8.dp).height(1.dp).background(DashboardTokens.Track))
                Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    QuickRecordChip("记录喂食", FishIcons.Calendar, onRecordFeeding, Modifier.weight(1f))
                    QuickRecordChip("记录水质", FishIcons.Droplet, onRecordWater, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun OverviewTaskRow(task: CareTask, targetName: String, completed: Boolean, onOpen: () -> Unit, onComplete: () -> Unit) {
    val look = taskVisualLook(task)
    val overdue = !completed && task.plannedAt < System.currentTimeMillis()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = "打开任务" + task.displayTitleForOverview(), onClick = onOpen)
            .padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(if (completed) DashboardTokens.AquaGreen else Color.Transparent)
                .border(1.6.dp, if (completed) DashboardTokens.AquaGreen else Color(0xFFC9D7E3), RoundedCornerShape(7.dp))
                .clickable(enabled = !completed, onClickLabel = "标记「${task.displayTitleForOverview()}」为已完成", role = Role.Checkbox, onClick = onComplete),
            contentAlignment = Alignment.Center,
        ) {
            if (completed) Icon(Icons.Outlined.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(13.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                text = if (completed) look.label else task.displayTitleForOverview(),
                fontSize = 13.5.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (completed) DashboardTokens.TertiaryInk else DashboardTokens.TitleInk,
                textDecoration = if (completed) TextDecoration.LineThrough else TextDecoration.None,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (completed) "$targetName · 已完成" else targetName,
                fontSize = 11.5.sp,
                lineHeight = 15.sp,
                color = if (completed) DashboardTokens.TertiaryInk else DashboardTokens.SecondaryInk,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = time(task.plannedAt),
            fontSize = 11.5.sp,
            fontWeight = if (overdue) FontWeight.SemiBold else FontWeight.Normal,
            color = if (overdue) DashboardTokens.AlertRed else DashboardTokens.SecondaryInk,
            maxLines = 1,
        )
    }
}

@Composable
private fun QuickRecordChip(label: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .heightIn(min = 36.dp)
            .clip(DashboardTokens.BadgeShape)
            .background(DashboardTokens.OceanTint)
            .clickable(onClickLabel = label, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Icon(icon, contentDescription = null, tint = DashboardTokens.DeepBlue, modifier = Modifier.size(15.dp))
        Text(label, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = DashboardTokens.DeepBlue, maxLines = 1)
    }
}

/* ------------------------------ 鱼缸横滑 / 新建 ------------------------------ */

@Composable
private fun AddTankCard(onAddTank: () -> Unit) {
    Column(
        modifier = Modifier
            .width(108.dp)
            .height(192.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(DashboardTokens.AquariumPanel)
            .border(1.4.dp, Color(0xFFBFD3E4), RoundedCornerShape(18.dp))
            .clickable(onClickLabel = "新建鱼缸", onClick = onAddTank)
            .padding(horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Outlined.Add, contentDescription = null, tint = DashboardTokens.DeepBlue, modifier = Modifier.size(24.dp))
        Text("新建鱼缸", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = DashboardTokens.DeepBlue, modifier = Modifier.padding(top = 6.dp))
        Text(
            text = "照片 · 水质 · 鱼只",
            fontSize = 10.5.sp,
            lineHeight = 14.sp,
            color = DashboardTokens.TertiaryInk,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 3.dp),
        )
    }
}

/* ------------------------------- 最近动态 ------------------------------- */

@Composable
private fun ActivityRow(entry: DashboardRecord, onClick: (() -> Unit)?) {
    val accent = recordAccent(entry.kind)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClickLabel = "查看成长记录", onClick = onClick) else Modifier)
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(30.dp).clip(RoundedCornerShape(10.dp)).background(accent.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
            Icon(recordIcon(entry.kind), contentDescription = null, tint = accent, modifier = Modifier.size(15.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                text = entry.title + " · " + entry.primary,
                fontSize = 13.sp,
                lineHeight = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = DashboardTokens.TitleInk,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(entry.caption, fontSize = 11.sp, color = DashboardTokens.TertiaryInk, maxLines = 1)
    }
}

@Composable
private fun ActivityDivider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(DashboardTokens.Track))
}

@Composable
private fun ReefFallback() {
    Box(
        modifier = Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(DashboardTokens.DeepBlue, DashboardTokens.OceanBlue, Color(0xFF6FB6E8))),
        ),
    )
}

/**
 * 统一的卡片描边：Material3 的 ElevatedCard 在当前版本没有 border 参数，
 * 这里在圆角形状上叠一层细描边，保持一致的轻微平面感。
 */
private fun Modifier.dashboardCardBorder(elevation: androidx.compose.ui.unit.Dp): Modifier = this
    .clip(DashboardTokens.CardShape)
    .background(DashboardTokens.CardBorder)
    .padding(1.dp)
    .shadow(elevation, DashboardTokens.CardShape)
    .background(DashboardTokens.SurfaceCard, DashboardTokens.CardShape)

/* ------------------------------- 分区标题 ------------------------------- */

@Composable
private fun DashboardSection(
    title: String,
    icon: ImageVector,
    actionLabel: String?,
    onAction: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(icon, contentDescription = null, tint = DashboardTokens.DeepBlue, modifier = Modifier.size(18.dp))
            Text(title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = DashboardTokens.TitleInk, modifier = Modifier.weight(1f), maxLines = 1)
            if (actionLabel != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(DashboardTokens.BadgeShape)
                        .clickable(onClickLabel = actionLabel, onClick = onAction)
                        .padding(start = 6.dp, end = 2.dp, top = 4.dp, bottom = 4.dp),
                ) {
                    Text(actionLabel, fontSize = 12.sp, color = DashboardTokens.SecondaryInk, maxLines = 1)
                    Icon(FishIcons.ArrowRight, contentDescription = null, tint = DashboardTokens.TertiaryInk, modifier = Modifier.size(16.dp))
                }
            }
        }
        content()
    }
}

/* ------------------------------- 我的鱼缸 ------------------------------- */

internal data class AquariumMetric(val label: String, val value: String, val icon: ImageVector)

internal data class AquariumCardData(
    val tank: Tank,
    val photo: Photo?,
    val metrics: List<AquariumMetric>,
    val fishCount: Int,
)

/**
 * 总览页的鱼缸卡：直接复用「鱼缸」列表页的 [AquariumCardShell]，
 * 深色玻璃卡面、受光描边、类型徽标与列表页完全一致，只是宽度收窄成横滑卡。
 */
@Composable
private fun AquariumCard(data: AquariumCardData, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val freshwater = data.tank.type == TankType.freshwater
    AquariumCardShell(
        accent = if (freshwater) AquariumCardColors.FreshwaterAccent else AquariumCardColors.SaltwaterAccent,
        onClick = onClick,
        onClickLabel = "查看${data.tank.name}详情",
        modifier = modifier,
        imageAspectRatio = 1.7f,
        imageContent = {
            if (data.photo != null) {
                ManagedPhotoImage(data.photo, true, Modifier.matchParentSize())
            } else {
                AquariumPhotoPlaceholder(Modifier.matchParentSize())
            }
        },
        overlayContent = {
            AquariumTypeBadge(
                label = if (freshwater) "淡水缸" else "海水缸",
                accent = if (freshwater) AquariumCardColors.FreshwaterBadge else AquariumCardColors.SaltwaterBadge,
                modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
            )
        },
        infoContent = {
            val capacity = data.tank.capacityValue?.takeIf { it > 0 }?.let { value ->
                val unit = data.tank.capacityUnit?.takeIf { it.isNotBlank() }.orEmpty()
                "${value.prettyNumber()} $unit".trim()
            }
            Column(
                modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 7.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = data.tank.name,
                        fontSize = 14.sp,
                        lineHeight = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AquariumCardColors.CardInk,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Icon(
                        imageVector = FishIcons.ArrowRight,
                        contentDescription = null,
                        tint = AquariumCardColors.CardSecondaryInk,
                        modifier = Modifier.size(16.dp),
                    )
                }
                Text(
                    text = listOfNotNull(capacity, "在养 ${data.fishCount} 条").joinToString(" · "),
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    color = AquariumCardColors.CardSecondaryInk,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                AquariumMetricLayout(data.metrics)
            }
        },
    )
}

@Composable
private fun AquariumPhotoPlaceholder(modifier: Modifier) {
    Box(
        modifier = modifier.background(
            Brush.verticalGradient(listOf(AquariumCardColors.PlaceholderTop, AquariumCardColors.PlaceholderBottom)),
        ),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(FishIcons.Aquarium, contentDescription = null, tint = AquariumCardColors.PlaceholderInk, modifier = Modifier.size(26.dp))
            Text("尚未添加鱼缸照片", fontSize = 11.sp, color = AquariumCardColors.PlaceholderText, maxLines = 1)
        }
    }
}

@Composable
private fun AquariumMetricLayout(metrics: List<AquariumMetric>) {
    // 与「鱼缸」列表卡同一套指标排版：两列，每列"图标 + 标签 / 数值"。
    Row(Modifier.fillMaxWidth().padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        val shown = metrics.take(2)
        if (shown.isEmpty()) {
            Text(
                text = "尚无水质记录",
                fontSize = 11.sp,
                lineHeight = 15.sp,
                color = AquariumCardColors.CardSecondaryInk.copy(alpha = 0.75f),
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
        } else {
            shown.forEach { item -> AquariumMetricItem(item, Modifier.weight(1f)) }
            if (shown.size == 1) Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun AquariumMetricItem(metric: AquariumMetric, modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        Icon(
            imageVector = metric.icon,
            contentDescription = null,
            tint = AquariumCardColors.CardMetricInk.copy(alpha = 0.85f),
            modifier = Modifier.size(13.dp),
        )
        Column {
            Text(metric.label, fontSize = 10.5.sp, lineHeight = 13.sp, color = AquariumCardColors.CardSecondaryInk, maxLines = 1)
            Text(
                text = metric.value,
                fontSize = 13.sp,
                lineHeight = 17.sp,
                fontWeight = FontWeight.Medium,
                color = AquariumCardColors.CardMetricInk,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/* ------------------------------- 今日任务 ------------------------------- */

private data class TaskTypeLook(val label: String, val icon: ImageVector, val tint: Color, val accent: Color)

private fun taskVisualLook(task: CareTask): TaskTypeLook = when (task.type) {
    CareTaskType.feeding -> TaskTypeLook("喂食", FishIcons.Calendar, DashboardTokens.SunsetTint, DashboardTokens.SunsetOrange)
    CareTaskType.water_change -> TaskTypeLook("换水", FishIcons.Droplet, DashboardTokens.OceanTint, DashboardTokens.OceanBlue)
    CareTaskType.water_test -> TaskTypeLook("水质检测", FishIcons.Droplet, DashboardTokens.CoralTint, DashboardTokens.CoralPurple)
    CareTaskType.salinity_test -> TaskTypeLook("盐度检测", FishIcons.WaterLevel, DashboardTokens.CoralTint, DashboardTokens.CoralPurple)
    CareTaskType.maintenance -> TaskTypeLook("设备维护", FishIcons.Aquarium, DashboardTokens.AquaTint, DashboardTokens.AquaGreen)
    CareTaskType.custom -> TaskTypeLook(task.customTitle ?: "自定义任务", FishIcons.Tag, DashboardTokens.OceanTint, DashboardTokens.DeepBlue)
}

/* ------------------------------- 最近记录 ------------------------------- */

internal enum class RecordKind { feeding, water, growth }

internal data class DashboardRecord(
    val kind: RecordKind,
    val title: String,
    val primary: String,
    val caption: String,
    val at: Long,
    val photoId: String?,
    val photoThumbnail: Boolean,
    val fishId: String? = null,
)

private fun recordIcon(kind: RecordKind): ImageVector = when (kind) {
    RecordKind.feeding -> FishIcons.Calendar
    RecordKind.water -> FishIcons.Droplet
    RecordKind.growth -> FishIcons.Fish
}

private fun recordAccent(kind: RecordKind): Color = when (kind) {
    RecordKind.feeding -> DashboardTokens.SunsetOrange
    RecordKind.water -> DashboardTokens.OceanBlue
    RecordKind.growth -> DashboardTokens.AquaGreen
}

/* ------------------------------- 数据与文案 ------------------------------- */

private fun buildRecentEntries(overview: OverviewSnapshot, fishRows: List<FishSummary>, tanks: List<TankSummary>): List<DashboardRecord> {
    val tankNames = tanks.associate { it.tank.id to it.tank.name }
    val fishById = fishRows.associateBy { it.fish.id }
    val entries = ArrayList<DashboardRecord>(3)

    overview.records.feedings.maxByOrNull { it.event.fedAt }?.let { row ->
        val tankName = row.event.tankId?.let { tankNames[it] }
            ?: row.targets.firstNotNullOfOrNull { target ->
                target.tankId?.let { tankNames[it] } ?: target.fishId?.let { fishId -> fishById[fishId]?.fish?.tankId?.let { tankNames[it] } }
            }
        val photoId = row.event.tankId?.let { id -> tanks.firstOrNull { it.tank.id == id }?.tank?.photoId }
        entries += DashboardRecord(
            kind = RecordKind.feeding,
            title = "喂食",
            primary = tankName ?: "未指定鱼缸",
            caption = relativeTimeLabel(row.event.fedAt),
            at = row.event.fedAt,
            photoId = photoId,
            photoThumbnail = true,
        )
    }

    overview.records.waterTests.maxByOrNull { it.test.measuredAt }?.let { record ->
        val measured = record.values.filter { it.value != null }
        entries += DashboardRecord(
            kind = RecordKind.water,
            title = "水质",
            primary = keywordSummary(measured) ?: (tankNames[record.test.tankId] ?: "未指定鱼缸"),
            caption = relativeTimeLabel(record.test.measuredAt),
            at = record.test.measuredAt,
            photoId = null,
            photoThumbnail = false,
        )
    }

    val latestGrowth = overview.records.growthRecords.maxByOrNull { it.measuredAt }
    if (latestGrowth != null) {
        val fish = fishById[latestGrowth.fishId]
        val measurement = listOfNotNull(
            latestGrowth.lengthValue?.let { it.prettyNumber() + latestGrowth.lengthUnit.orEmpty() },
            latestGrowth.weightValue?.let { it.prettyNumber() + latestGrowth.weightUnit.orEmpty() },
        ).joinToString(" · ")
        entries += DashboardRecord(
            kind = RecordKind.growth,
            title = "成长记录",
            primary = (fish?.fish?.name ?: "未命名鱼只") + if (measurement.isEmpty()) "" else " · " + measurement,
            caption = relativeTimeLabel(latestGrowth.measuredAt),
            at = latestGrowth.measuredAt,
            photoId = fish?.fish?.coverPhotoId,
            photoThumbnail = true,
            fishId = fish?.fish?.id ?: latestGrowth.fishId,
        )
    } else {
        val fallback = fishRows.filter { it.fish.status in setOf(FishStatus.active, FishStatus.quarantine) }.maxByOrNull { it.fish.addedAt }
        if (fallback != null) {
            entries += DashboardRecord(
                kind = RecordKind.growth,
                title = "成长记录",
                primary = fallback.fish.name + " · 尚无测量",
                caption = "入缸 " + durationLabel(fallback.fish.addedAt),
                at = fallback.fish.addedAt,
                photoId = fallback.fish.coverPhotoId,
                photoThumbnail = true,
                fishId = fallback.fish.id,
            )
        }
    }

    return entries.sortedByDescending { it.at }
}

private fun keywordSummary(values: List<WaterTestValue>): String? =
    aquariumParameterSummaries(values, limit = 4).firstOrNull()

private fun aquariumMetrics(latest: WaterTestWithValues?): List<AquariumMetric> {
    val icons = mapOf(
        "水温" to FishIcons.Droplet,
        "pH" to FishIcons.Eye,
        "盐度" to FishIcons.WaterLevel,
        "比重" to FishIcons.WaterLevel,
    )
    return aquariumParameterSummaries(latest?.values.orEmpty()).map { summary ->
        val label = summary.substringBefore(' ')
        val value = summary.substringAfter(' ', "").ifBlank { "--" }
        AquariumMetric(label, value, icons[label] ?: FishIcons.Droplet)
    }
}

private fun taskTargetName(task: CareTask, tanks: List<TankSummary>, fishRows: List<FishSummary>): String {
    task.tankId?.let { id -> tanks.firstOrNull { it.tank.id == id }?.tank?.name?.let { name -> return name } }
    task.fishId?.let { id ->
        val fish = fishRows.firstOrNull { it.fish.id == id }?.fish ?: return@let
        return fish.name + " · " + (tanks.firstOrNull { it.tank.id == fish.tankId }?.tank?.name ?: "未指定鱼缸")
    }
    return "未指定鱼缸"
}

private fun time(value: Long) = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(value))
