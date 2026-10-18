@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.guanyji.app.feature.statistics

import android.app.Application
import android.app.DatePickerDialog
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.guanyji.app.core.designsystem.DeepWaterCard
import com.guanyji.app.core.designsystem.DetailTokens
import com.guanyji.app.core.designsystem.SubpageTopBar
import com.guanyji.app.data.local.Tank
import com.guanyji.app.feature.profile.AppPreferences
import com.guanyji.app.feature.overview.prettyNumber
import com.guanyji.app.feature.profile.ProfileViewModel
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private enum class ReportRange(val label: String, val days: Long?) {
    Days7("7 天", 7), Days30("30 天", 30), Days90("90 天", 90), Custom("自定义", null),
}

private enum class ReportTrendTab(val label: String) { Feeding("喂食"), Change("换水"), Water("水质"), Growth("成长") }

private enum class ChangeMeasure(val label: String) { Count("次数"), Volume("水量") }

private enum class GrowthMeasure(val label: String, val suffix: String) { Length("体长", "length"), Weight("体重", "weight") }

@Composable
fun StatisticsScreen(
    onBack: () -> Unit,
    onAddRecord: (String, String?) -> Unit,
    onOpenRecords: (String, String?) -> Unit = { _, _ -> },
    vm: StatisticsViewModel = viewModel(factory = StatisticsViewModel.factory(LocalContext.current.applicationContext as Application)),
) {
    val profile: ProfileViewModel = viewModel(factory = ProfileViewModel.factory(LocalContext.current.applicationContext as Application))
    val preferences by profile.preferences.collectAsStateWithLifecycle()
    val tanks by vm.tanks.collectAsStateWithLifecycle()
    val filter by vm.filter.collectAsStateWithLifecycle()
    val report by vm.report.collectAsStateWithLifecycle()
    var tankMenu by remember { mutableStateOf(false) }
    var customSheet by remember { mutableStateOf(false) }
    var tabName by rememberSaveable { mutableStateOf(ReportTrendTab.Feeding.name) }
    var waterCode by rememberSaveable { mutableStateOf<String?>(null) }
    var changeMeasureName by rememberSaveable { mutableStateOf(ChangeMeasure.Count.name) }
    var growthFishId by rememberSaveable { mutableStateOf<String?>(null) }
    var growthMeasureName by rememberSaveable { mutableStateOf(GrowthMeasure.Length.name) }

    val reportState = report
    val result = reportState?.getOrNull()
    val selectedTank = tanks.firstOrNull { it.id == filter.tankId }
    val topSubtitle = buildString {
        append(selectedTank?.name ?: "全部鱼缸")
        result?.let { append(" · ${it.activeFish} 条在养") }
        append(" · 本地记录")
    }
    val today = LocalDate.now()
    val selectedPreset = ReportRange.entries.firstOrNull {
        it.days != null && filter.through == today && filter.from == today.minusDays(it.days - 1)
    }

    Scaffold(
        containerColor = DetailTokens.PageBackground,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { SubpageTopBar("统计报表", topSubtitle, onBack) },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 9.dp, bottom = 26.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            item {
                ReportFilterBar(
                    range = selectedPreset,
                    custom = selectedPreset == null,
                    from = filter.from,
                    through = filter.through,
                    tankName = selectedTank?.name ?: "全部鱼缸",
                    tankMenu = tankMenu,
                    onTankMenu = { tankMenu = it },
                    tanks = tanks,
                    onRange = { vm.setRange(it.days!!) },
                    onCustom = { customSheet = true },
                    onSelectTank = { vm.setTank(it) },
                    datePattern = preferences.dateFormat,
                )
            }
            if (reportState == null) {
                item { ReportLoading() }
            } else if (reportState.isFailure) {
                item { ReportError(reportState.exceptionOrNull()?.message) }
            } else if (result != null) {
                item { ReportOverview(result, filter, tanks, preferences) }
                item { ReportTrendHeader(tabName) { tabName = it.name } }
                val tab = ReportTrendTab.entries.firstOrNull { it.name == tabName } ?: ReportTrendTab.Feeding
                val waterParams = result.waterParameters.keys.sortedBy { waterParameterOrder(it) }
                val effectiveWaterCode = waterCode?.takeIf { it in waterParams } ?: waterParams.firstOrNull()
                val growthFish = result.fishGrowth.entries.groupBy { it.key.substringBeforeLast('_') }
                val fishId = growthFishId?.takeIf { it in growthFish.keys } ?: growthFish.keys.firstOrNull()
                val growthKeys = fishId?.let { id -> result.fishGrowth.keys.filter { it.startsWith("${id}_") } }.orEmpty()
                val requestedGrowthMeasure = GrowthMeasure.entries.firstOrNull { it.name == growthMeasureName } ?: GrowthMeasure.Length
                val growthMeasure = if ("${fishId}_${requestedGrowthMeasure.suffix}" in growthKeys) requestedGrowthMeasure
                    else if (growthKeys.any { it.endsWith("_length") }) GrowthMeasure.Length
                    else if (growthKeys.any { it.endsWith("_weight") }) GrowthMeasure.Weight
                    else requestedGrowthMeasure
                val series = when (tab) {
                    ReportTrendTab.Feeding -> ReportSeries(
                        title = "每日喂食",
                        points = result.feedingTrend,
                        unit = "次",
                        precision = 0,
                        emptyMessage = "这个范围没有喂食记录",
                        emptyAction = "记录喂食",
                        onEmptyAction = { onAddRecord("feeding", filter.tankId) },
                    )
                    ReportTrendTab.Change -> {
                        val measure = ChangeMeasure.entries.firstOrNull { it.name == changeMeasureName } ?: ChangeMeasure.Count
                        val points = if (measure == ChangeMeasure.Count) result.waterChangeTrend else convertVolumePoints(result.waterChangeVolumeTrend, preferences)
                        ReportSeries(
                            title = if (measure == ChangeMeasure.Count) "每日换水次数" else "每日换水量",
                            points = points,
                            unit = if (measure == ChangeMeasure.Count) "次" else preferences.volume.ifBlank { "L" },
                            precision = if (measure == ChangeMeasure.Count) 0 else 1,
                            emptyMessage = if (measure == ChangeMeasure.Count) "这个范围没有换水记录" else "换水记录没有填写水量",
                            emptyAction = "记录换水",
                            onEmptyAction = { onAddRecord("change", filter.tankId) },
                            selector = {
                                Segmented(
                                    options = ChangeMeasure.entries.map { it.label },
                                    selected = measure.label,
                                    onSelect = { changeMeasureName = ChangeMeasure.entries.first { option -> option.label == it }.name },
                                )
                            },
                        )
                    }
                    ReportTrendTab.Water -> {
                        val code = effectiveWaterCode
                        val points = code?.let { result.waterParameters[it] }.orEmpty().let { convertWaterPoints(code, it, preferences) }
                        ReportSeries(
                            title = code?.let(::waterParameterName) ?: "水质参数",
                            points = points,
                            unit = points.firstOrNull()?.unit.orEmpty(),
                            precision = waterPrecision(code),
                            emptyMessage = if (waterParams.isEmpty()) "这个范围没有水质实测值" else "这个参数还没有实测值",
                            emptyAction = "记录水质",
                            onEmptyAction = { onAddRecord("water", filter.tankId) },
                            selector = {
                                Column(Modifier.padding(top = 2.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                    waterParams.chunked(4).forEach { row ->
                                        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                            row.forEach { parameter ->
                                                ReportChip(
                                                    text = waterParameterName(parameter),
                                                    selected = parameter == code,
                                                    onClick = { waterCode = parameter },
                                                )
                                            }
                                        }
                                    }
                                    if (waterParams.isEmpty()) Text("暂无参数", color = DetailTokens.TertiaryInk, fontSize = 9.sp)
                                }
                            },
                        )
                    }
                    ReportTrendTab.Growth -> {
                        val fishEntries = growthFish[fishId].orEmpty()
                        val key = fishId?.let { "${it}_${growthMeasure.suffix}" }
                        val seriesKey = fishEntries.firstOrNull { it.key == key }?.key
                        val points = seriesKey?.let { result.fishGrowth[it] }.orEmpty().let { convertGrowthPoints(seriesKey, it, preferences) }
                        val fishName = fishEntries.firstOrNull()?.value?.firstOrNull()?.label ?: "选择鱼只"
                        ReportSeries(
                            title = if (seriesKey == null) "成长记录" else "$fishName · ${growthMeasure.label}",
                            points = points,
                            unit = points.firstOrNull()?.unit.orEmpty(),
                            precision = if (growthMeasure == GrowthMeasure.Weight) 0 else 1,
                            emptyMessage = if (growthKeysEmpty(result)) "这个范围没有成长测量" else "这条鱼还没有${growthMeasure.label}记录",
                            emptyAction = "记录成长",
                            onEmptyAction = { onAddRecord("growth", filter.tankId) },
                            selector = {
                                if (growthFish.isEmpty()) {
                                    Text("暂无成长记录；先到鱼只成长档案记录体长或体重。", color = DetailTokens.TertiaryInk, fontSize = 9.sp)
                                } else {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        FishDropdown(fishGroups = growthFish, selected = fishId, onSelect = { growthFishId = it })
                                        Spacer(Modifier.weight(1f))
                                        Segmented(
                                            options = GrowthMeasure.entries.map { it.label },
                                            selected = growthMeasure.label,
                                            onSelect = { growthMeasureName = GrowthMeasure.entries.first { option -> option.label == it }.name },
                                        )
                                    }
                                }
                            },
                        )
                    }
                }
                item { ReportChartCard(series, preferences.dateFormat) }
                item { ReportRecords(result, filter.tankId, preferences, onOpenRecords) }
                item {
                    Text(
                        "统计只基于本机已保存记录；未记录日期不补零，没有数据的指标不生成空图表。",
                        color = DetailTokens.TertiaryInk, fontSize = 8.5.sp, modifier = Modifier.padding(horizontal = 3.dp, vertical = 2.dp),
                    )
                }
            }
        }
    }
    if (customSheet) {
        ReportRangeSheet(
            from = filter.from,
            through = filter.through,
            onDismiss = { customSheet = false },
            onApply = { from, through -> vm.setDates(from, through); customSheet = false },
            datePattern = preferences.dateFormat,
        )
    }
}

private data class ReportSeries(
    val title: String,
    val points: List<TrendPoint>,
    val unit: String,
    val precision: Int,
    val emptyMessage: String,
    val emptyAction: String,
    val onEmptyAction: () -> Unit,
    val selector: (@Composable () -> Unit)? = null,
)

@Composable
private fun ReportFilterBar(
    range: ReportRange?,
    custom: Boolean,
    from: LocalDate,
    through: LocalDate,
    tankName: String,
    tankMenu: Boolean,
    onTankMenu: (Boolean) -> Unit,
    tanks: List<Tank>,
    onRange: (ReportRange) -> Unit,
    onCustom: () -> Unit,
    onSelectTank: (String?) -> Unit,
    datePattern: String,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Text("报表期", color = DetailTokens.TertiaryInk, fontSize = 9.5.sp, modifier = Modifier.padding(end = 2.dp))
            ReportRange.entries.filter { it.days != null }.forEach { option ->
                ReportChip(option.label, range == option) { onRange(option) }
            }
            ReportChip("自定义", custom, onCustom)
            Spacer(Modifier.weight(1f))
            Box {
                Text(
                    "$tankName ⌄", color = DetailTokens.SecondaryInk, fontSize = 9.sp,
                    modifier = Modifier.clip(DetailTokens.PillShape).background(Color(0xFFE9F2F8)).clickable { onTankMenu(true) }.padding(horizontal = 8.dp, vertical = 5.dp),
                )
                DropdownMenu(expanded = tankMenu, onDismissRequest = { onTankMenu(false) }) {
                    DropdownMenuItem(text = { Text("全部鱼缸") }, onClick = { onSelectTank(null); onTankMenu(false) })
                    tanks.forEach { tank -> DropdownMenuItem(text = { Text(tank.name) }, onClick = { onSelectTank(tank.id); onTankMenu(false) }) }
                }
            }
        }
        Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${from.format(java.time.format.DateTimeFormatter.ofPattern("yyyy.MM.dd"))} – ${through.format(java.time.format.DateTimeFormatter.ofPattern("yyyy.MM.dd"))}",
                color = DetailTokens.SecondaryInk, fontSize = 9.sp, fontWeight = FontWeight.SemiBold,
            )
            Text(" · 共 ${through.toEpochDay() - from.toEpochDay() + 1} 天", color = DetailTokens.TertiaryInk, fontSize = 9.sp)
        }
    }
}

@Composable
private fun ReportChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text,
        color = if (selected) Color.White else Color(0xFF5B7690),
        fontSize = 9.sp,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        modifier = Modifier.clip(DetailTokens.PillShape)
            .background(if (selected) DetailTokens.Primary else Color.White)
            .border(1.dp, if (selected) DetailTokens.Primary else Color(0xFFE3EEF7), DetailTokens.PillShape)
            .clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 5.dp),
        maxLines = 1,
    )
}

@Composable
private fun Segmented(options: List<String>, selected: String, onSelect: (String) -> Unit) {
    Row(Modifier.clip(RoundedCornerShape(8.dp)).background(Color(0xFFEDF4FA)).padding(2.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        options.forEach { option ->
            Text(
                option,
                color = if (selected == option) DetailTokens.Primary else DetailTokens.TertiaryInk,
                fontSize = 8.sp,
                fontWeight = if (selected == option) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(if (selected == option) Color.White else Color.Transparent)
                    .clickable { onSelect(option) }.padding(horizontal = 7.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun ReportOverview(result: StatisticsSummary, filter: StatisticsFilter, tanks: List<Tank>, preferences: AppPreferences) {
    val volume = result.waterChangeVolume?.let { formatMeasurement(it, result.waterChangeUnit ?: "L", preferences.volume) }
    val capacityLiters = capacityLiters(tanks, filter.tankId)
    val volumeLiters = result.waterChangeVolume?.let { formatMeasurement(it, result.waterChangeUnit ?: "L", "L").first }
    val ratio = if (volumeLiters != null && capacityLiters != null && capacityLiters > 0) volumeLiters / capacityLiters * 100 else null
    DeepWaterCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("本期概览", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Text("${filter.from.monthValue} 月 ${filter.from.dayOfMonth} 日 – ${filter.through.monthValue} 月 ${filter.through.dayOfMonth} 日", color = Color(0xFF8FB8CE), fontSize = 8.5.sp)
            }
            Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(13.dp)) {
                ReportMetric("喂食", "${result.feedingEvents}", "次", "${oneDecimal(result.feedingsPerWeek)} 次/周 · ${result.feedingTrend.size} 天有记录", Modifier.weight(1f))
                ReportMetric("换水", "${result.waterChangeEvents}", "次", volume?.let { "${it.first.prettyNumber()} ${it.second} · ${oneDecimal(result.waterChangesPerWeek)} 次/周" } ?: "暂无换水量", Modifier.weight(1f))
            }
            Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(13.dp)) {
                ReportMetric("水质", "${result.waterTestEvents}", "次", "${result.waterParameters.size} 个参数有实测", Modifier.weight(1f))
                ReportMetric(
                    "任务",
                    result.taskCompletionPercent?.let { "$it%" } ?: "—",
                    null,
                    if (result.taskCompletionPercent == null) "暂无已记录任务" else "${result.completedTasks}/${result.eligibleTasks} 项已明确完成",
                    Modifier.weight(1f),
                )
            }
            Box(Modifier.fillMaxWidth().padding(top = 9.dp).height(1.dp).background(Color(0x18FFFFFF)))
            val insight = buildString {
                if (volume != null) {
                    append("换水 ${volume.first.prettyNumber()} ${volume.second}")
                    if (ratio != null) append("，约占鱼缸容量 ${oneDecimal(ratio)}%")
                } else {
                    append("本期还没有换水水量")
                }
                if (result.feedingTrend.isNotEmpty()) append(" · ${result.feedingTrend.size} 天有喂食记录")
            }
            Text(insight, color = Color(0xFF9BC3D7), fontSize = 8.5.sp, lineHeight = 13.sp, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

@Composable
private fun ReportMetric(label: String, value: String, unit: String?, sub: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, color = Color(0xFF8FB8CE), fontSize = 8.5.sp)
        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 1.dp)) {
            Text(value, color = Color(0xFFF4FBFF), fontSize = 19.sp, fontWeight = FontWeight.Bold)
            unit?.let { Text(it, color = Color(0xFF9BC3D7), fontSize = 8.5.sp, modifier = Modifier.padding(start = 2.dp, bottom = 2.dp)) }
        }
        Text(sub, color = Color(0xFF7FA6BC), fontSize = 7.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ReportTrendHeader(selectedName: String, onSelect: (ReportTrendTab) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(4.dp).height(15.dp).clip(RoundedCornerShape(2.dp)).background(Brush.verticalGradient(listOf(Color(0xFF5FE0CD), Color(0xFF1D6FD0)))))
            Text("趋势", color = DetailTokens.TitleInk, fontSize = 13.5.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 7.dp))
            Spacer(Modifier.weight(1f))
            Text("只显示真实记录", color = DetailTokens.TertiaryInk, fontSize = 8.5.sp)
        }
        Row(Modifier.fillMaxWidth().padding(top = 7.dp), horizontalArrangement = Arrangement.spacedBy(19.dp)) {
            ReportTrendTab.entries.forEach { tab ->
                val on = tab.name == selectedName
                Column(Modifier.width(androidx.compose.foundation.layout.IntrinsicSize.Max).clickable { onSelect(tab) }.padding(bottom = 5.dp)) {
                    Text(tab.label, color = if (on) DetailTokens.TitleInk else DetailTokens.SecondaryInk, fontSize = 10.5.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal)
                    Box(Modifier.padding(top = 5.dp).fillMaxWidth().height(2.dp).clip(RoundedCornerShape(1.dp)).background(if (on) DetailTokens.Primary else Color.Transparent))
                }
            }
        }
    }
}

@Composable
private fun ReportChartCard(series: ReportSeries, datePattern: String) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(17.dp)).background(Color.White)
            .border(1.dp, DetailTokens.GlassBorder, RoundedCornerShape(17.dp)).padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 9.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(series.title, color = DetailTokens.TitleInk, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Text(
                if (series.points.isEmpty()) "暂无数据" else "${series.points.size} 个采样点 · ${formatReportDate(series.points.first().at, "M/d")}–${formatReportDate(series.points.last().at, "M/d")}",
                color = DetailTokens.TertiaryInk, fontSize = 8.sp,
            )
        }
        series.selector?.let { it() }
        if (series.points.isEmpty()) {
            Column(Modifier.fillMaxWidth().height(150.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Text(series.emptyMessage, color = DetailTokens.SecondaryInk, fontSize = 10.sp)
                Text(series.emptyAction, color = DetailTokens.Primary, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 7.dp).clickable { series.onEmptyAction() })
            }
        } else {
            SingleTrendChart(series.points, series.precision, datePattern)
            ReportStats(series.points, series.unit, series.precision)
            if (series.points.size == 1) Text("仅 1 个采样点，再记录一次即可形成趋势", color = DetailTokens.TertiaryInk, fontSize = 8.sp, modifier = Modifier.padding(top = 3.dp))
        }
    }
}

@Composable
private fun SingleTrendChart(points: List<TrendPoint>, precision: Int, datePattern: String) {
    var selectedIndex by remember(points) { mutableStateOf(points.lastIndex) }
    Column {
        Canvas(Modifier.fillMaxWidth().height(140.dp).pointerInput(points) {
            detectTapGestures { tap ->
                if (points.isNotEmpty()) {
                    val left = 7.dp.toPx()
                    val right = size.width - 7.dp.toPx()
                    selectedIndex = if (points.size == 1) 0 else ((tap.x - left) / (right - left).coerceAtLeast(1f) * (points.size - 1)).roundToInt().coerceIn(0, points.lastIndex)
                }
            }
        }) {
            if (points.isEmpty()) return@Canvas
            val left = 7.dp.toPx()
            val right = size.width - 7.dp.toPx()
            val top = 18.dp.toPx()
            val bottom = size.height - 8.dp.toPx()
            val values = points.map { it.value }
            val low = values.minOrNull() ?: return@Canvas
            val high = values.maxOrNull() ?: return@Canvas
            val spread = max(high - low, max(abs(high) * 0.08, 0.1))
            val coords = points.mapIndexed { index, point ->
                val x = if (points.size == 1) (left + right) / 2 else left + index * (right - left) / (points.size - 1)
                val y = bottom - ((point.value - low) / spread * (bottom - top)).toFloat()
                Offset(x, y)
            }
            for (i in 1..2) {
                val y = top + (bottom - top) * i / 3f
                drawLine(Color(0xFFEDF4FA), Offset(left, y), Offset(right, y), 1.dp.toPx())
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
                drawPath(area, Brush.verticalGradient(listOf(Color(0x225FE0CD), Color(0x005FE0CD))), style = androidx.compose.ui.graphics.drawscope.Fill)
                drawPath(line, Brush.horizontalGradient(listOf(Color(0xFF5FE0CD), Color(0xFF3D8BDA))), style = Stroke(2.3.dp.toPx(), cap = StrokeCap.Round))
            }
            coords.forEachIndexed { index, point ->
                drawCircle(Color.White, radius = if (index == selectedIndex) 6.dp.toPx() else 4.dp.toPx(), center = point)
                drawCircle(Color(0xFF2FB9AB), radius = if (index == selectedIndex) 4.dp.toPx() else 2.5.dp.toPx(), center = point)
            }
            val chosen = coords[selectedIndex.coerceIn(0, coords.lastIndex)]
            val text = formatReportNumber(points[selectedIndex.coerceIn(0, points.lastIndex)].value, precision)
            val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFF062640.toInt()
                textSize = 8.sp.toPx()
                textAlign = android.graphics.Paint.Align.CENTER
                typeface = android.graphics.Typeface.DEFAULT_BOLD
            }
            val bubbleW = paint.measureText(text) + 12.dp.toPx()
            val bubbleH = 15.dp.toPx()
            val bx = (chosen.x - bubbleW / 2).coerceIn(0f, size.width - bubbleW)
            val by = (chosen.y - bubbleH - 7.dp.toPx()).coerceAtLeast(0f)
            drawContext.canvas.nativeCanvas.drawRoundRect(android.graphics.RectF(bx, by, bx + bubbleW, by + bubbleH), 7.dp.toPx(), 7.dp.toPx(), android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF5FE0CD.toInt() })
            drawContext.canvas.nativeCanvas.drawText(text, bx + bubbleW / 2, by + bubbleH * 0.7f, paint)
        }
        Row(Modifier.fillMaxWidth().padding(top = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatReportDate(points.first().at, "M/d"), color = DetailTokens.TertiaryInk, fontSize = 7.5.sp)
            Text(formatReportDate(points.last().at, "M/d"), color = DetailTokens.TertiaryInk, fontSize = 7.5.sp)
        }
    }
}


@Composable
private fun ReportStats(points: List<TrendPoint>, unit: String, precision: Int) {
    val values = points.map { it.value }
    val latest = points.last()
    val minValue = values.minOrNull() ?: 0.0
    val maxValue = values.maxOrNull() ?: 0.0
    val delta = if (points.size >= 2) latest.value - points.first().value else null
    Row(Modifier.fillMaxWidth().padding(top = 7.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        StatCell("最新", formatReportNumber(latest.value, precision), unit, Modifier.weight(1f))
        StatCell("最低", formatReportNumber(minValue, precision), unit, Modifier.weight(1f))
        StatCell("最高", formatReportNumber(maxValue, precision), unit, Modifier.weight(1f))
        StatCell("变化", delta?.let { (if (it > 0) "+" else "") + formatReportNumber(it, precision) } ?: "—", if (delta != null) unit else null, Modifier.weight(1f))
    }
}

@Composable
private fun StatCell(label: String, value: String, unit: String?, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, color = DetailTokens.TertiaryInk, fontSize = 7.5.sp)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, color = DetailTokens.TitleInk, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
            unit?.let { Text(it, color = DetailTokens.TertiaryInk, fontSize = 7.5.sp, modifier = Modifier.padding(start = 1.dp, bottom = 1.dp)) }
        }
    }
}

@Composable
private fun ReportRecords(result: StatisticsSummary, tankId: String?, preferences: AppPreferences, onOpen: (String, String?) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 3.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 2.dp, vertical = 2.dp)) {
            Text("本期记录", color = DetailTokens.TitleInk, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Text("点开查看对应记录", color = DetailTokens.TertiaryInk, fontSize = 8.5.sp)
        }
        ReportRecordRow(
            tag = "喂",
            tint = Color(0xFFE2F8F3),
            ink = Color(0xFF0E9C86),
            title = "喂食 ${result.feedingTrend.size} 天有记录",
            subtitle = "${result.feedingEvents} 次 · ${oneDecimal(result.feedingsPerWeek)} 次/周",
            right = "查看 ›",
            onClick = { onOpen("feeding", tankId) },
        )
        ReportRecordRow(
            tag = "换",
            tint = Color(0xFFEAF3FF),
            ink = Color(0xFF1D6FD0),
            title = "换水 ${result.waterChangeEvents} 次",
            subtitle = result.waterChangeVolume?.let {
                val display = formatMeasurement(it, result.waterChangeUnit ?: "L", preferences.volume)
                "${display.first.prettyNumber()} ${display.second} · ${oneDecimal(result.waterChangesPerWeek)} 次/周"
            } ?: "未填写换水量",
            right = "查看 ›",
            onClick = { onOpen("change", tankId) },
        )
        ReportRecordRow(
            tag = "水",
            tint = Color(0xFFE7F8FF),
            ink = Color(0xFF1187A8),
            title = "水质 ${result.waterTestEvents} 次检测",
            subtitle = "${result.waterParameters.size} 个参数有实测值",
            right = "查看 ›",
            onClick = { onOpen("water", tankId) },
        )
    }
}

@Composable
private fun ReportRecordRow(tag: String, tint: Color, ink: Color, title: String, subtitle: String, right: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(top = 5.dp).clip(RoundedCornerShape(12.dp)).background(Color.White)
            .border(1.dp, DetailTokens.GlassBorder, RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(27.dp).clip(RoundedCornerShape(9.dp)).background(tint), contentAlignment = Alignment.Center) {
            Text(tag, color = ink, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        Column(Modifier.weight(1f).padding(start = 9.dp)) {
            Text(title, color = DetailTokens.TitleInk, fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = DetailTokens.TertiaryInk, fontSize = 8.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(right, color = DetailTokens.SecondaryInk, fontSize = 8.5.sp, modifier = Modifier.padding(start = 7.dp))
    }
}

@Composable
private fun FishDropdown(fishGroups: Map<String, List<Map.Entry<String, List<TrendPoint>>>>, selected: String?, onSelect: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val selectedName = fishGroups[selected]?.firstOrNull()?.value?.firstOrNull()?.label ?: "选择鱼只"
    Box {
        Text(
            "$selectedName ⌄", color = DetailTokens.SecondaryInk, fontSize = 9.sp,
            modifier = Modifier.clip(DetailTokens.PillShape).background(Color.White).border(1.dp, Color(0xFFE3EEF7), DetailTokens.PillShape)
                .clickable { open = true }.padding(horizontal = 8.dp, vertical = 5.dp),
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            fishGroups.keys.forEach { id ->
                val name = fishGroups[id]?.firstOrNull()?.value?.firstOrNull()?.label ?: id
                DropdownMenuItem(text = { Text(name) }, onClick = { onSelect(id); open = false })
            }
        }
    }
}

@Composable
private fun ReportRangeSheet(from: LocalDate, through: LocalDate, onDismiss: () -> Unit, onApply: (LocalDate, LocalDate) -> Unit, datePattern: String) {
    var start by remember(from) { mutableStateOf(from) }
    var end by remember(through) { mutableStateOf(through) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(start = 18.dp, end = 18.dp, bottom = 22.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Text("选择报表期", color = DetailTokens.TitleInk, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Text("开始和结束都包含在统计范围内。", color = DetailTokens.TertiaryInk, fontSize = 9.sp)
            RangeDateRow("开始日期", start, datePattern) { start = it ?: start }
            RangeDateRow("结束日期", end, datePattern) { end = it ?: end }
            Text("共 ${if (end.isBefore(start)) 0 else end.toEpochDay() - start.toEpochDay() + 1} 天", color = DetailTokens.SecondaryInk, fontSize = 9.5.sp)
            Button(
                onClick = { onApply(start, end) },
                enabled = !end.isBefore(start),
                shape = DetailTokens.PillShape,
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) { Text("应用范围", fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun RangeDateRow(label: String, value: LocalDate, pattern: String, onChange: (LocalDate?) -> Unit) {
    val context = LocalContext.current
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color.White).border(1.dp, DetailTokens.GlassBorder, RoundedCornerShape(12.dp))
            .clickable {
                DatePickerDialog(context, { _, year, month, day -> onChange(LocalDate.of(year, month + 1, day)) }, value.year, value.monthValue - 1, value.dayOfMonth).apply {
                    datePicker.maxDate = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                }.show()
            }.padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = DetailTokens.SecondaryInk, fontSize = 10.sp)
        Spacer(Modifier.weight(1f))
        Text(value.format(java.time.format.DateTimeFormatter.ofPattern(pattern)), color = DetailTokens.TitleInk, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        Text("›", color = DetailTokens.TertiaryInk, fontSize = 15.sp, modifier = Modifier.padding(start = 5.dp))
    }
}

@Composable
private fun ReportLoading() {
    Column(Modifier.fillMaxWidth().padding(top = 30.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        androidx.compose.material3.CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
        Text("正在汇总本地记录…", color = DetailTokens.SecondaryInk, fontSize = 10.sp, modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
private fun ReportError(message: String?) {
    Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("暂时无法计算统计", color = MaterialTheme.colorScheme.error, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Text(message ?: "请稍后重试", color = DetailTokens.SecondaryInk, fontSize = 10.sp, modifier = Modifier.padding(top = 5.dp))
    }
}

private fun capacityLiters(tanks: List<Tank>, selectedId: String?): Double? {
    val selected = tanks.filter { selectedId == null || it.id == selectedId }
    if (selected.isEmpty()) return null
    val values = selected.mapNotNull { tank -> tank.capacityValue?.let { formatMeasurement(it, tank.capacityUnit.orEmpty(), "L").first } }
    return if (values.size == selected.size) values.sum() else null
}

private fun waterParameterOrder(code: String): Int = listOf("temperature", "ph", "salinity", "specific_gravity", "alkalinity", "ammonia", "nitrite", "nitrate").indexOf(code).let { if (it < 0) 99 else it }

internal fun waterParameterName(code: String): String = when (code) {
    "temperature" -> "温度"; "ph" -> "pH"; "salinity" -> "盐度"; "specific_gravity" -> "比重"
    "alkalinity" -> "碱度"; "ammonia" -> "氨/铵"; "nitrite" -> "亚硝酸盐"; "nitrate" -> "硝酸盐"; else -> code
}

private fun waterPrecision(code: String?): Int = when (code) {
    "salinity", "specific_gravity" -> 3
    "ph" -> 2
    "ammonia", "nitrite", "nitrate" -> 2
    else -> 1
}

private fun convertWaterPoints(code: String?, points: List<TrendPoint>, prefs: AppPreferences): List<TrendPoint> {
    if (code != "temperature") return points
    return points.map { point -> formatMeasurement(point.value, point.unit, prefs.temperature).let { point.copy(value = it.first, unit = it.second) } }
}

private fun convertGrowthPoints(key: String?, points: List<TrendPoint>, prefs: AppPreferences): List<TrendPoint> {
    if (key == null) return points
    val target = if (key.endsWith("_length")) prefs.length else prefs.weight
    return points.map { point -> formatMeasurement(point.value, point.unit, target).let { point.copy(value = it.first, unit = it.second) } }
}

private fun convertVolumePoints(points: List<TrendPoint>, prefs: AppPreferences): List<TrendPoint> =
    points.map { point -> formatMeasurement(point.value, point.unit, prefs.volume).let { point.copy(value = it.first, unit = it.second) } }

private fun growthKeysEmpty(result: StatisticsSummary): Boolean = result.fishGrowth.isEmpty()

private fun formatReportDate(at: Long, pattern: String): String = SimpleDateFormat(pattern, Locale.CHINA).format(Date(at))

private fun formatReportNumber(value: Double, precision: Int): String =
    if (precision <= 0) value.roundToInt().toString() else String.format(Locale.CHINA, "%.${precision}f", value)

private fun oneDecimal(value: Double): String = String.format(Locale.CHINA, "%.1f", value)
