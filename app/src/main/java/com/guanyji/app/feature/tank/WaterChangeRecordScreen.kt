package com.guanyji.app.feature.tank

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.guanyji.app.core.designsystem.DeepWaterCard
import com.guanyji.app.core.designsystem.DetailTokens
import com.guanyji.app.core.designsystem.LoadingState
import com.guanyji.app.core.designsystem.SubpageTopBar
import com.guanyji.app.core.designsystem.rememberLeaveFormHandler
import com.guanyji.app.data.local.TankType
import com.guanyji.app.data.local.WaterChange
import com.guanyji.app.feature.overview.prettyNumber
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun WaterChangeRecordScreen(
    tankId: String,
    editId: String?,
    onBack: () -> Unit,
    vm: TankViewModel = viewModel(factory = TankViewModel.factory(LocalContext.current.applicationContext as Application)),
) {
    val summaryState by remember(tankId) { vm.summary(tankId) }.collectAsStateWithLifecycle()
    val tank = (summaryState as? TankViewModel.SummaryState.Found)?.summary?.tank
    val context = LocalContext.current
    val now = remember { System.currentTimeMillis() }
    var volume by rememberSaveable(editId) { mutableStateOf("") }
    var volumeUnit by rememberSaveable(editId) { mutableStateOf("L") }
    var date by rememberSaveable(editId) { mutableStateOf(now) }
    var beforeTemperature by rememberSaveable(editId) { mutableStateOf("") }
    var afterTemperature by rememberSaveable(editId) { mutableStateOf("") }
    var beforeSalinity by rememberSaveable(editId) { mutableStateOf("") }
    var afterSalinity by rememberSaveable(editId) { mutableStateOf("") }
    var supplies by rememberSaveable(editId) { mutableStateOf("") }
    var notes by rememberSaveable(editId) { mutableStateOf("") }
    var nextPlannedAt by rememberSaveable(editId) { mutableStateOf<Long?>(null) }
    var formLoaded by remember(editId) { mutableStateOf(editId == null) }
    var initialSnapshot by remember(editId) { mutableStateOf<String?>(null) }
    var error by remember(editId) { mutableStateOf<String?>(null) }
    var isSaving by remember(editId) { mutableStateOf(false) }

    LaunchedEffect(editId) {
        if (editId != null) {
            val record = vm.waterChange(editId)
            if (record != null && record.tankId == tankId) {
                volume = record.volumeValue?.prettyNumber().orEmpty()
                volumeUnit = record.volumeUnit ?: "L"
                date = record.completedAt
                beforeTemperature = record.beforeTemperature?.prettyNumber().orEmpty()
                afterTemperature = record.afterTemperature?.prettyNumber().orEmpty()
                beforeSalinity = record.beforeSalinity?.prettyNumber().orEmpty()
                afterSalinity = record.afterSalinity?.prettyNumber().orEmpty()
                supplies = record.supplies.orEmpty()
                notes = record.notes.orEmpty()
                nextPlannedAt = record.nextPlannedAt
            } else {
                error = "无法加载这条换水记录"
            }
            formLoaded = true
        }
    }

    val snapshot = listOf(volume, volumeUnit, date, beforeTemperature, afterTemperature, beforeSalinity, afterSalinity, supplies, notes, nextPlannedAt).joinToString("|")
    LaunchedEffect(formLoaded, snapshot) {
        if (formLoaded && initialSnapshot == null) initialSnapshot = snapshot
    }
    val dirty = formLoaded && initialSnapshot != null && snapshot != initialSnapshot
    val leave = rememberLeaveFormHandler(dirty, onBack)
    val quantity = volume.toDoubleOrNull()
    val ratio = quantity?.let { calculateWaterChangeRatioPercent(it, volumeUnit, tank?.capacityValue, tank?.capacityUnit) }

    if (tank == null) {
        if (summaryState is TankViewModel.SummaryState.Loading) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingState("正在加载鱼缸…") }
        else Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("找不到这个鱼缸", style = MaterialTheme.typography.titleLarge)
            Text("鱼缸可能已被删除或链接已失效。", color = DetailTokens.SecondaryInk)
            TextButton(onClick = onBack) { Text("返回") }
        }
        return
    }

    fun save() {
        val value = volume.toDoubleOrNull()
        if (value == null || value <= 0) { error = "请输入有效的换水量"; return }
        val beforeTemp = parsePositive(beforeTemperature) ?: if (beforeTemperature.isNotBlank()) { error = "换水前水温格式不正确"; return } else null
        val afterTemp = parsePositive(afterTemperature) ?: if (afterTemperature.isNotBlank()) { error = "换水后水温格式不正确"; return } else null
        val beforeSalt = parsePositive(beforeSalinity) ?: if (beforeSalinity.isNotBlank()) { error = "换水前盐度格式不正确"; return } else null
        val afterSalt = parsePositive(afterSalinity) ?: if (afterSalinity.isNotBlank()) { error = "换水后盐度格式不正确"; return } else null
        isSaving = true
        error = null
        vm.addWaterChange(
            WaterChange(
                id = editId ?: java.util.UUID.randomUUID().toString(),
                tankId = tankId,
                completedAt = date,
                volumeValue = value,
                volumeUnit = volumeUnit,
                ratio = calculateWaterChangeRatioPercent(value, volumeUnit, tank.capacityValue, tank.capacityUnit),
                nextPlannedAt = nextPlannedAt,
                notes = notes.ifBlank { null },
                beforeTemperature = beforeTemp,
                afterTemperature = afterTemp,
                temperatureUnit = if (beforeTemp != null || afterTemp != null) "°C" else null,
                beforeSalinity = beforeSalt,
                afterSalinity = afterSalt,
                salinityUnit = if (beforeSalt != null || afterSalt != null) "ppt" else null,
                supplies = supplies.ifBlank { null },
            ),
            onSaved = { isSaving = false; onBack() },
            onError = { isSaving = false; error = it },
        )
    }

    Scaffold(
        containerColor = DetailTokens.PageBackground,
        topBar = {
            SubpageTopBar(
                if (editId == null) "记录换水" else "编辑换水",
                "${tank.name} · ${if (tank.type == TankType.saltwater) "海水缸" else "淡水缸"}",
                leave,
            )
        },
        bottomBar = { ChangeSaveBar(isSaving, ::save) },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 22.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                DeepWaterCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("换水量", color = Color(0xFF8FB8CE), fontSize = 9.5.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.weight(1f))
                            Text(
                                tank.capacityValue?.let { "鱼缸 ${it.prettyNumber()} ${tank.capacityUnit.orEmpty()}" } ?: "鱼缸容量未记录",
                                color = Color(0xFF8FB8CE), fontSize = 8.5.sp,
                            )
                        }
                        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 3.dp)) {
                            BasicTextField(
                                value = volume,
                                onValueChange = { raw -> volume = raw.filter { it.isDigit() || it == '.' }.take(7) },
                                singleLine = true,
                                textStyle = TextStyle(color = Color(0xFFF4FBFF), fontSize = 34.sp, fontWeight = FontWeight.Bold),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.weight(1f),
                                decorationBox = { inner ->
                                    Box {
                                        if (volume.isBlank()) Text("0", color = Color(0x4DFFFFFF), fontSize = 34.sp, fontWeight = FontWeight.Bold)
                                        inner()
                                    }
                                },
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                listOf("L", "gal").forEach { unit ->
                                    Text(unit, color = if (volumeUnit == unit) Color(0xFF062640) else Color(0xFF9BC3D7), fontSize = 9.sp, fontWeight = if (volumeUnit == unit) FontWeight.Bold else FontWeight.Normal,
                                        modifier = Modifier.clip(DetailTokens.PillShape).background(if (volumeUnit == unit) Color(0xFF5FE0CD) else Color.Transparent)
                                            .border(1.dp, if (volumeUnit == unit) Color(0xFF5FE0CD) else Color(0x33FFFFFF), DetailTokens.PillShape)
                                            .clickable { volumeUnit = unit }.padding(horizontal = 9.dp, vertical = 5.dp))
                                }
                            }
                        }
                        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            val ratioText = when {
                                quantity == null -> "输入换水量后计算比例"
                                ratio != null -> "约 ${formatWaterChangeRatio(ratio)}% 换水比例"
                                else -> "容量未记录，暂不计算比例"
                            }
                            Text(ratioText, color = Color(0xFF8FEDDF), fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.weight(1f))
                            Text("按鱼缸容量自动计算", color = Color(0xFF7FA6BC), fontSize = 8.sp)
                        }
                    }
                }
            }
            error?.let { item { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 4.dp)) } }
            item {
                ChangeSection("换水时间") {
                    ChangeDateRow("实际完成时间", date) { date = it ?: date }
                }
            }
            item {
                ChangeSection("换水前后", "可选；只记录实际测到的值") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ChangeNumberField("换水前水温", beforeTemperature, "°C", { beforeTemperature = it }, Modifier.weight(1f))
                        ChangeNumberField("换水后水温", afterTemperature, "°C", { afterTemperature = it }, Modifier.weight(1f))
                    }
                    if (tank.type == TankType.saltwater) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                            ChangeNumberField("换水前盐度", beforeSalinity, "ppt", { beforeSalinity = it }, Modifier.weight(1f))
                            ChangeNumberField("换水后盐度", afterSalinity, "ppt", { afterSalinity = it }, Modifier.weight(1f))
                        }
                    }
                }
            }
            item {
                ChangeSection("耗材与备注", "可选") {
                    OutlinedTextField(supplies, { supplies = it }, Modifier.fillMaxWidth(), label = { Text("用品，例如盐、滤材") }, singleLine = true, shape = RoundedCornerShape(12.dp))
                    OutlinedTextField(notes, { notes = it }, Modifier.fillMaxWidth().padding(top = 8.dp), label = { Text("备注") }, minLines = 2, shape = RoundedCornerShape(12.dp))
                    ChangeDateRow("下一次换水计划", nextPlannedAt, clearable = true, onChange = { nextPlannedAt = it })
                }
            }
            item {
                Text("保存后会写入本机记录；编辑已有记录不会新增一条。", color = DetailTokens.TertiaryInk, fontSize = 9.sp, modifier = Modifier.padding(horizontal = 4.dp))
            }
        }
    }
}

@Composable
private fun ChangeSection(title: String, subtitle: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White).border(1.dp, DetailTokens.GlassBorder, RoundedCornerShape(16.dp)).padding(14.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(title, color = DetailTokens.TitleInk, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
            subtitle?.let { Text(it, color = DetailTokens.TertiaryInk, fontSize = 8.5.sp, modifier = Modifier.padding(start = 6.dp)) }
        }
        Spacer(Modifier.height(9.dp))
        content()
    }
}

@Composable
private fun ChangeNumberField(label: String, value: String, unit: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = { raw -> onChange(raw.filter { it.isDigit() || it == '.' }.take(6)) },
        label = { Text(label, fontSize = 10.sp) },
        suffix = { Text(unit, fontSize = 9.sp) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier,
    )
}

@Composable
private fun ChangeDateRow(label: String, value: Long?, clearable: Boolean = false, onChange: (Long?) -> Unit) {
    val context = LocalContext.current
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { openDateTimePicker(context, value ?: System.currentTimeMillis()) { onChange(it) } }.padding(vertical = 4.dp)) {
        Text(label, color = DetailTokens.SecondaryInk, fontSize = 10.5.sp)
        Spacer(Modifier.weight(1f))
        Text(value?.let { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(it)) } ?: "未设置", color = if (value == null) DetailTokens.TertiaryInk else DetailTokens.TitleInk, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        Text("›", color = DetailTokens.TertiaryInk, fontSize = 15.sp, modifier = Modifier.padding(start = 5.dp))
        if (clearable && value != null) {
            TextButton(onClick = { onChange(null) }, contentPadding = PaddingValues(horizontal = 4.dp), modifier = Modifier.height(30.dp)) { Text("清除", fontSize = 9.sp) }
        }
    }
}

@Composable
private fun ChangeSaveBar(isSaving: Boolean, onSave: () -> Unit) {
    Surface(color = Color(0xFAFFFFFF), shadowElevation = 12.dp, modifier = Modifier.imePadding()) {
        Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 9.dp)) {
            Button(onClick = onSave, enabled = !isSaving, shape = DetailTokens.PillShape, modifier = Modifier.fillMaxWidth().height(50.dp)) {
                Text(if (isSaving) "保存中…" else "保存换水", fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

private fun parsePositive(value: String): Double? {
    if (value.isBlank()) return null
    return value.toDoubleOrNull()?.takeIf { it.isFinite() && it > 0 }
}
