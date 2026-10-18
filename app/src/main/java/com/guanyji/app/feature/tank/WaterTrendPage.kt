@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.guanyji.app.feature.tank

import android.app.Application
import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.Button
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.guanyji.app.core.designsystem.DetailTokens
import com.guanyji.app.core.designsystem.DeepMetric
import com.guanyji.app.core.designsystem.DeepWaterCard
import com.guanyji.app.core.designsystem.EmptyState
import com.guanyji.app.core.designsystem.LoadingState
import com.guanyji.app.core.designsystem.SubpageTopBar
import com.guanyji.app.data.local.TankType
import com.guanyji.app.data.local.WaterChange
import com.guanyji.app.data.local.WaterTestValue
import com.guanyji.app.data.local.WaterTestWithValues
import com.guanyji.app.feature.overview.prettyNumber
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

private enum class WaterTrendTab(val label: String) { Water("水质趋势"), Change("换水记录") }

private enum class WaterRange(val label: String, val days: Int?) {
    Days7("7 天", 7), Days30("30 天", 30), Days90("90 天", 90), All("全部", null),
}

private data class WaterPoint(val at: Long, val value: Double)

@Composable
fun TankWaterRecordsPage(
    tankId: String,
    onBack: () -> Unit,
    onRecord: () -> Unit,
    onRecordChange: () -> Unit,
    onEditChange: (String) -> Unit,
    onRecordTemplate: (String) -> Unit,
    initialTab: String? = null,
    vm: TankViewModel = viewModel(factory = TankViewModel.factory(LocalContext.current.applicationContext as Application)),
) {
    val summaryState by remember(tankId) { vm.summary(tankId) }.collectAsStateWithLifecycle()
    val tank = (summaryState as? TankViewModel.SummaryState.Found)?.summary?.tank
    val tests by remember(tankId) { vm.tests(tankId) }.collectAsStateWithLifecycle(initialValue = emptyList())
    val changes by remember(tankId) { vm.changes(tankId) }.collectAsStateWithLifecycle(initialValue = emptyList())
    var tab by rememberSaveable(tankId) { mutableStateOf(WaterTrendTab.entries.firstOrNull { it.name.equals(initialTab, true) }?.name ?: WaterTrendTab.Water.name) }
    var selectedCode by rememberSaveable(tankId) { mutableStateOf<String?>(null) }
    var rangeKey by rememberSaveable(tankId) { mutableStateOf(WaterRange.All.name) }
    var detailTest by remember { mutableStateOf<WaterTestWithValues?>(null) }
    val parameters = remember(tank?.type) { tank?.type?.let(::waterParameters).orEmpty() }
    val latest = tests.firstOrNull()
    LaunchedEffect(latest?.test?.id, parameters) {
        if (selectedCode == null || parameters.none { it.code == selectedCode }) {
            selectedCode = latest?.values?.firstOrNull { value -> parameters.any { it.code == value.parameterCode } }?.parameterCode
                ?: parameters.firstOrNull()?.code
        }
    }
    if (tank == null) {
        if (summaryState is TankViewModel.SummaryState.Loading) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingState("正在加载水质趋势") }
        else Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Text("找不到这个鱼缸", modifier = Modifier.testTag("tank-not-found"), style = androidx.compose.material3.MaterialTheme.typography.titleLarge)
            Text("鱼缸可能已被删除或链接已失效。", color = DetailTokens.SecondaryInk)
            TextButton(onClick = onBack) { Text("返回") }
        }
        return
    }
    val currentTab = WaterTrendTab.entries.firstOrNull { it.name == tab } ?: WaterTrendTab.Water
    Scaffold(
        containerColor = DetailTokens.PageBackground,
        topBar = {
            SubpageTopBar(
                "水质趋势",
                "${tank.name} · ${waterTankTypeLabel(tank.type)}",
                onBack,
                actionIcon = Icons.Outlined.Add,
                actionDescription = if (currentTab == WaterTrendTab.Water) "记录水质" else "记录换水",
                onAction = if (currentTab == WaterTrendTab.Water) onRecord else onRecordChange,
                titleModifier = Modifier.testTag("tank-records-title"),
            )
        },
        bottomBar = {
            WaterBottomBar(
                isWater = currentTab == WaterTrendTab.Water,
                count = if (currentTab == WaterTrendTab.Water) tests.size else changes.size,
                latestAt = if (currentTab == WaterTrendTab.Water) latest?.test?.measuredAt else changes.maxByOrNull { it.completedAt }?.completedAt,
                onClick = if (currentTab == WaterTrendTab.Water) onRecord else onRecordChange,
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            WaterTabBar(currentTab, changes.size) { tab = it.name }
            if (currentTab == WaterTrendTab.Water) {
                if (tests.isEmpty()) {
                    EmptyWaterTrend(onRecord)
                } else {
                    val selected = parameters.firstOrNull { it.code == selectedCode } ?: parameters.firstOrNull()
                    val range = WaterRange.entries.firstOrNull { it.name == rangeKey } ?: WaterRange.All
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 18.dp),
                        verticalArrangement = Arrangement.spacedBy(9.dp),
                    ) {
                        item {
                            WaterTrendHero(
                                tankType = tank.type,
                                tests = tests,
                                latest = latest,
                                parameters = parameters,
                                selectedCode = selected?.code,
                                range = range,
                                onSelectCode = { selectedCode = it },
                                onSelectRange = { rangeKey = it.name },
                                onRecord = onRecord,
                            )
                        }
                        item { WaterHistoryHeader(tests.size) }
                        val groups = tests.sortedByDescending { it.test.measuredAt }.groupBy { waterDayLabel(it.test.measuredAt) }
                        groups.forEach { (day, list) ->
                            item { WaterDayHeader(day, list.size) }
                            items(list.size, key = { list[it].test.id }) { index ->
                                WaterHistoryRow(list[index]) { detailTest = list[index] }
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    item { WaterChangeSummary(changes) }
                    if (changes.isEmpty()) {
                        item { WaterChangeEmptyHint() }
                    } else {
                        val groups = changes.sortedByDescending { it.completedAt }.groupBy { waterMonthLabel(it.completedAt) }
                        groups.forEach { (month, list) ->
                            item { WaterChangeMonthHeader(month, list.size) }
                            items(list.size, key = { list[it].id }) { index ->
                                WaterChangeRow(list[index]) { onEditChange(list[index].id) }
                            }
                        }
                    }
                }
            }
        }
    }
    detailTest?.let { test ->
        ModalBottomSheet(onDismissRequest = { detailTest = null }) {
            WaterTestDetailSheet(test, onClose = { detailTest = null }, onTemplate = { onRecordTemplate(test.test.id); detailTest = null })
        }
    }
}

@Composable
private fun WaterTabBar(selected: WaterTrendTab, changeCount: Int, onSelect: (WaterTrendTab) -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(38.dp).background(DetailTokens.PageBackground).padding(horizontal = 18.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        WaterTrendTab.entries.forEach { tab ->
            val on = selected == tab
            Column(Modifier.width(IntrinsicSize.Max).padding(end = 22.dp).clickable { onSelect(tab) }.padding(bottom = 7.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(tab.label, color = if (on) DetailTokens.TitleInk else DetailTokens.SecondaryInk, fontSize = 12.5.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal)
                    if (tab == WaterTrendTab.Change) Text(" $changeCount", color = DetailTokens.TertiaryInk, fontSize = 8.5.sp)
                }
                Box(Modifier.padding(top = 6.dp).fillMaxWidth().height(2.dp).clip(RoundedCornerShape(1.dp)).background(if (on) DetailTokens.Primary else Color.Transparent))
            }
        }
    }
}

@Composable
private fun WaterTrendHero(
    tankType: TankType,
    tests: List<WaterTestWithValues>,
    latest: WaterTestWithValues?,
    parameters: List<WaterParameter>,
    selectedCode: String?,
    range: WaterRange,
    onSelectCode: (String) -> Unit,
    onSelectRange: (WaterRange) -> Unit,
    onRecord: () -> Unit,
) {
    val latestValues = parameters.mapNotNull { parameter ->
        latest?.values?.firstOrNull { it.parameterCode == parameter.code && it.value != null }?.let { parameter to it }
    }.take(3)
    val selected = parameters.firstOrNull { it.code == selectedCode } ?: parameters.firstOrNull()
    val cutoff = range.days?.let { System.currentTimeMillis() - it * 86_400_000L }
    val points = remember(tests, selected?.code, range.name) {
        if (selected == null) emptyList()
        else tests.mapNotNull { test ->
            test.values.firstOrNull { it.parameterCode == selected.code && it.value != null }?.let { WaterPoint(test.test.measuredAt, it.value!!) }
        }.filter { cutoff == null || it.at >= cutoff }.sortedBy { it.at }
    }
    WaterTrendHeroContent(tankType, latest, parameters, selected, latestValues, points, range, onSelectCode, onSelectRange, onRecord)
}

@Composable
private fun WaterTrendHeroContent(
    tankType: TankType,
    latest: WaterTestWithValues?,
    parameters: List<WaterParameter>,
    selected: WaterParameter?,
    latestValues: List<Pair<WaterParameter, WaterTestValue>>,
    points: List<WaterPoint>,
    range: WaterRange,
    onSelectCode: (String) -> Unit,
    onSelectRange: (WaterRange) -> Unit,
    onRecord: () -> Unit,
) {
    DeepWaterCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            if (latest == null) {
                Text("还没有水质检测", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text("记录一次实测值后，这里会显示最近水况和趋势。", color = Color(0xFF9BC3D7), fontSize = 10.sp, modifier = Modifier.padding(top = 4.dp))
                Text("记录水质", color = Color(0xFF8FEDDF), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 10.dp).clickable(onClick = onRecord))
            } else {
                Row(Modifier.fillMaxWidth()) {
                    latestValues.forEach { (parameter, value) ->
                        WaterLatestMetric(parameter, value, Modifier.weight(1f))
                    }
                    if (latestValues.isEmpty()) Text("这次检测没有有效数值", color = Color(0xFF9BC3D7), fontSize = 10.sp)
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0x24FFFFFF)).padding(vertical = 10.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 10.dp)) {
                    Text("趋势", color = Color(0xFFCBE4F2), fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.weight(1f))
                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        WaterRange.entries.forEach { option ->
                            Text(option.label, color = if (range == option) Color.White else Color(0xFF9BC3D7), fontSize = 8.5.sp,
                                modifier = Modifier.clip(DetailTokens.PillShape).background(if (range == option) Color(0x24FFFFFF) else Color.Transparent)
                                    .border(1.dp, if (range == option) Color(0x44FFFFFF) else Color.Transparent, DetailTokens.PillShape)
                                    .clickable { onSelectRange(option) }.padding(horizontal = 7.dp, vertical = 5.dp))
                        }
                    }
                }
                Column(Modifier.padding(top = 7.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    parameters.chunked(4).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            row.forEach { parameter ->
                                val on = selected?.code == parameter.code
                                Text(parameter.label, color = if (on) Color(0xFF062640) else Color(0xFF9BC3D7), fontSize = 9.5.sp,
                                    modifier = Modifier.clip(DetailTokens.PillShape).background(if (on) Color(0xFF5FE0CD) else Color.Transparent)
                                        .border(1.dp, if (on) Color(0xFF5FE0CD) else Color(0x28FFFFFF), DetailTokens.PillShape)
                                        .clickable { onSelectCode(parameter.code) }.padding(horizontal = 9.dp, vertical = 5.dp))
                            }
                        }
                    }
                }
                if (points.isEmpty()) {
                    Box(Modifier.fillMaxWidth().height(118.dp), contentAlignment = Alignment.Center) {
                        Text(if (selected == null) "先选择一个参数" else "这个参数还没有实测值", color = Color(0xFF9BC3D7), fontSize = 10.sp)
                    }
                } else {
                    WaterTrendChart(points, Modifier.fillMaxWidth(), selected?.unit.orEmpty())
                    if (points.size == 1) Text("仅 1 个测量点，再记录一次即可形成趋势", color = Color(0xFF8FB8CE), fontSize = 8.5.sp, modifier = Modifier.padding(top = 2.dp))
                }
                WaterHeroStats(points, selected?.unit.orEmpty())
            }
        }
    }
}

@Composable
private fun WaterTrendChart(points: List<WaterPoint>, modifier: Modifier = Modifier, unit: String) {
    val startLabel = SimpleDateFormat("MM/dd", Locale.getDefault()).format(Date(points.first().at))
    val endLabel = SimpleDateFormat("MM/dd", Locale.getDefault()).format(Date(points.last().at))
    Column(modifier) {
        Canvas(Modifier.fillMaxWidth().height(104.dp)) {
            if (points.isEmpty()) return@Canvas
            val left = 8.dp.toPx()
            val right = size.width - 8.dp.toPx()
            val top = 8.dp.toPx()
            val bottom = size.height - 8.dp.toPx()
            val values = points.map { it.value }
            val low = values.minOrNull() ?: return@Canvas
            val high = values.maxOrNull() ?: return@Canvas
            val spread = max(high - low, max(kotlin.math.abs(high) * 0.08, 0.1))
            val coords = points.mapIndexed { index, point ->
                val x = if (points.size == 1) (left + right) / 2f else left + index * (right - left) / (points.size - 1)
                val y = bottom - ((point.value - low) / spread * (bottom - top)).toFloat()
                Offset(x, y)
            }
            for (i in 1..2) {
                val y = top + (bottom - top) * i / 3f
                drawLine(Color(0x1FFFFFFF), Offset(left, y), Offset(right, y), 1.dp.toPx())
            }
            if (coords.size > 1) {
                val line = Path().apply {
                    moveTo(coords.first().x, coords.first().y)
                    coords.drop(1).forEach { lineTo(it.x, it.y) }
                }
                val area = Path().apply {
                    moveTo(coords.first().x, bottom)
                    lineTo(coords.first().x, coords.first().y)
                    coords.drop(1).forEach { lineTo(it.x, it.y) }
                    lineTo(coords.last().x, bottom)
                    close()
                }
                drawPath(area, Brush.verticalGradient(listOf(Color(0x442AC9B9), Color(0x002AC9B9))), style = androidx.compose.ui.graphics.drawscope.Fill)
                drawPath(line, Brush.horizontalGradient(listOf(Color(0xFF5FE0CD), Color(0xFF6FA8FF))), style = Stroke(2.4.dp.toPx(), cap = StrokeCap.Round))
            }
            coords.forEachIndexed { index, point ->
                drawCircle(Color(0xFF0A3554), radius = if (index == coords.lastIndex) 5.dp.toPx() else 3.5.dp.toPx(), center = point)
                drawCircle(Color(0xFF5FE0CD), radius = if (index == coords.lastIndex) 3.2.dp.toPx() else 2.dp.toPx(), center = point)
            }
            val last = coords.last()
            val text = points.last().value.prettyNumber()
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFF062640.toInt()
                textSize = 8.sp.toPx()
                textAlign = Paint.Align.CENTER
                typeface = android.graphics.Typeface.DEFAULT_BOLD
            }
            val bubbleW = paint.measureText(text) + 12.dp.toPx()
            val bubbleH = 15.dp.toPx()
            val bubbleX = (last.x - bubbleW / 2).coerceIn(0f, size.width - bubbleW)
            val bubbleY = (last.y - bubbleH - 7.dp.toPx()).coerceAtLeast(0f)
            drawContext.canvas.nativeCanvas.drawRoundRect(RectF(bubbleX, bubbleY, bubbleX + bubbleW, bubbleY + bubbleH), 7.dp.toPx(), 7.dp.toPx(), Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF5FE0CD.toInt() })
            drawContext.canvas.nativeCanvas.drawText(text, bubbleX + bubbleW / 2, bubbleY + bubbleH * 0.7f, paint)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(startLabel, color = Color(0xFF7FA6BC), fontSize = 8.sp)
            Text(endLabel, color = Color(0xFF7FA6BC), fontSize = 8.sp)
        }
    }
}

@Composable
private fun WaterHeroStats(points: List<WaterPoint>, unit: String) {
    val values = points.map { it.value }
    val min = values.minOrNull()
    val max = values.maxOrNull()
    val delta = if (points.size >= 2) points.last().value - points.first().value else null
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        DeepMetric("最低", min?.prettyNumber() ?: "—", if (min != null) unit else null, Modifier.weight(1f))
        DeepMetric("最高", max?.prettyNumber() ?: "—", if (max != null) unit else null, Modifier.weight(1f))
        DeepMetric("变化", delta?.let { (if (it > 0) "+" else "") + it.prettyNumber() } ?: "—", if (delta != null) unit else null, Modifier.weight(1f))
        DeepMetric("采样点", points.size.toString(), "个", Modifier.weight(1f))
    }
}

@Composable
private fun WaterLatestMetric(parameter: WaterParameter, value: WaterTestValue, modifier: Modifier = Modifier) {
    val suspicious = parameter.code == "salinity" && value.value != null && value.unit in listOf("ppt", "PSU") && value.value in 1.0..1.05
    Column(modifier.padding(end = 8.dp)) {
        Text(parameter.label, color = Color(0xFF8FB8CE), fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 2.dp)) {
            Text(value.value?.let { it.prettyNumber() } ?: "—", color = Color(0xFFF4FBFF), fontSize = 21.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(value.unit.orEmpty(), color = Color(0xFF9BC3D7), fontSize = 8.5.sp, modifier = Modifier.padding(start = 2.dp, bottom = 2.dp))
        }
        Text(if (suspicious) "数值像比重" else "实测值", color = if (suspicious) Color(0xFFF6C46B) else Color(0xFF7FA6BC), fontSize = 7.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun WaterHistoryHeader(count: Int) {
    Row(Modifier.fillMaxWidth().padding(top = 5.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        Box(Modifier.width(4.dp).height(15.dp).clip(RoundedCornerShape(2.dp)).background(Brush.verticalGradient(listOf(Color(0xFF5FE0CD), Color(0xFF1D6FD0)))))
        Text("检测历史", color = DetailTokens.TitleInk, fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
        Text("$count 次", color = Color(0xFF4C7E9C), fontSize = 9.sp, modifier = Modifier.clip(DetailTokens.PillShape).background(Color(0xFFE9F2F8)).padding(horizontal = 6.dp, vertical = 2.dp))
        Spacer(Modifier.weight(1f))
        Text("按日期分组", color = DetailTokens.TertiaryInk, fontSize = 9.sp)
    }
}

@Composable
private fun WaterDayHeader(day: String, count: Int) {
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(day, color = DetailTokens.SecondaryInk, fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
        Text("· $count 次检测", color = DetailTokens.TertiaryInk, fontSize = 9.sp)
        Box(Modifier.weight(1f).height(1.dp).background(Color(0xFFE3EEF7)))
    }
}

@Composable
private fun WaterHistoryRow(test: WaterTestWithValues, onOpen: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp)).background(Color.White)
            .border(1.dp, DetailTokens.GlassBorder, RoundedCornerShape(13.dp)).clickable(onClick = onOpen).padding(horizontal = 11.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Text(SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(test.test.measuredAt)), color = DetailTokens.SecondaryInk, fontSize = 9.5.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(38.dp))
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            test.values.take(4).forEach { value ->
                val suspicious = value.parameterCode == "salinity" && value.value != null && value.unit in listOf("ppt", "PSU") && value.value in 1.0..1.05
                Text("${parameterLabel(value.parameterCode)} ${value.value?.prettyNumber() ?: "—"} ${value.unit.orEmpty()}", color = if (suspicious) Color(0xFFA66A1E) else DetailTokens.SecondaryInk, fontSize = 8.5.sp,
                    modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(if (suspicious) Color(0xFFFCF3E2) else Color(0xFFF1F7FB)).padding(horizontal = 6.dp, vertical = 4.dp))
            }
            if (test.values.isEmpty()) Text("无实测值", color = DetailTokens.TertiaryInk, fontSize = 8.5.sp)
        }
        Text("›", color = Color(0xFFA8BECF), fontSize = 16.sp)
    }
}

@Composable
private fun WaterChangeSummary(changes: List<WaterChange>) {
    val latest = changes.maxByOrNull { it.completedAt }
    val monthCount = changes.count { sameWaterMonth(it.completedAt, System.currentTimeMillis()) }
    val avgDays = if (changes.size >= 2) {
        val sorted = changes.sortedBy { it.completedAt }
        val gaps = sorted.zipWithNext { a, b -> (b.completedAt - a.completedAt).coerceAtLeast(0L) / 86_400_000.0 }
        gaps.average().takeIf { it > 0 }?.let { "%.1f".format(Locale.getDefault(), it) }
    } else null
    DeepWaterCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("换水记录", color = Color(0xFF8FB8CE), fontSize = 9.5.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                Text("共 ${changes.size} 次", color = Color(0xFF8FEDDF), fontSize = 9.sp)
            }
            if (latest == null) {
                Text("还没有换水记录", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 5.dp))
                Text("换水后记录实际换水量，比例会按鱼缸容量自动计算。", color = Color(0xFF9BC3D7), fontSize = 9.5.sp, modifier = Modifier.padding(top = 4.dp))
            } else {
                Row(Modifier.padding(top = 5.dp), verticalAlignment = Alignment.Bottom) {
                    Column(Modifier.weight(1f)) {
                        Text("最近换水", color = Color(0xFF8FB8CE), fontSize = 8.5.sp)
                        Text(waterDayLabel(latest.completedAt), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 1.dp))
                        Text(waterTimeLabel(latest.completedAt), color = Color(0xFF9BC3D7), fontSize = 8.5.sp)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("换水量", color = Color(0xFF8FB8CE), fontSize = 8.5.sp)
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(latest.volumeValue?.prettyNumber() ?: "—", color = Color(0xFFF4FBFF), fontSize = 21.sp, fontWeight = FontWeight.Bold)
                            Text(latest.volumeUnit.orEmpty(), color = Color(0xFF9BC3D7), fontSize = 8.5.sp, modifier = Modifier.padding(start = 2.dp, bottom = 2.dp))
                        }
                        latest.ratio?.let { Text("约 ${formatWaterChangeRatio(it)}%", color = Color(0xFF8FEDDF), fontSize = 8.5.sp) }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                ChangeStat("最近换水", latest?.let { waterDayLabel(it.completedAt).replace(" ", "") } ?: "—", Modifier.weight(1f))
                ChangeStat("本月", "$monthCount 次", Modifier.weight(1f))
                ChangeStat("平均间隔", avgDays?.let { "$it 天" } ?: "—", Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ChangeStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, color = Color(0xFF8FB8CE), fontSize = 8.sp)
        Text(value, color = Color(0xFFF4FBFF), fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 1.dp))
    }
}

@Composable
private fun WaterChangeEmptyHint() {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(15.dp)).background(Color.White).border(1.dp, DetailTokens.GlassBorder, RoundedCornerShape(15.dp)).padding(13.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("换水记录会显示", color = DetailTokens.TitleInk, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("换水量", "比例", "前后水温").forEach { label ->
                Text(label, color = DetailTokens.SecondaryInk, fontSize = 8.5.sp, modifier = Modifier.clip(DetailTokens.PillShape).background(Color(0xFFF1F7FB)).padding(horizontal = 7.dp, vertical = 4.dp))
            }
        }
        Text("点底部「记录换水」新增；已有记录点开可编辑。比例按鱼缸容量计算，不会自动生成记录。", color = DetailTokens.TertiaryInk, fontSize = 9.sp, lineHeight = 13.sp)
    }
}

@Composable
private fun WaterChangeMonthHeader(month: String, count: Int) {
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(month, color = DetailTokens.SecondaryInk, fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
        Text("· $count 次", color = DetailTokens.TertiaryInk, fontSize = 9.sp)
        Box(Modifier.weight(1f).height(1.dp).background(Color(0xFFE3EEF7)))
    }
}

@Composable
private fun WaterChangeRow(change: WaterChange, onClick: () -> Unit) {
    val temps = if (change.beforeTemperature != null || change.afterTemperature != null) {
        "水温 ${change.beforeTemperature?.prettyNumber() ?: "—"} → ${change.afterTemperature?.prettyNumber() ?: "—"} ${change.temperatureUnit.orEmpty()}"
    } else null
    val salinity = if (change.beforeSalinity != null || change.afterSalinity != null) {
        "盐度 ${change.beforeSalinity?.prettyNumber() ?: "—"} → ${change.afterSalinity?.prettyNumber() ?: "—"} ${change.salinityUnit.orEmpty()}"
    } else null
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp)).background(Color.White)
            .border(1.dp, DetailTokens.GlassBorder, RoundedCornerShape(13.dp)).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(waterDayLabel(change.completedAt), color = DetailTokens.SecondaryInk, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                Text(waterTimeLabel(change.completedAt), color = DetailTokens.TertiaryInk, fontSize = 8.5.sp, modifier = Modifier.padding(start = 5.dp))
                Spacer(Modifier.weight(1f))
                Text("编辑", color = DetailTokens.Primary, fontSize = 8.5.sp)
            }
            Row(verticalAlignment = Alignment.Bottom) {
                Text(change.volumeValue?.let { "${it.prettyNumber()} ${change.volumeUnit.orEmpty()}" } ?: "换水量未记录", color = DetailTokens.TitleInk, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                change.ratio?.let { Text("约 ${formatWaterChangeRatio(it)}%", color = DetailTokens.Primary, fontSize = 8.5.sp, modifier = Modifier.padding(start = 7.dp, bottom = 3.dp).clip(DetailTokens.PillShape).background(Color(0xFFEAF4FF)).padding(horizontal = 6.dp, vertical = 2.dp)) }
            }
            listOfNotNull(temps, salinity).forEach { Text(it, color = DetailTokens.SecondaryInk, fontSize = 8.5.sp) }
            (change.supplies ?: change.notes)?.takeIf { it.isNotBlank() }?.let {
                Text(it, color = DetailTokens.TertiaryInk, fontSize = 8.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Text("›", color = Color(0xFFA8BECF), fontSize = 17.sp, modifier = Modifier.padding(start = 8.dp))
    }
}

private fun sameWaterMonth(a: Long, b: Long): Boolean {
    val ca = Calendar.getInstance().apply { timeInMillis = a }
    val cb = Calendar.getInstance().apply { timeInMillis = b }
    return ca.get(Calendar.YEAR) == cb.get(Calendar.YEAR) && ca.get(Calendar.MONTH) == cb.get(Calendar.MONTH)
}

private fun waterMonthLabel(at: Long): String = SimpleDateFormat("yyyy 年 M 月", Locale.getDefault()).format(Date(at))

@Composable
private fun EmptyWaterTrend(onRecord: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        DeepWaterCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("还没有水质检测", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text("记录第一次实测值后，这里会出现最近水况和趋势。", color = Color(0xFF9BC3D7), fontSize = 10.sp, modifier = Modifier.padding(top = 4.dp))
            }
        }
        EmptyState("水质趋势需要一条真实记录", "不会用默认值生成趋势；未测参数保持为空。")
        Button(onClick = onRecord, shape = DetailTokens.ButtonShape) { Text("记录水质") }
    }
}

@Composable
private fun WaterBottomBar(isWater: Boolean, count: Int, latestAt: Long?, onClick: () -> Unit) {
    Surface(color = Color(0xFAFFFFFF), shadowElevation = 12.dp) {
        Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                Text(if (isWater) "已记录 $count 次检测" else "共 $count 次换水", color = DetailTokens.TitleInk, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                Text(latestAt?.let { "最近 ${waterDayLabel(it)} ${waterTimeLabel(it)}" } ?: if (isWater) "还没有记录" else "记录第一次换水", color = DetailTokens.TertiaryInk, fontSize = 9.sp)
            }
            Button(onClick = onClick, shape = DetailTokens.PillShape, modifier = Modifier.height(48.dp), contentPadding = PaddingValues(horizontal = 20.dp)) {
                Text(if (isWater) "记录水质" else "记录换水", fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun WaterTestDetailSheet(test: WaterTestWithValues, onClose: () -> Unit, onTemplate: () -> Unit) {
    Column(Modifier.padding(start = 18.dp, end = 18.dp, bottom = 22.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text("检测详情", color = DetailTokens.TitleInk, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Text("${waterDayLabel(test.test.measuredAt)} ${waterTimeLabel(test.test.measuredAt)}", color = DetailTokens.TertiaryInk, fontSize = 9.5.sp, modifier = Modifier.padding(start = 7.dp))
        }
        val values = test.values
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            values.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { value ->
                        Column(Modifier.weight(1f).clip(RoundedCornerShape(13.dp)).background(Color.White).border(1.dp, DetailTokens.GlassBorder, RoundedCornerShape(13.dp)).padding(10.dp)) {
                            Text(parameterLabel(value.parameterCode), color = DetailTokens.TertiaryInk, fontSize = 9.sp)
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(value.value?.prettyNumber() ?: "—", color = DetailTokens.TitleInk, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                                Text(value.unit.orEmpty(), color = DetailTokens.TertiaryInk, fontSize = 8.5.sp, modifier = Modifier.padding(start = 2.dp, bottom = 2.dp))
                            }
                            val suspicious = value.parameterCode == "salinity" && value.value != null && value.unit in listOf("ppt", "PSU") && value.value in 1.0..1.05
                            if (suspicious) Text("数值像比重，建议改记比重", color = Color(0xFFB77E2B), fontSize = 7.5.sp)
                        }
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
        val meta = listOfNotNull(
            test.test.method?.takeIf { it.isNotBlank() }?.let { "测量方式：$it" },
            test.test.notes?.takeIf { it.isNotBlank() }?.let { "备注：$it" },
            test.values.firstOrNull { it.targetMin != null || it.targetMax != null }?.let { "目标范围：已记录" },
        )
        if (meta.isNotEmpty()) Text(meta.joinToString(" · "), color = DetailTokens.TertiaryInk, fontSize = 9.sp)
        Text("详情只读；需要修改请重新记录一次，避免覆盖历史测量。", color = DetailTokens.TertiaryInk, fontSize = 9.sp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onClose) { Text("关闭") }
            Spacer(Modifier.weight(1f))
            Button(onClick = onTemplate, shape = DetailTokens.PillShape) { Text("以此为模板再记一次") }
        }
    }
}

private fun waterTankTypeLabel(type: TankType): String = if (type == TankType.saltwater) "海水缸" else "淡水缸"
private fun waterDayKey(at: Long): String = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(at))
private fun waterDayLabel(at: Long): String = SimpleDateFormat("M 月 d 日", Locale.getDefault()).format(Date(at))
private fun waterTimeLabel(at: Long): String = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(at))
