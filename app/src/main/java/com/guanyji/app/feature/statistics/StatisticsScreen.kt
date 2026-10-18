package com.guanyji.app.feature.statistics

import android.app.Application
import android.app.DatePickerDialog
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.guanyji.app.core.designsystem.InfoCard
import com.guanyji.app.core.designsystem.PageTitle
import com.guanyji.app.feature.profile.AppPreferences
import com.guanyji.app.feature.profile.ProfileViewModel
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date
import java.util.Locale
import kotlin.math.abs

@Composable
fun LegacyStatisticsScreen(onBack: () -> Unit, onAddRecord: (String, String?) -> Unit, vm: StatisticsViewModel = viewModel(factory = StatisticsViewModel.factory(LocalContext.current.applicationContext as Application))) {
    val profile: ProfileViewModel = viewModel(factory = ProfileViewModel.factory(LocalContext.current.applicationContext as Application))
    val preferences by profile.preferences.collectAsStateWithLifecycle()
    val tanks by vm.tanks.collectAsStateWithLifecycle()
    val filter by vm.filter.collectAsStateWithLifecycle()
    val report by vm.report.collectAsStateWithLifecycle()
    var tankMenu by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<TrendPoint?>(null) }
    Column(Modifier.fillMaxSize()) {
        androidx.compose.foundation.layout.Box(Modifier.testTag("statistics-screen")) {
            PageTitle("统计报表", "根据本地已保存记录汇总", onBack)
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box {
                OutlinedButton(onClick = { tankMenu = true }) { Text(tanks.firstOrNull { it.id == filter.tankId }?.name ?: "全部鱼缸") }
                DropdownMenu(expanded = tankMenu, onDismissRequest = { tankMenu = false }) {
                    DropdownMenuItem(text = { Text("全部鱼缸") }, onClick = { vm.setTank(null); tankMenu = false })
                    tanks.forEach { tank -> DropdownMenuItem(text = { Text(tank.name) }, onClick = { vm.setTank(tank.id); tankMenu = false }) }
                }
            }
            FilterChip(selected = filter.from == LocalDate.now() && filter.through == LocalDate.now(), onClick = { vm.setRange(1) }, label = { Text("今天") })
            FilterChip(selected = filter.from == LocalDate.now().minusDays(29), onClick = { vm.setRange(30) }, label = { Text("30天") })
            FilterChip(selected = filter.from == LocalDate.now().minusDays(89), onClick = { vm.setRange(90) }, label = { Text("90天") })
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DateRangeButton(filter.from, preferences.dateFormat, LocalDate.of(2000, 1, 1), filter.through) { from -> vm.setDates(from, filter.through) }
            DateRangeButton(filter.through, preferences.dateFormat, filter.from, LocalDate.now()) { through -> vm.setDates(filter.from, through) }
        }
        if (report == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            val result = report!!.getOrElse { failure ->
                Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
                    Text("暂时无法计算统计", color = MaterialTheme.colorScheme.error)
                    Text(failure.message ?: "请重试")
                }
                return@Column
            }
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Metric("在养鱼只", "${result.activeFish} 条", Modifier.weight(1f))
                    Metric("喂食频率", "${result.feedingsPerWeek.oneDecimal()} 次/周", Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Metric("换水频率", "${result.waterChangesPerWeek.oneDecimal()} 次/周", Modifier.weight(1f))
                    Metric("任务完成率", result.taskCompletionPercent?.let { "$it%" } ?: "暂无已记录任务", Modifier.weight(1f))
                }
                InfoCard("换水量", content = {
                    val displayVolume = result.waterChangeVolume?.let { if (preferences.volume == "gal") it / 3.785411784 to "gal" else it to "L" }
                    Text(if (displayVolume == null) "所选范围内没有可统计的换水量记录。" else "${displayVolume.first.oneDecimal()} ${displayVolume.second} · ${result.waterChangeEvents} 次换水")
                })
                if (result.feedingTrend.isEmpty()) EmptyChart("喂食频率趋势", "所选范围内没有喂食记录，未记录日期不会补成零。", "记录喂食") { onAddRecord("feeding", filter.tankId) }
                else TrendCard("每日喂食（已记录）", result.feedingTrend, selected, preferences.dateFormat) { selected = it }
                if (result.waterChangeTrend.isEmpty()) EmptyChart("换水频率趋势", "所选范围内没有换水记录。", "记录换水") { onAddRecord("change", filter.tankId) }
                else TrendCard("每日换水（已记录）", result.waterChangeTrend, selected, preferences.dateFormat) { selected = it }
                if (result.waterChangeVolumeTrend.isNotEmpty()) TrendCard("每日换水量", convertVolumePoints(result.waterChangeVolumeTrend, preferences), selected, preferences.dateFormat) { selected = it }
                if (result.waterParameters.isEmpty()) EmptyChart("水质参数趋势", "所选鱼缸和日期内没有水质测量值。", "记录水质") { onAddRecord("water", filter.tankId) }
                else result.waterParameters.forEach { (parameter, points) -> TrendCard(parameterName(parameter), convertWaterPoints(parameter, points, preferences), selected, preferences.dateFormat) { selected = it } }
                if (result.fishGrowth.isEmpty()) EmptyChart("单鱼成长趋势", "所选范围内没有体长或体重测量记录。", "记录成长") { onAddRecord("growth", filter.tankId) }
                else result.fishGrowth.forEach { (series, points) -> TrendCard(points.firstOrNull()?.label ?: series, convertGrowthPoints(series, points, preferences), selected, preferences.dateFormat) { selected = it } }
                Text("任务完成率只按有执行记录或明确跳过的计划计算；没有计划的日期不计为失败。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun DateRangeButton(date: LocalDate, pattern: String, min: LocalDate, max: LocalDate, onSelected: (LocalDate) -> Unit) {
    val context = LocalContext.current
    OutlinedButton(onClick = {
        DatePickerDialog(context, { _, year, month, day ->
            val date = LocalDate.of(year, month + 1, day)
            if (!date.isBefore(min) && !date.isAfter(max)) onSelected(date)
        }, date.year, date.monthValue - 1, date.dayOfMonth).apply {
            datePicker.minDate = min.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            datePicker.maxDate = max.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        }.show()
    }) { Text(date.format(java.time.format.DateTimeFormatter.ofPattern(pattern)), maxLines = 1) }
}

@Composable
private fun Metric(title: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier) { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    } }
}

@Composable
private fun EmptyChart(title: String, message: String, action: String, onClick: () -> Unit) {
    InfoCard(title, content = {
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = onClick) { Text(action) }
    })
}

@Composable
private fun TrendCard(title: String, points: List<TrendPoint>, selected: TrendPoint?, datePattern: String, onSelect: (TrendPoint) -> Unit) {
    val point = selected?.takeIf { it in points } ?: points.last()
    InfoCard(title, content = {
        Text("${point.at.formatDate(datePattern)} · ${point.value.oneDecimal()} ${point.unit}", style = MaterialTheme.typography.titleSmall)
        Box(Modifier.fillMaxWidth().height(150.dp)) {
            val chartColor = MaterialTheme.colorScheme.primary
            Canvas(Modifier.fillMaxSize().semantics { contentDescription = "$title 趋势图，${points.size} 个数据点" }.pointerInput(points) {
                detectTapGestures { tap ->
                    val nearest = points.minByOrNull { candidate -> abs(tap.x - points.indexOf(candidate).toFloat() * size.width / (points.size - 1).coerceAtLeast(1)) }
                    nearest?.let(onSelect)
                }
            }) {
                val values = points.map { it.value }
                val low = values.minOrNull() ?: 0.0
                val high = values.maxOrNull() ?: low + 1.0
                val span = (high - low).takeIf { it > 0.0 } ?: 1.0
                val dx = size.width / (points.size - 1).coerceAtLeast(1)
                val path = androidx.compose.ui.graphics.Path()
                points.forEachIndexed { index, sample ->
                    val x = index * dx
                    val y = size.height - ((sample.value - low) / span).toFloat() * (size.height - 16.dp.toPx()) - 8.dp.toPx()
                    if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(path, chartColor, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx()))
                points.forEachIndexed { index, sample ->
                    val x = index * dx
                    val y = size.height - ((sample.value - low) / span).toFloat() * (size.height - 16.dp.toPx()) - 8.dp.toPx()
                    drawCircle(chartColor, if (sample == point) 5.dp.toPx() else 3.dp.toPx(), androidx.compose.ui.geometry.Offset(x, y))
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(points.first().at.formatDate(datePattern), style = MaterialTheme.typography.labelSmall)
            Text(points.last().at.formatDate(datePattern), style = MaterialTheme.typography.labelSmall)
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
            points.forEach { sample ->
                TextButton(onClick = { onSelect(sample) }) { Text("${sample.at.formatDate(datePattern)} ${sample.value.oneDecimal()} ${sample.unit}") }
            }
        }
        Text("共 ${points.size} 条真实测量", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    })
}

private fun Long.formatDate(pattern: String = "M月d日") = SimpleDateFormat(pattern, Locale.CHINA).format(Date(this))
private fun Double.oneDecimal() = String.format(Locale.CHINA, "%.1f", this)
private fun parameterName(code: String) = when (code) {
    "temperature" -> "温度"; "ph" -> "pH"; "salinity" -> "盐度"; "specific_gravity" -> "比重"; "alkalinity" -> "碱度"
    "ammonia" -> "氨/铵"; "nitrite" -> "亚硝酸盐"; "nitrate" -> "硝酸盐"; else -> code
}

private fun convertWaterPoints(parameter: String, points: List<TrendPoint>, prefs: AppPreferences) = points.map { point ->
    if (parameter != "temperature") point else formatMeasurement(point.value, point.unit, prefs.temperature).let { point.copy(value = it.first, unit = it.second) }
}

private fun convertGrowthPoints(series: String, points: List<TrendPoint>, prefs: AppPreferences) = points.map { point ->
    when {
        series.endsWith("_length") -> formatMeasurement(point.value, point.unit, prefs.length).let { point.copy(value = it.first, unit = it.second, label = "${point.label} · 体长") }
        series.endsWith("_weight") -> formatMeasurement(point.value, point.unit, prefs.weight).let { point.copy(value = it.first, unit = it.second, label = "${point.label} · 体重") }
        else -> point
    }
}

private fun convertVolumePoints(points: List<TrendPoint>, prefs: AppPreferences) = points.map { point ->
    formatMeasurement(point.value, point.unit, prefs.volume).let { point.copy(value = it.first, unit = it.second) }
}
