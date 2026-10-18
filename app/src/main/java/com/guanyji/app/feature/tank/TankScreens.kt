@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.guanyji.app.feature.tank

import android.app.Application
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.guanyji.app.core.designsystem.*
import com.guanyji.app.data.local.*
import com.guanyji.app.data.repository.applySavedOrder
import com.guanyji.app.data.repository.mergeVisibleReorder
import com.guanyji.app.data.repository.movedItem
import com.guanyji.app.data.repository.waterChangeTaskMarker
import com.guanyji.app.feature.care.CareViewModel
import com.guanyji.app.feature.photo.ManagedCardPhoto
import com.guanyji.app.feature.photo.ManagedPhotoImage
import com.guanyji.app.feature.photo.PhotoPickerField
import com.guanyji.app.feature.photo.PhotoViewModel
import com.guanyji.app.feature.photo.PhotoViewModelFactory
import com.guanyji.app.feature.overview.FishIcons
import com.guanyji.app.feature.overview.prettyNumber
import com.guanyji.app.feature.overview.relativeTimeLabel
import com.guanyji.app.ui.theme.AquariumCardColors
import com.guanyji.app.ui.theme.AquariumCardTokens
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.request.ImageRequest
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.ScrollMoveMode
import sh.calvin.reorderable.rememberReorderableLazyGridState
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private enum class TankFilter(val label: String) { all("全部"), freshwater("淡水"), saltwater("海水"), archived("已归档") }
private enum class TankSection(val label: String) { overview("概览"), fish("鱼只"), water("水质"), care("养护"), stats("统计") }
internal data class WaterParameter(val code: String, val label: String, val unit: String, val units: List<String> = listOf(unit))

private val freshwaterParameters = listOf(
    WaterParameter("temperature", "温度", "°C", listOf("°C", "°F")),
    WaterParameter("ph", "pH", "pH"),
    WaterParameter("ammonia", "氨/铵", "mg/L", listOf("mg/L", "ppm")),
    WaterParameter("nitrite", "亚硝酸盐", "mg/L", listOf("mg/L", "ppm")),
    WaterParameter("nitrate", "硝酸盐", "mg/L", listOf("mg/L", "ppm")),
)
private val saltwaterParameters = listOf(
    WaterParameter("salinity", "盐度", "ppt", listOf("ppt", "PSU")),
    WaterParameter("specific_gravity", "比重", "SG"),
    WaterParameter("alkalinity", "碱度", "dKH", listOf("dKH", "meq/L")),
)
internal fun waterParameters(type: TankType) = freshwaterParameters + if (type == TankType.saltwater) saltwaterParameters else emptyList()
internal fun parameterLabel(code: String) = (freshwaterParameters + saltwaterParameters).firstOrNull { it.code == code }?.label ?: code

@Composable
fun TankListScreen(onCreate: () -> Unit, onOpen: (String) -> Unit, vm: TankViewModel = viewModel(factory = TankViewModel.factory(LocalContext.current.applicationContext as android.app.Application))) {
    val cardState by vm.cards.collectAsStateWithLifecycle()
    val tanks = (cardState as? TankCardsState.Success)?.tanks.orEmpty()
    var search by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(TankFilter.all) }
    var showFilterSheet by remember { mutableStateOf(false) }
    val gridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
    // 鱼缸页没有排序菜单：列表顺序本身就是用户拖出来的，未排过时回落到默认顺序（最近创建在前）。
    val savedOrder by vm.listOrder.collectAsStateWithLifecycle()
    var dragOrder by remember { mutableStateOf<List<String>?>(null) }
    LaunchedEffect(savedOrder, dragOrder) {
        val pending = dragOrder
        if (pending != null && pending == savedOrder) dragOrder = null
    }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val filtered = tanks.filter { card ->
        val query = search.trim()
        val typeLabel = if (card.type == TankType.freshwater) "淡水" else "海水"
        val matches = query.isEmpty() || card.name.contains(query, ignoreCase = true) || typeLabel.contains(query, ignoreCase = true)
        matches && when (filter) {
            TankFilter.all -> card.status == TankStatus.active
            TankFilter.freshwater -> card.status == TankStatus.active && card.type == TankType.freshwater
            TankFilter.saltwater -> card.status == TankStatus.active && card.type == TankType.saltwater
            TankFilter.archived -> card.status == TankStatus.archived
        }
    }
    val defaultOrder = remember(tanks) { tanks.map { it.id } }
    val fullOrder = remember(defaultOrder, dragOrder, savedOrder) {
        defaultOrder.applySavedOrder(dragOrder ?: savedOrder) { it }
    }
    val visible = filtered.associateBy { it.id }.let { byId -> fullOrder.mapNotNull(byId::get) }
    val visibleIds = visible.map { it.id }
    // 搜索是临时视图，搜索状态下不开放拖拽。
    val dragEnabled = search.isBlank()

    fun moveDragged(fromKey: Any?, toKey: Any?) {
        val fromId = fromKey as? String ?: return
        val toId = toKey as? String ?: return
        val from = visibleIds.indexOf(fromId)
        val to = visibleIds.indexOf(toId)
        if (from < 0 || to < 0) return
        dragOrder = mergeVisibleReorder(fullOrder, visibleIds.movedItem(from, to))
    }

    fun persistOrder(ids: List<String>) {
        vm.saveListOrder(ids) { message -> scope.launch { snackbar.showSnackbar(message) } }
    }

    /** 无障碍「上移 / 下移」：不依赖拖拽手势。 */
    fun moveByOne(id: String, delta: Int): Boolean {
        val from = visibleIds.indexOf(id)
        if (from < 0) return false
        val to = (from + delta).coerceIn(0, visibleIds.lastIndex)
        if (to == from) return false
        val merged = mergeVisibleReorder(fullOrder, visibleIds.movedItem(from, to))
        dragOrder = merged
        persistOrder(merged)
        return true
    }

    val reorderState = rememberReorderableLazyGridState(
        lazyGridState = gridState,
        scrollMoveMode = ScrollMoveMode.INSERT,
        onMove = { from, to -> moveDragged(from.key, to.key) },
    )
    // 与「我的鱼」共用同一套工具条形态：筛选维度全部放进面板，不再单独占一行 chip。
    if (showFilterSheet) {
        ListFilterSheet(
            onDismiss = { showFilterSheet = false },
            onReset = { filter = TankFilter.all },
            resultLabel = "查看 ${visible.size} 个鱼缸",
            onApply = { showFilterSheet = false },
        ) {
            FilterSection(
                label = "筛选",
                options = TankFilter.entries.map { FilterOption(it.name, it.label) },
                selectedKey = filter.name,
                onSelect = { key -> key?.let { name -> TankFilter.entries.firstOrNull { it.name == name } }?.let { filter = it } },
            )
        }
    }

    Scaffold(
        containerColor = Color(0xFFF2F8FC),
        snackbarHost = { SnackbarHost(snackbar) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).testTag("tank-list-screen")) {
            Box(Modifier.fillMaxWidth().height(ListToolbarTokens.HeaderHeight + ListToolbarTokens.ToolbarOverhang)) {
                AquariumListHeader(
                    title = "鱼缸",
                    subtitle = "用心守护每一个小小的海洋",
                    countLabel = if (cardState is TankCardsState.Success) "${tanks.count { it.status == TankStatus.active }} 个在用" else "读取中",
                    icon = FishIcons.Aquarium,
                    addContentDescription = "新增鱼缸",
                    onAdd = onCreate,
                    modifier = Modifier.align(Alignment.TopCenter),
                )
                ListSearchToolbar(
                    query = search,
                    onQueryChange = { search = it },
                    placeholder = "搜索鱼缸名称或类型",
                    filterActive = filter != TankFilter.all,
                    onFilterClick = { showFilterSheet = true },
                    modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 16.dp),
                )
            }
            ListResultRow(
                countText = if (cardState is TankCardsState.Success) "共 ${visible.size} 个鱼缸" else "正在读取本地记录",
                modifier = Modifier.padding(top = 10.dp),
            )
            if (filter != TankFilter.all) {
                AppliedFilterRow(
                    chips = listOf(AppliedFilterChip(filter.label) { filter = TankFilter.all }),
                    onClearAll = { filter = TankFilter.all },
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            if (cardState is TankCardsState.Loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingState() }
            } else if (cardState is TankCardsState.Failed) {
                Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) { ErrorState((cardState as TankCardsState.Failed).message, onRetry = vm::retryCards) }
            } else if (visible.isEmpty()) {
                Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    EmptyState(if (filter == TankFilter.archived) "暂无归档鱼缸" else if (tanks.isEmpty()) "开启你的第一座鱼缸" else "没有匹配的鱼缸", if (search.isBlank()) "创建鱼缸后，在这里管理环境与养护记录。" else "没有匹配的鱼缸，请尝试其他名称。")
                    if (filter != TankFilter.archived) Button(onClick = onCreate, modifier = Modifier.padding(top = 16.dp)) { Text("创建鱼缸") }
                }
            } else LazyVerticalGrid(columns = GridCells.Adaptive(minSize = 158.dp), state = gridState, modifier = Modifier.weight(1f), contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 4.dp, bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(visible, key = { it.id }) { card ->
                    ReorderableItem(reorderState, key = card.id) { isDragging ->
                        val photo by remember(card.photoId) { vm.photo(card.photoId) }.collectAsStateWithLifecycle()
                        TankCard(
                            card,
                            photo,
                            onClick = { onOpen(card.id) },
                            dragging = isDragging,
                            dragHandleModifier = if (dragEnabled) {
                                Modifier.longPressDraggableHandle(
                                    onDragStarted = { haptics.performHapticFeedback(HapticFeedbackType.LongPress) },
                                    onDragStopped = {
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        dragOrder?.let(::persistOrder)
                                    },
                                )
                            } else {
                                Modifier
                            },
                            modifier = Modifier.semantics {
                                if (dragEnabled && visible.size > 1) {
                                    customActions = listOf(
                                        CustomAccessibilityAction("上移") { moveByOne(card.id, -1) },
                                        CustomAccessibilityAction("下移") { moveByOne(card.id, 1) },
                                    )
                                }
                            },
                        )
                    }
                }
                if (filter != TankFilter.archived) item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) { CareTip() }
            }
        }
    }
}

@Composable
private fun TankCard(
    card: TankCardUiModel,
    photo: Photo?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    dragging: Boolean = false,
    dragHandleModifier: Modifier = Modifier,
) {
    AquariumCardShell(
        accent = card.type.tankCardAccent(),
        onClick = onClick,
        onClickLabel = "查看${card.name}详情",
        modifier = modifier.testTag("tank-card-${card.id}"),
        lifted = dragging,
        dragHandleModifier = dragHandleModifier,
        imageContent = { TankCardImage(card.type, photo) },
        overlayContent = {
            AquariumTypeBadge(
                label = card.typeLabel,
                accent = card.type.tankCardBadgeAccent(),
                modifier = Modifier.align(Alignment.TopStart).padding(
                    start = AquariumCardTokens.CardTypeBadgeInset,
                    top = AquariumCardTokens.CardTypeBadgeTopInset,
                ),
            )
            TankBadge(
                label = card.statusLabel,
                color = card.status.tankCardStatusBadgeAccent(),
                modifier = Modifier.align(Alignment.TopEnd).padding(
                    end = AquariumCardTokens.CardTypeBadgeInset,
                    top = AquariumCardTokens.CardTypeBadgeTopInset,
                ),
            )
        },
        infoContent = { TankCardInfo(card) },
    )
}

@Composable
private fun TankCardImage(type: TankType, photo: Photo?) {
    ManagedCardPhoto(
        photo = photo,
        contentDescription = "鱼缸照片",
        modifier = Modifier.fillMaxSize(),
        placeholder = { label, _ -> TankCardPhotoPlaceholder(type, label) },
    )
}

@Composable
private fun TankCardInfo(card: TankCardUiModel) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(
            start = AquariumCardTokens.CardBodyInset,
            end = AquariumCardTokens.CardBodyInset,
            top = AquariumCardTokens.CardBodyInset,
            bottom = AquariumCardTokens.CardBodyBottomInset,
        ),
        verticalArrangement = Arrangement.spacedBy(AquariumCardTokens.CardTextRowSpacing),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = card.name,
                modifier = Modifier.weight(1f),
                fontSize = AquariumCardTokens.CardTitleFontSize,
                lineHeight = AquariumCardTokens.CardTitleLineHeight,
                fontWeight = FontWeight.SemiBold,
                color = AquariumCardColors.CardInk,
                maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
            Icon(
                imageVector = FishIcons.ArrowRight,
                contentDescription = null,
                tint = AquariumCardColors.CardSecondaryInk,
                modifier = Modifier.size(AquariumCardTokens.CardArrowSize),
            )
        }
        Text(
            text = "${card.capacityLabel} · 在养 ${card.activeFishCount} 条",
            fontSize = AquariumCardTokens.CardSecondaryFontSize,
            lineHeight = AquariumCardTokens.CardSecondaryLineHeight,
            color = AquariumCardColors.CardSecondaryInk,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        )
        card.metrics.chunked(2).forEach { rowMetrics ->
            Row(horizontalArrangement = Arrangement.spacedBy(AquariumCardTokens.FishCardMetricItemGap)) {
                rowMetrics.forEach { metric ->
                    TankWaterMetric(metric, Modifier.weight(1f))
                }
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AquariumCardTokens.FishCardIconGap),
        ) {
            Icon(
                imageVector = FishIcons.Fish,
                contentDescription = null,
                tint = AquariumCardColors.CardMetricInk.copy(alpha = 0.85f),
                modifier = Modifier.size(AquariumCardTokens.CardFishIconSize),
            )
            Text(
                text = "今日喂食",
                fontSize = AquariumCardTokens.CardMetricFontSize,
                lineHeight = AquariumCardTokens.CardMetricLineHeight,
                color = AquariumCardColors.CardSecondaryInk,
                maxLines = 1,
            )
            Text(
                text = card.feedingLabel,
                fontSize = AquariumCardTokens.CardMetricValueFontSize,
                lineHeight = AquariumCardTokens.CardMetricValueLineHeight,
                color = AquariumCardColors.CardMetricInk,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
        Text(
            text = "检测 ${card.latestWaterTestAt?.let(::shortDate) ?: "--"} · 换水 ${card.lastWaterChangeAt?.let(::shortDate) ?: "--"}",
            fontSize = AquariumCardTokens.CardMetricFontSize,
            lineHeight = AquariumCardTokens.CardMetricLineHeight,
            color = AquariumCardColors.CardSecondaryInk,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun TankWaterMetric(metric: TankWaterMetricUiModel, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AquariumCardTokens.FishCardMetricIconGap),
    ) {
        Icon(
            imageVector = tankMetricIcon(metric.code),
            contentDescription = null,
            tint = AquariumCardColors.CardMetricInk.copy(alpha = 0.85f),
            modifier = Modifier.size(AquariumCardTokens.FishCardMetricIconSize),
        )
        Column {
            Text(
                text = metric.label,
                fontSize = AquariumCardTokens.CardMetricFontSize,
                lineHeight = AquariumCardTokens.CardMetricLineHeight,
                color = AquariumCardColors.CardSecondaryInk,
                maxLines = 1,
            )
            Text(
                text = metric.value,
                fontSize = AquariumCardTokens.CardMetricValueFontSize,
                lineHeight = AquariumCardTokens.CardMetricValueLineHeight,
                color = AquariumCardColors.CardMetricInk,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
        }
    }
}

private fun tankMetricIcon(code: String) = when (code) {
    "temperature" -> FishIcons.Droplet
    "ph" -> FishIcons.Eye
    else -> FishIcons.WaterLevel
}

@Composable
private fun TankCardPhotoPlaceholder(type: TankType, label: String) {
    val accent = type.tankCardAccent()
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        accent.copy(alpha = 0.42f),
                        AquariumCardColors.CardTop,
                        AquariumCardColors.CardBottom,
                    ),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(
                imageVector = FishIcons.Aquarium,
                contentDescription = null,
                tint = AquariumCardColors.PlaceholderInk,
                modifier = Modifier.size(28.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = AquariumCardColors.PlaceholderText,
            )
        }
    }
}

@Composable
private fun TankBadge(label: String, color: Color, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = AquariumCardTokens.TypeBadgeShape,
        color = color,
        contentColor = AquariumCardTokens.TypeBadgeTextColor,
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(
                horizontal = AquariumCardTokens.TypeBadgeHorizontalInset,
                vertical = AquariumCardTokens.TypeBadgeVerticalInset,
            ),
            fontSize = AquariumCardTokens.CardCaptionFontSize,
            lineHeight = AquariumCardTokens.CardCaptionLineHeight,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
    }
}

@Composable
private fun CareTip() {
    Surface(Modifier.fillMaxWidth().padding(top = 4.dp), shape = MaterialTheme.shapes.large, color = Color.White, shadowElevation = 2.dp) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Outlined.Notifications, contentDescription = null, tint = Color(0xFF1687E8))
            Column {
                Text("鱼缸小贴士", style = MaterialTheme.typography.titleSmall, color = Color(0xFF17466A))
                Text("定期记录水质与换水，帮助追踪环境变化。", style = MaterialTheme.typography.bodySmall, color = Color(0xFF647F95))
            }
        }
    }
}

@Composable
private fun TankMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(modifier = modifier.heightIn(min = 72.dp), shape = MaterialTheme.shapes.large, color = Color.White, shadowElevation = 3.dp) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 5.dp, vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(value, style = MaterialTheme.typography.labelLarge, color = Color(0xFF17466A), maxLines = 1)
            Text(label, style = MaterialTheme.typography.labelSmall, color = Color(0xFF67839A), maxLines = 1)
        }
    }
}

@Composable
fun TankFormScreen(tankId: String?, onBack: () -> Unit, onSaved: (String) -> Unit, vm: TankViewModel = viewModel(factory = TankViewModel.factory(LocalContext.current.applicationContext as android.app.Application))) {
    var existing by remember(tankId) { mutableStateOf<Tank?>(null) }
    var tankLoaded by remember(tankId) { mutableStateOf(tankId == null) }
    var formLoaded by remember(tankId) { mutableStateOf(tankId == null) }
    var activeFishCount by remember(tankId) { mutableStateOf(0) }
    val photoVm: PhotoViewModel = viewModel(key = "tank-photo-$tankId", factory = PhotoViewModelFactory(vm.photoRepository))
    val photoState by photoVm.state.collectAsStateWithLifecycle()
    val tankFish by remember(tankId) { vm.fish(tankId.orEmpty()) }.collectAsStateWithLifecycle(initialValue = emptyList())
    LaunchedEffect(tankFish) { activeFishCount = tankFish.count { it.status == FishStatus.active || it.status == FishStatus.quarantine } }
    var name by remember(tankId) { mutableStateOf("") }
    var type by remember(tankId) { mutableStateOf(TankType.freshwater) }
    var length by remember(tankId) { mutableStateOf("") }
    var width by remember(tankId) { mutableStateOf("") }
    var height by remember(tankId) { mutableStateOf("") }
    var capacity by remember(tankId) { mutableStateOf("") }
    var unit by remember(tankId) { mutableStateOf("L") }
    var startedAt by remember(tankId) { mutableStateOf<Long?>(null) }
    var equipment by remember(tankId) { mutableStateOf("") }
    var salinityManagementEnabled by remember(tankId) { mutableStateOf(false) }
    var notes by remember(tankId) { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var typeConfirmation by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    LaunchedEffect(tankId) {
        if (tankId == null) { tankLoaded = true; formLoaded = true; return@LaunchedEffect }
        val tank = vm.tankOnce(tankId)
        existing = tank
        tankLoaded = true
        if (tank != null) {
            name = tank.name; type = tank.type
            length = tank.lengthCm?.pretty().orEmpty(); width = tank.widthCm?.pretty().orEmpty(); height = tank.heightCm?.pretty().orEmpty()
            capacity = tank.capacityValue?.takeUnless { tank.capacityIsEstimated }?.pretty().orEmpty()
            unit = tank.capacityUnit ?: "L"; startedAt = tank.startedAt; equipment = tank.equipment.orEmpty(); notes = tank.notes.orEmpty(); salinityManagementEnabled = tank.salinityManagementEnabled
            formLoaded = true
        }
    }
    LaunchedEffect(existing?.photoId, tankId) { if (tankId != null) photoVm.load(existing?.photoId) }
    val estimate = listOf(length, width, height).mapNotNull(String::toDoubleOrNull).takeIf { it.size == 3 }?.let { estimateTankCapacityLiters(it[0], it[1], it[2]) }
    val estimateInUnit = estimate?.let { if (unit == "gal") it / 3.785411784 else it }
    val effectiveCapacity = capacity.toDoubleOrNull() ?: estimateInUnit
    val hasRealOverride = capacity.toDoubleOrNull() != null
    val editableTank = existing
    val hasUnsavedChanges = photoState.isBusy || if (tankId == null) {
        name.isNotBlank() || length.isNotBlank() || width.isNotBlank() || height.isNotBlank() || capacity.isNotBlank() || equipment.isNotBlank() || notes.isNotBlank() || salinityManagementEnabled || photoState.photo != null
    } else formLoaded && editableTank != null && (
        name != editableTank.name || type != editableTank.type || length.toDoubleOrNull() != editableTank.lengthCm || width.toDoubleOrNull() != editableTank.widthCm || height.toDoubleOrNull() != editableTank.heightCm ||
            capacity.toDoubleOrNull() != editableTank.capacityValue.takeUnless { editableTank.capacityIsEstimated } || unit != (editableTank.capacityUnit ?: "L") || startedAt != editableTank.startedAt ||
            equipment != editableTank.equipment.orEmpty() || salinityManagementEnabled != editableTank.salinityManagementEnabled || notes != editableTank.notes.orEmpty() || photoState.photo?.id != editableTank.photoId
        )
    val leaveForm = rememberLeaveFormHandler(hasUnsavedChanges, onBack)

    if (typeConfirmation) ConfirmDialog(title = "切换鱼缸类型？", message = "请检查现有 ${activeFishCount} 条鱼的类型适配性。历史水质检测会保留。", confirmText = "确认切换", onConfirm = { type = if (type == TankType.freshwater) TankType.saltwater else TankType.freshwater; typeConfirmation = false }, onDismiss = { typeConfirmation = false })
    Scaffold(topBar = { TopAppBarSimple(if (tankId == null) "创建鱼缸" else "编辑鱼缸", leaveForm) }, bottomBar = { Surface(shadowElevation = 4.dp) { Button(enabled = formLoaded && (tankId == null || existing != null) && !isSaving && !photoState.isBusy && photoState.cameraCaptureUri == null, onClick = {
        val parsedCapacity = effectiveCapacity
        if (name.isBlank()) { error = "请输入鱼缸名称"; return@Button }
        val measurements = listOf(length, width, height, capacity)
        if (measurements.any { it.isNotBlank() && it.toDoubleOrNull() == null }) { error = "尺寸和容量请输入有效数字"; return@Button }
        if (measurements.mapNotNull(String::toDoubleOrNull).any { it <= 0 || !it.isFinite() }) { error = "尺寸和容量必须大于 0"; return@Button }
        val now = System.currentTimeMillis()
        val tank = Tank(id = existing?.id ?: java.util.UUID.randomUUID().toString(), name = name, type = type, photoId = photoState.photo?.id ?: existing?.photoId, lengthCm = length.toDoubleOrNull(), widthCm = width.toDoubleOrNull(), heightCm = height.toDoubleOrNull(), capacityValue = parsedCapacity, capacityUnit = parsedCapacity?.let { unit }, capacityIsEstimated = parsedCapacity != null && !hasRealOverride && estimate != null, startedAt = startedAt, equipment = equipment.ifBlank { null }, status = existing?.status ?: TankStatus.active, salinityManagementEnabled = type == TankType.saltwater && salinityManagementEnabled, notes = notes.ifBlank { null }, createdAt = existing?.createdAt ?: now, updatedAt = now)
        isSaving = true
        vm.save(tank, { onSaved(tank.id) }, { isSaving = false; error = it })
    }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) { Text(if (isSaving) "保存中…" else "保存鱼缸") } } }) { padding ->
        if (tankId != null && !tankLoaded) Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { LoadingState("正在加载鱼缸…") }
        else if (tankId != null && existing == null) Column(Modifier.fillMaxSize().padding(padding).padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("找不到这个鱼缸", modifier = Modifier.testTag("tank-edit-not-found"), style = MaterialTheme.typography.titleLarge)
            TextButton(onClick = onBack) { Text("返回") }
        }
        else LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { PhotoPickerField(photoVm, Modifier.fillMaxWidth()) }
            item { OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("名称 *") }, singleLine = true) }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("鱼缸类型", style = MaterialTheme.typography.titleSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(type == TankType.freshwater, { if (type != TankType.freshwater) { if (activeFishCount > 0) typeConfirmation = true else type = TankType.freshwater } }, label = { Text("淡水") })
                        FilterChip(type == TankType.saltwater, { if (type != TankType.saltwater) { if (activeFishCount > 0) typeConfirmation = true else type = TankType.saltwater } }, label = { Text("海水") })
                    }
                    Text("水质参数模板：${if (type == TankType.freshwater) "温度、pH、氨/铵、亚硝酸盐、硝酸盐" else "温度、pH、氨/铵、亚硝酸盐、硝酸盐、盐度、比重、碱度"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (type == TankType.saltwater) Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(salinityManagementEnabled, { salinityManagementEnabled = it }, modifier = Modifier.padding(end = 8.dp))
                        Column { Text("启用盐度管理"); Text("开启后可安排盐度检测提醒；无测量值时只提示检测。", style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("尺寸（厘米）", style = MaterialTheme.typography.titleSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(length, { length = it }, Modifier.weight(1f), label = { Text("长") }, singleLine = true)
                        OutlinedTextField(width, { width = it }, Modifier.weight(1f), label = { Text("宽") }, singleLine = true)
                        OutlinedTextField(height, { height = it }, Modifier.weight(1f), label = { Text("高") }, singleLine = true)
                    }
                    estimateInUnit?.let { Text("尺寸估算容量：${it.pretty()} $unit（估算）", color = MaterialTheme.colorScheme.primary) }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(capacity, { capacity = it }, Modifier.weight(1f), label = { Text("实际水体容量") }, singleLine = true)
                        var unitMenu by remember { mutableStateOf(false) }
                        Box { OutlinedButton(onClick = { unitMenu = true }) { Text(unit) }; DropdownMenu(unitMenu, { unitMenu = false }) { listOf("L", "gal").forEach { DropdownMenuItem(text = { Text(it) }, onClick = { unit = it; unitMenu = false }) } } }
                    }
                    if (capacity.isBlank() && estimate != null) Text("未填写实际容量时保存估算值；输入实际容量可覆盖估算。", style = MaterialTheme.typography.bodySmall)
                }
            }
            item { DateField("启用日期", startedAt) { startedAt = it } }
            item { OutlinedTextField(equipment, { equipment = it }, Modifier.fillMaxWidth(), label = { Text("设备信息（过滤、加热、照明、造流）") }, minLines = 2) }
            item { OutlinedTextField(notes, { notes = it }, Modifier.fillMaxWidth(), label = { Text("备注") }, minLines = 3) }
            item { error?.let { Text(it, color = MaterialTheme.colorScheme.error) } }
            item { Spacer(Modifier.height(12.dp)) }
        }
    }
}

@Composable
fun TankDetailScreen(
    tankId: String,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onOpenFish: (String) -> Unit,
    onQuickAction: (String, String) -> Unit,
    onOpenRecords: (String) -> Unit = {},
    vm: TankViewModel = viewModel(factory = TankViewModel.factory(LocalContext.current.applicationContext as android.app.Application)),
) {
    val summaryState by remember(tankId) { vm.summary(tankId) }.collectAsStateWithLifecycle()
    val foundSummary = (summaryState as? TankViewModel.SummaryState.Found)?.summary
    val fishState by remember(tankId) { vm.fishState(tankId) }.collectAsStateWithLifecycle()
    val fish = (fishState as? TankFishState.Success)?.fish.orEmpty()
    val tests by remember(tankId) { vm.tests(tankId) }.collectAsStateWithLifecycle(initialValue = emptyList())
    val changes by remember(tankId) { vm.changes(tankId) }.collectAsStateWithLifecycle(initialValue = emptyList())
    val tasks by remember(tankId) { vm.tasks(tankId) }.collectAsStateWithLifecycle(initialValue = emptyList())
    val feedings by remember(tankId) { vm.feedings(tankId) }.collectAsStateWithLifecycle(initialValue = emptyList())
    val photoId = foundSummary?.tank?.photoId
    val photo by remember(photoId) { vm.photo(photoId) }.collectAsStateWithLifecycle()
    val tank = foundSummary?.tank
    val activeFishCount = fish.count { it.status == FishStatus.active }
    val quarantineFishCount = fish.count { it.status == FishStatus.quarantine }
    val latestTest = tests.maxByOrNull { it.test.measuredAt }
    val latestValues = latestTest?.values.orEmpty()
    val lastChange = changes.maxByOrNull { it.completedAt }
    val nextTask = tasks.filter { it.status == CareTaskStatus.pending || it.status == CareTaskStatus.overdue }.minByOrNull { it.plannedAt }
    val todayFeedings = feedings.count { isToday(it.event.fedAt) }
    val scrollState = rememberScrollState()
    var showMore by remember { mutableStateOf(false) }
    var showArchive by remember { mutableStateOf(false) }
    var archiving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val manageable = tank?.status == TankStatus.active
    val heroHeight = 272.dp
    val overlap = 30.dp

    if (showArchive && fishState is TankFishState.Success) {
        ConfirmDialog(
            title = "归档鱼缸？",
            message = "${tank?.name.orEmpty()}\n在养：$activeFishCount 条 · 已隔离：$quarantineFishCount 条\n确认后将同步归档以上鱼只、停止该缸未来养护任务和提醒。已转出、已故及已归档鱼只状态不变；所有档案与历史记录保留。",
            confirmText = "确认归档",
            confirmEnabled = !archiving,
            onConfirm = {
                if (!archiving) {
                    archiving = true
                    vm.archive(tankId, true, { archiving = false; showArchive = false; onBack() }, { archiving = false; error = it; showArchive = false })
                }
            },
            onDismiss = { showArchive = false },
        )
    }

    Scaffold(
        containerColor = DetailTokens.PageBackground,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (tank != null && manageable) {
                DetailRecordDock(
                    primaryText = "记录水质",
                    primaryIcon = FishIcons.Droplet,
                    onPrimary = { onQuickAction("water", tankId) },
                    secondary = listOf(
                        DockAction("喂食", FishIcons.Fish) { onQuickAction("feeding", tankId) },
                        DockAction("换水", FishIcons.WaterLevel) { onQuickAction("change", tankId) },
                    ),
                )
            }
        },
    ) { padding ->
        when {
            summaryState == TankViewModel.SummaryState.Loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { LoadingState("正在加载鱼缸…") }
            summaryState is TankViewModel.SummaryState.Missing || summaryState is TankViewModel.SummaryState.Failed -> Column(
                Modifier.fillMaxSize().padding(padding).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(if (summaryState is TankViewModel.SummaryState.Missing) "找不到这个鱼缸" else "鱼缸加载失败", modifier = Modifier.testTag("tank-not-found"), style = MaterialTheme.typography.titleLarge)
                Text(if (summaryState is TankViewModel.SummaryState.Missing) "鱼缸可能已被删除或链接已失效。" else (summaryState as TankViewModel.SummaryState.Failed).message)
                TextButton(onClick = onBack) { Text("返回") }
            }
            tank != null -> Box(Modifier.fillMaxSize().padding(padding)) {
                Column(Modifier.fillMaxSize().verticalScroll(scrollState)) {
                    Box(Modifier.fillMaxWidth()) {
                        TankDetailHero(
                            tank = tank,
                            photo = photo,
                            activeFishCount = activeFishCount,
                            onBack = onBack,
                            onMore = { showMore = true },
                            modifier = Modifier.fillMaxWidth().height(heroHeight),
                        )
                        Column(Modifier.fillMaxWidth().padding(top = heroHeight - overlap, bottom = 24.dp)) {
                            TankWaterCard(
                                tank = tank,
                                values = latestValues,
                                measuredAt = latestTest?.test?.measuredAt,
                                onTrend = { onOpenRecords("water") },
                                onRecord = { onQuickAction("water", tankId) },
                                modifier = Modifier.padding(horizontal = 16.dp),
                            )
                            DetailSectionHeader(
                                title = "缸内鱼群",
                                actionLabel = "管理",
                                onAction = { onOpenRecords("fish") },
                                modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp),
                            )
                            TankFishStrip(
                                fish = fish,
                                loading = fishState is TankFishState.Loading,
                                canAdd = manageable,
                                onOpenFish = onOpenFish,
                                onAdd = { onQuickAction("fish", tankId) },
                                vm = vm,
                            )
                            when (val state = fishState) {
                                TankFishState.Loading -> Unit
                                is TankFishState.Error -> ErrorState("鱼只数量加载失败。${state.message}", onRetry = vm::retryFish)
                                is TankFishState.Success -> Unit
                            }
                            DetailSectionHeader(
                                title = "养护节奏",
                                actionLabel = "本周",
                                mutedAction = true,
                                modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp),
                            )
                            TankCareCard(
                                todayFeedings = todayFeedings,
                                lastFeeding = feedings.maxByOrNull { it.event.fedAt },
                                lastChange = lastChange,
                                nextTask = nextTask,
                                onRecordChange = { onQuickAction("change", tankId) },
                                onAddTask = { onQuickAction("task", tankId) },
                                onOpenCare = { onOpenRecords("care") },
                                modifier = Modifier.padding(horizontal = 16.dp),
                            )
                            DetailSectionHeader(
                                title = "最近动态",
                                actionLabel = "全部记录",
                                onAction = { onOpenRecords("activity") },
                                modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp),
                            )
                            TankTimelineCard(
                                items = buildTankTimeline(tests, changes, feedings).take(4),
                                modifier = Modifier.padding(horizontal = 16.dp),
                            )
                            DetailSectionHeader(
                                title = "鱼缸档案",
                                actionLabel = "仅展示已记录",
                                mutedAction = true,
                                modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp),
                            )
                            TankProfileCard(
                                tank = tank,
                                onEdit = onEdit,
                                modifier = Modifier.padding(horizontal = 16.dp),
                            )
                        }
                    }
                }
                AnimatedVisibility(
                    visible = scrollState.value > 190,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.align(Alignment.TopCenter),
                ) {
                    CompactDetailTopBar(
                        title = tank.name,
                        statusText = if (manageable) "在养" else "已归档",
                        statusColor = if (manageable) DetailTokens.Aqua else DetailTokens.TertiaryInk,
                        onBack = onBack,
                        onMore = { showMore = true },
                    )
                }
            }
        }
    }

    if (showMore && tank != null) {
        ModalBottomSheet(
            onDismissRequest = { showMore = false },
            containerColor = Color.White,
            contentColor = DetailTokens.TitleInk,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
                Text("管理 ${tank.name}", color = DetailTokens.TitleInk, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text("低频操作集中在这里", color = DetailTokens.SecondaryInk, fontSize = 11.5.sp, modifier = Modifier.padding(top = 3.dp, bottom = 8.dp))
                TankMoreAction("编辑鱼缸", "名称、照片、类型、容量与设备") { showMore = false; onEdit() }
                if (manageable) {
                    TankMoreAction("归档鱼缸", "停止未来任务与提醒，历史记录保留", danger = true) { showMore = false; showArchive = true }
                }
                TextButton(onClick = { showMore = false }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) { Text("取消") }
            }
        }
    }

    LaunchedEffect(error) {
        val message = error ?: return@LaunchedEffect
        snackbar.showSnackbar(message)
        error = null
    }
}

@Composable
private fun TankDetailHero(
    tank: Tank,
    photo: Photo?,
    activeFishCount: Int,
    onBack: () -> Unit,
    onMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val statusBarInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val startedDays = tank.startedAt?.let { daysSince(it) }
    Box(modifier) {
        DetailHeroPhoto(photo, Modifier.fillMaxSize())
        HeroScrim(Modifier.fillMaxSize())
        HeroWaterEffects(Modifier.fillMaxSize())
        Row(
            Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = statusBarInset + 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HeroOrbButton(Icons.AutoMirrored.Outlined.ArrowBack, "返回", onBack)
            Spacer(Modifier.weight(1f))
            HeroOrbButton(Icons.Outlined.MoreVert, "更多操作", onMore)
        }
        Column(
            Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(start = 18.dp, end = 18.dp, bottom = 44.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                HeroGhostPill(if (tank.type == TankType.freshwater) "淡水缸" else "海水缸")
                HeroStatusPill(if (tank.status == TankStatus.active) "在养" else "已归档", if (tank.status == TankStatus.active) DetailTokens.AquaBright else Color(0xFF93A3B1), if (tank.status == TankStatus.active) Color(0x7A12806C) else Color(0x7A3F4A57), if (tank.status == TankStatus.active) Color(0x7A6DEBD2) else Color(0x8A93A3B1))
            }
            Text(tank.name, color = Color.White, fontSize = 31.sp, lineHeight = 37.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(tank.capacityValue?.let { "${it.prettyNumber()} ${tank.capacityUnit.orEmpty()}" } ?: "容量未记录", color = Color(0xFFF2FBFF), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Box(Modifier.size(3.dp).clip(CircleShape).background(Color(0x99CBE4F2)))
                Text("在养 $activeFishCount 条", color = DetailTokens.HeroSecondary, fontSize = 11.5.sp)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                startedDays?.let {
                    Text("运行 $it 天", color = DetailTokens.HeroSecondary, fontSize = 11.5.sp)
                    Box(Modifier.size(3.dp).clip(CircleShape).background(Color(0x99CBE4F2)))
                }
                Text(if (tank.type == TankType.freshwater) "淡水" else "海水", color = DetailTokens.HeroSecondary, fontSize = 11.5.sp)
            }
        }
    }
}

@Composable
private fun TankWaterCard(
    tank: Tank,
    values: List<WaterTestValue>,
    measuredAt: Long?,
    onTrend: () -> Unit,
    onRecord: () -> Unit,
    modifier: Modifier = Modifier,
) {
    fun valueOf(code: String): WaterTestValue? = values.firstOrNull { it.parameterCode == code && it.value != null }
    val temperature = valueOf("temperature")
    val salinity = valueOf("salinity")
    val ph = valueOf("ph")
    DeepWaterCard(modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 13.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("水况", color = DetailTokens.HeroInk, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text(measuredAt?.let { "检测于 ${relativeTimeLabel(it)}" } ?: "还没有检测", color = Color(0xFF9BC3D7), fontSize = 10.5.sp)
                Spacer(Modifier.weight(1f))
                Row(
                    Modifier.clip(DetailTokens.PillShape).clickable(role = Role.Button, onClick = onTrend).padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(1.dp),
                ) {
                    Text("趋势", color = DetailTokens.AquaBright, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    Icon(FishIcons.ArrowRight, contentDescription = null, tint = DetailTokens.AquaBright, modifier = Modifier.size(11.dp))
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                WaterGauge(
                    valueText = temperature?.value?.prettyNumber() ?: "—",
                    unit = temperature?.unit?.replace("°", "")?.let { "°$it" } ?: "°C",
                    label = "水温",
                )
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    TankWaterRow("盐度", salinity, "已测")
                    TankWaterRow("pH", ph, if (ph != null) "已测" else "待检测", warn = ph == null)
                }
            }
            Box(Modifier.fillMaxWidth().padding(top = 8.dp).height(1.dp).background(Color(0x24FFFFFF)))
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("只显示最近一次实测值 · 不用默认值代替", color = Color(0xFF8FB8CE), fontSize = 10.sp, modifier = Modifier.weight(1f))
                Row(
                    Modifier.clip(DetailTokens.PillShape).clickable(role = Role.Button, onClick = if (measuredAt == null) onRecord else onTrend).padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(1.dp),
                ) {
                    Text(if (measuredAt == null) "记录第一次水质" else "去水质页", color = Color(0xFFA9CCDE), fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                    Icon(FishIcons.ArrowRight, contentDescription = null, tint = Color(0xFFA9CCDE), modifier = Modifier.size(11.dp))
                }
            }
        }
    }
}

@Composable
private fun TankWaterRow(label: String, value: WaterTestValue?, tag: String, warn: Boolean = false) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        Column(Modifier.weight(1f)) {
            Text(label, color = Color(0xFF9BC3D7), fontSize = 10.sp)
            Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 1.dp)) {
                Text(value?.value?.prettyNumber() ?: "未记录", color = if (value == null) Color(0xFF86A9BE) else Color(0xFFF5FCFF), fontSize = if (value == null) 13.sp else 18.sp, fontWeight = if (value == null) FontWeight.Normal else FontWeight.Bold, maxLines = 1)
                value?.unit?.let { Text(it, color = Color(0xFF9BC3D7), fontSize = 9.5.sp, modifier = Modifier.padding(start = 2.dp, bottom = 2.dp)) }
            }
        }
        Text(
            tag,
            modifier = Modifier.clip(DetailTokens.PillShape).background(if (warn) Color(0x24E4832F) else Color(0x245FE0CD)).border(1.dp, if (warn) Color(0x52E4832F) else Color(0x525FE0CD), DetailTokens.PillShape).padding(horizontal = 6.dp, vertical = 2.dp),
            color = if (warn) Color(0xFFFFD9A0) else Color(0xFF8FEDDF),
            fontSize = 9.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun TankFishStrip(
    fish: List<Fish>,
    loading: Boolean,
    canAdd: Boolean,
    onOpenFish: (String) -> Unit,
    onAdd: () -> Unit,
    vm: TankViewModel,
) {
    if (loading) {
        Box(Modifier.fillMaxWidth().height(104.dp), contentAlignment = Alignment.Center) { LoadingState("正在核对鱼只…") }
        return
    }
    if (fish.isEmpty()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp).clip(DetailTokens.SectionShape).background(Color.White).border(1.dp, DetailTokens.GlassBorder, DetailTokens.SectionShape).padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("这个鱼缸还没有鱼只", color = DetailTokens.SecondaryInk, fontSize = 12.sp, modifier = Modifier.weight(1f))
            if (canAdd) {
                Text("＋ 添加鱼", modifier = Modifier.clip(DetailTokens.PillShape).clickable(role = Role.Button, onClick = onAdd).padding(horizontal = 6.dp, vertical = 4.dp), color = DetailTokens.Primary, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        return
    }
    LazyRow(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        items(fish, key = { it.id }) { item ->
            TankFishAvatar(fish = item, onClick = { onOpenFish(item.id) }, vm = vm)
        }
        if (canAdd) {
            item(key = "add") {
                Column(Modifier.width(82.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier.size(82.dp).clip(RoundedCornerShape(16.dp)).background(Color(0xFFF8FBFE)).border(1.dp, Color(0xFFBFD6E6), RoundedCornerShape(16.dp)).clickable(role = Role.Button, onClick = onAdd),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Outlined.Add, contentDescription = "添加鱼", tint = Color(0xFF7FA0B6), modifier = Modifier.size(22.dp)) }
                    Text("添加鱼", color = Color(0xFF7FA0B6), fontSize = 11.5.sp, modifier = Modifier.padding(top = 5.dp))
                }
            }
        }
    }
}

@Composable
private fun TankFishAvatar(fish: Fish, onClick: () -> Unit, vm: TankViewModel) {
    val shown by remember(fish.coverPhotoId) { vm.photo(fish.coverPhotoId) }.collectAsStateWithLifecycle()
    Column(Modifier.width(82.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(82.dp).clip(RoundedCornerShape(16.dp)).background(Color(0xFF0A2A44)).border(1.dp, Color(0xFFD7E7F2), RoundedCornerShape(16.dp)).clickable(role = Role.Button, onClick = onClick),
        ) {
            if (shown != null) ManagedPhotoImage(shown, thumbnail = true, modifier = Modifier.fillMaxSize())
            else Icon(FishIcons.Fish, contentDescription = null, tint = Color(0x99CFE6F7), modifier = Modifier.align(Alignment.Center).size(24.dp))
            Box(
                Modifier.align(Alignment.TopEnd).padding(5.dp).size(7.dp).clip(CircleShape).background(
                    when (fish.status) {
                        FishStatus.active -> Color(0xFF3FD0B6)
                        FishStatus.quarantine -> Color(0xFFE2A03F)
                        else -> Color(0xFF93A3B1)
                    },
                ).border(1.dp, Color.White.copy(alpha = 0.8f), CircleShape),
            )
        }
        Text(fish.name, color = Color(0xFF22445E), fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 5.dp))
    }
}

@Composable
private fun TankCareCard(
    todayFeedings: Int,
    lastFeeding: FeedingEventWithTargets?,
    lastChange: WaterChange?,
    nextTask: CareTask?,
    onRecordChange: () -> Unit,
    onAddTask: () -> Unit,
    onOpenCare: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val changeDays = lastChange?.let { daysSince(it.completedAt) }
    DetailGlassCard(modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                CareMetric("今日喂食", if (todayFeedings > 0) "$todayFeedings" else "0", if (todayFeedings > 0) "次" else null, lastFeeding?.let { relativeTimeLabel(it.event.fedAt) } ?: "今天还没记录", Modifier.weight(1f))
                Box(Modifier.width(1.dp).height(40.dp).background(Color(0xFFEFF5FA)))
                CareMetric("上次换水", changeDays?.toString() ?: "暂无", if (changeDays != null) "天前" else null, lastChange?.let { formatDate(it.completedAt) } ?: "还没有记录", Modifier.weight(1f).padding(start = 12.dp))
                Box(Modifier.width(1.dp).height(40.dp).background(Color(0xFFEFF5FA)))
                CareMetric("下次任务", nextTask?.let { formatShortDate(it.plannedAt) } ?: "未安排", null, nextTask?.let { it.type.label() } ?: "去添加计划", Modifier.weight(1f).padding(start = 12.dp))
            }
            Box(Modifier.fillMaxWidth().padding(top = 9.dp).height(1.dp).background(Color(0xFFEFF5FA)))
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("记录换水", modifier = Modifier.clip(DetailTokens.PillShape).clickable(role = Role.Button, onClick = onRecordChange).padding(vertical = 2.dp), color = DetailTokens.Primary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                Text("添加任务", modifier = Modifier.clip(DetailTokens.PillShape).clickable(role = Role.Button, onClick = onAddTask).padding(vertical = 2.dp), color = DetailTokens.Primary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                Row(Modifier.clip(DetailTokens.PillShape).clickable(role = Role.Button, onClick = onOpenCare).padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text("养护页", color = DetailTokens.TertiaryInk, fontSize = 10.5.sp)
                    Icon(FishIcons.ArrowRight, contentDescription = null, tint = DetailTokens.TertiaryInk, modifier = Modifier.size(11.dp))
                }
            }
        }
    }
}

@Composable
private fun CareMetric(label: String, value: String, unit: String?, caption: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, color = DetailTokens.TertiaryInk, fontSize = 10.sp)
        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 2.dp)) {
            Text(value, color = Color(0xFF173A54), fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            unit?.let { Text(it, color = DetailTokens.SecondaryInk, fontSize = 10.sp, modifier = Modifier.padding(start = 2.dp, bottom = 2.dp)) }
        }
        Text(caption, color = Color(0xFF9AB0C1), fontSize = 9.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 1.dp))
    }
}

@Composable
private fun TankTimelineCard(items: List<TankTimelineItem>, modifier: Modifier = Modifier) {
    DetailGlassCard(modifier.fillMaxWidth()) {
        if (items.isEmpty()) {
            Text("暂无动态。记录水质、换水或喂食后，会在这里形成一条真实时间线。", color = DetailTokens.SecondaryInk, fontSize = 11.5.sp, lineHeight = 16.sp, modifier = Modifier.padding(16.dp))
        } else {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 2.dp)) {
                items.forEachIndexed { index, item ->
                    DetailTimelineRow(
                        icon = item.icon,
                        tint = item.tint,
                        iconBackground = item.background,
                        title = item.title,
                        subtitle = item.subtitle,
                        trailing = item.trailing,
                        trailingSub = item.trailingSub,
                        showDivider = index != items.lastIndex,
                    )
                }
            }
        }
    }
}

@Composable
private fun TankProfileCard(tank: Tank, onEdit: () -> Unit, modifier: Modifier = Modifier) {
    val sizeText = listOfNotNull(tank.lengthCm, tank.widthCm, tank.heightCm).takeIf { it.size == 3 }?.joinToString(" × ") { "${it.pretty()} cm" } ?: "未记录"
    val cells = listOf(
        "尺寸" to sizeText,
        "启用日期" to (tank.startedAt?.let(::formatDate) ?: "未记录"),
        "设备" to (tank.equipment?.trim()?.takeIf(String::isNotBlank) ?: "未记录"),
        "备注" to (tank.notes?.trim()?.takeIf(String::isNotBlank) ?: "未记录"),
    )
    DetailGlassCard(modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 11.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("环境与设备", color = Color(0xFF173A54), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Text("编辑鱼缸", modifier = Modifier.clip(DetailTokens.PillShape).clickable(role = Role.Button, onClick = onEdit).padding(horizontal = 4.dp, vertical = 4.dp), color = DetailTokens.Primary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }
            Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                cells.chunked(2).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        row.forEach { (label, value) ->
                            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                Text(label, color = Color(0xFF90A7B9), fontSize = 10.sp, modifier = Modifier.width(46.dp))
                                Text(value, color = if (value == "未记录") Color(0xFFA3B6C5) else DetailTokens.BodyInk, fontSize = 11.5.sp, fontWeight = if (value == "未记录") FontWeight.Normal else FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TankMoreAction(label: String, description: String, danger: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 4.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(32.dp).clip(RoundedCornerShape(10.dp)).background(if (danger) Color(0xFFFDECEA) else Color(0xFFF1F6FA)), contentAlignment = Alignment.Center) {
            Icon(if (danger) FishIcons.Alert else Icons.Outlined.Edit, contentDescription = null, tint = if (danger) DetailTokens.Danger else DetailTokens.Primary, modifier = Modifier.size(16.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(label, color = if (danger) DetailTokens.Danger else DetailTokens.TitleInk, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(description, color = DetailTokens.TertiaryInk, fontSize = 10.5.sp, lineHeight = 14.sp, modifier = Modifier.padding(top = 2.dp))
        }
        Icon(FishIcons.ArrowRight, contentDescription = null, tint = Color(0xFF9AB2C4), modifier = Modifier.size(16.dp))
    }
}

private data class TankTimelineItem(
    val at: Long,
    val icon: ImageVector,
    val tint: Color,
    val background: Color,
    val title: String,
    val subtitle: String,
    val trailing: String?,
    val trailingSub: String?,
)

private fun buildTankTimeline(
    tests: List<WaterTestWithValues>,
    changes: List<WaterChange>,
    feedings: List<FeedingEventWithTargets>,
): List<TankTimelineItem> {
    val items = ArrayList<TankTimelineItem>()
    tests.forEach { test ->
        val measured = test.values.filter { it.value != null }
        val main = measured.firstOrNull { it.parameterCode == "temperature" } ?: measured.firstOrNull()
        val mainText = main?.let { "${parameterLabel(it.parameterCode)} ${it.value?.prettyNumber()} ${it.unit}" } ?: "水质检测"
        val extra = measured.drop(1).take(2).joinToString(" · ") { "${parameterLabel(it.parameterCode)} ${it.value?.prettyNumber()} ${it.unit}" }
        items += TankTimelineItem(
            at = test.test.measuredAt,
            icon = FishIcons.Droplet,
            tint = DetailTokens.WaterEvent,
            background = DetailTokens.WaterEventTint,
            title = "水质检测 · $mainText",
            subtitle = listOfNotNull(extra.ifBlank { null }, "${measured.size} 项").joinToString(" · "),
            trailing = relativeTimeLabel(test.test.measuredAt),
            trailingSub = null,
        )
    }
    changes.forEach { change ->
        items += TankTimelineItem(
            at = change.completedAt,
            icon = FishIcons.WaterLevel,
            tint = DetailTokens.SunInk,
            background = DetailTokens.SunTint,
            title = "换水 · ${change.volumeValue?.let { "${it.prettyNumber()} ${change.volumeUnit.orEmpty()}" } ?: "换水量未记录"}",
            subtitle = change.ratio?.let { "换水比例 ${it.pretty()}%" } ?: "容量未记录，不计算比例",
            trailing = relativeTimeLabel(change.completedAt),
            trailingSub = null,
        )
    }
    feedings.forEach { row ->
        items += TankTimelineItem(
            at = row.event.fedAt,
            icon = FishIcons.Fish,
            tint = Color(0xFF0E8E80),
            background = DetailTokens.AquaTint,
            title = "喂食 · ${row.event.foodType?.takeIf(String::isNotBlank) ?: "饲料未记录"}",
            subtitle = if (row.targets.any { it.targetType == FeedingTargetType.tank }) "全缸 1 次" else "指定鱼只",
            trailing = "1 次",
            trailingSub = relativeTimeLabel(row.event.fedAt),
        )
    }
    return items.sortedByDescending { it.at }
}

private fun daysSince(value: Long): Long {
    val now = Calendar.getInstance()
    val then = Calendar.getInstance().apply { timeInMillis = value }
    val startNow = Calendar.getInstance().apply { timeInMillis = now.timeInMillis; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
    val startThen = Calendar.getInstance().apply { timeInMillis = then.timeInMillis; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
    return ((startNow.timeInMillis - startThen.timeInMillis) / 86_400_000L).coerceAtLeast(0)
}

private fun isToday(value: Long): Boolean {
    val now = Calendar.getInstance()
    val then = Calendar.getInstance().apply { timeInMillis = value }
    return now.get(Calendar.YEAR) == then.get(Calendar.YEAR) && now.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR)
}

private fun formatShortDate(value: Long): String = SimpleDateFormat("M月d日", Locale.getDefault()).format(Date(value))

@Composable
private fun QuickActions(tankId: String, onQuickAction: (String, String) -> Unit) {
    val actions = listOf("feeding" to "快速喂食", "fish" to "添加鱼", "water" to "记录水质", "change" to "记录换水")
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        actions.chunked(2).forEach { rowActions ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowActions.forEach { (action, label) ->
                    OutlinedButton(onClick = { onQuickAction(action, tankId) }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1) }
                }
            }
        }
    }
}

@Composable
fun TankQuickFormScreen(tankId: String, action: String, editId: String? = null, onBack: () -> Unit, onOpenHistory: () -> Unit = {}, templateTestId: String? = null, vm: TankViewModel = viewModel(factory = TankViewModel.factory(LocalContext.current.applicationContext as android.app.Application))) {
    if (action == "water") { WaterRecordScreen(tankId, onBack, onOpenHistory, vm, templateTestId); return }
    if (action == "change") { WaterChangeRecordScreen(tankId, editId, onBack, vm); return }
    val summaryState by remember(tankId) { vm.summary(tankId) }.collectAsStateWithLifecycle()
    val summary = (summaryState as? TankViewModel.SummaryState.Found)?.summary
    val tank = summary?.tank
    val parameters = remember(tank?.type) { tank?.type?.let(::waterParameters).orEmpty() }
    val actionTitle = when (action) { "fish" -> "添加鱼"; "water" -> "记录水质"; "change" -> if (editId == null) "记录换水" else "编辑换水记录"; else -> "添加养护任务" }
    var name by remember { mutableStateOf("") }
    var species by remember { mutableStateOf("") }
    var values by remember(tank?.type) { mutableStateOf(parameters.associate { it.code to "" }) }
    var selectedParameters by remember(tank?.type) { mutableStateOf(parameters.map { it.code }.toSet()) }
    var units by remember(tank?.type) { mutableStateOf(parameters.associate { it.code to it.unit }) }
    var methods by remember(tank?.type) { mutableStateOf(parameters.associate { it.code to "" }) }
    var parameterNotes by remember(tank?.type) { mutableStateOf(parameters.associate { it.code to "" }) }
    var targetMins by remember(tank?.type) { mutableStateOf(parameters.associate { it.code to "" }) }
    var targetMaxs by remember(tank?.type) { mutableStateOf(parameters.associate { it.code to "" }) }
    var volume by remember { mutableStateOf("") }
    var volumeUnit by remember { mutableStateOf("L") }
    var beforeTemperature by remember { mutableStateOf("") }
    var afterTemperature by remember { mutableStateOf("") }
    var beforeSalinity by remember { mutableStateOf("") }
    var afterSalinity by remember { mutableStateOf("") }
    var supplies by remember { mutableStateOf("") }
    var nextPlannedAt by remember { mutableStateOf<Long?>(null) }
    var taskType by remember { mutableStateOf(CareTaskType.water_change) }
    var taskCustomTitle by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var date by remember { androidx.compose.runtime.mutableLongStateOf(System.currentTimeMillis()) }
    var initialDate by remember(editId) { mutableLongStateOf(date) }
    var error by remember { mutableStateOf<String?>(null) }
    var warning by remember { mutableStateOf<String?>(null) }
    var pendingWaterTest by remember { mutableStateOf<Pair<WaterTest, List<WaterTestValue>>?>(null) }
    var isSaving by remember { mutableStateOf(false) }
    var formLoaded by remember(editId) { mutableStateOf(editId == null) }
    LaunchedEffect(editId) {
        if (editId != null) {
            val record = vm.waterChange(editId)
            if (record != null && record.tankId == tankId) {
                volume = record.volumeValue?.pretty().orEmpty(); volumeUnit = record.volumeUnit ?: "L"
                beforeTemperature = record.beforeTemperature?.pretty().orEmpty(); afterTemperature = record.afterTemperature?.pretty().orEmpty()
                beforeSalinity = record.beforeSalinity?.pretty().orEmpty(); afterSalinity = record.afterSalinity?.pretty().orEmpty()
                supplies = record.supplies.orEmpty(); notes = record.notes.orEmpty(); date = record.completedAt; nextPlannedAt = record.nextPlannedAt
                initialDate = record.completedAt
            } else error = "无法加载这条换水记录"
            formLoaded = true
        }
    }
    val hasUnsavedChanges = when (action) {
        "fish" -> name.isNotBlank() || species.isNotBlank() || notes.isNotBlank()
        "water" -> date != initialDate || values.values.any(String::isNotBlank) || notes.isNotBlank() || methods.values.any(String::isNotBlank) || parameterNotes.values.any(String::isNotBlank) || targetMins.values.any(String::isNotBlank) || targetMaxs.values.any(String::isNotBlank) || selectedParameters != parameters.map { it.code }.toSet() || units != parameters.associate { it.code to it.unit }
        "change" -> date != initialDate || volume.isNotBlank() || beforeTemperature.isNotBlank() || afterTemperature.isNotBlank() || beforeSalinity.isNotBlank() || afterSalinity.isNotBlank() || supplies.isNotBlank() || notes.isNotBlank() || nextPlannedAt != null || volumeUnit != "L"
        else -> date != initialDate || taskType != CareTaskType.water_change || taskCustomTitle.isNotBlank() || notes.isNotBlank()
    }
    val leaveForm = rememberLeaveFormHandler(formLoaded && hasUnsavedChanges, onBack)
    val persistWaterTest: (WaterTest, List<WaterTestValue>) -> Unit = { test, records ->
        isSaving = true
        vm.addWaterTest(test, records, { isSaving = false; onBack() }, { message -> isSaving = false; error = message })
    }
    val taskLabels = mapOf(CareTaskType.feeding to "喂食", CareTaskType.water_change to "换水", CareTaskType.water_test to "水质检测", CareTaskType.salinity_test to "盐度检测", CareTaskType.maintenance to "设备维护", CareTaskType.custom to "自定义")

    Scaffold(topBar = { TopAppBarSimple(actionTitle, leaveForm) }, bottomBar = { Surface(shadowElevation = 4.dp) { Button(enabled = !isSaving && formLoaded && tank != null, onClick = {
        if (tank == null || isSaving || !formLoaded) return@Button
        val now = System.currentTimeMillis()
        val failed = { message: String -> isSaving = false; error = message }
        val saved = { isSaving = false; onBack() }
        when (action) {
            "fish" -> {
                if (name.isBlank()) { error = "请输入鱼只名称"; return@Button }
                isSaving = true
                vm.addFish(Fish(tankId = tankId, name = name.trim(), species = species.ifBlank { null }, coverPhotoId = null, addedAt = date, birthDate = null, status = FishStatus.active, statusChangedAt = null, notes = notes.ifBlank { null }, createdAt = now, updatedAt = now), saved, failed)
            }
            "water" -> {
                val entered = parameters.filter { it.code in selectedParameters && values[it.code].orEmpty().isNotBlank() }
                if (entered.isEmpty()) { error = "至少填写一项实测水质参数"; return@Button }
                val invalid = entered.any { parameter -> isValidWaterMeasurement(values[parameter.code].orEmpty(), targetMins[parameter.code].orEmpty(), targetMaxs[parameter.code].orEmpty()).not() }
                if (invalid) { error = "检测值和目标范围请输入有效数字，并确认下限不高于上限"; return@Button }
                isSaving = true
                val test = WaterTest(tankId = tankId, measuredAt = date, method = null, notes = notes.ifBlank { null })
                val records = entered.map { parameter ->
                    val minimum = targetMins[parameter.code]?.takeIf(String::isNotBlank)?.toDoubleOrNull()
                    val maximum = targetMaxs[parameter.code]?.takeIf(String::isNotBlank)?.toDoubleOrNull()
                    WaterTestValue(waterTestId = test.id, parameterCode = parameter.code, value = values[parameter.code]?.toDoubleOrNull(), unit = units[parameter.code] ?: parameter.unit, targetMin = minimum, targetMax = maximum, measuredAt = date, method = methods[parameter.code]?.ifBlank { null }, notes = parameterNotes[parameter.code]?.ifBlank { null }, targetSource = if (minimum != null || maximum != null) "user" else null)
                }
                val outside = records.filter { isOutsideWaterTarget(it.value!!, it.targetMin, it.targetMax) }
                if (outside.isNotEmpty()) {
                    warning = "${outside.joinToString { parameterLabel(it.parameterCode) }} 超出你设置的目标范围；请核对测量并考虑复测。不会自动调整鱼缸。"
                    pendingWaterTest = test to records
                    return@Button
                }
                persistWaterTest(test, records)
            }
            "change" -> {
                val quantity = volume.toDoubleOrNull()
                if (quantity == null || !quantity.isFinite() || quantity <= 0) { error = "请输入大于 0 的换水量"; return@Button }
                val optionalNumbers = listOf(beforeTemperature, afterTemperature, beforeSalinity, afterSalinity)
                if (optionalNumbers.any { it.isNotBlank() && (it.toDoubleOrNull()?.let { number -> number > 0 && number.isFinite() } != true) }) { error = "水温和盐度须为大于 0 的有效数值"; return@Button }
                isSaving = true
                vm.addWaterChange(WaterChange(id = editId ?: java.util.UUID.randomUUID().toString(), tankId = tankId, completedAt = date, volumeValue = quantity, volumeUnit = volumeUnit, ratio = calculateWaterChangeRatioPercent(quantity, volumeUnit, tank.capacityValue, tank.capacityUnit), nextPlannedAt = nextPlannedAt, notes = notes.ifBlank { null }, beforeTemperature = beforeTemperature.toDoubleOrNull(), afterTemperature = afterTemperature.toDoubleOrNull(), temperatureUnit = if (beforeTemperature.isNotBlank() || afterTemperature.isNotBlank()) "°C" else null, beforeSalinity = beforeSalinity.toDoubleOrNull(), afterSalinity = afterSalinity.toDoubleOrNull(), salinityUnit = if (beforeSalinity.isNotBlank() || afterSalinity.isNotBlank()) "ppt" else null, supplies = supplies.ifBlank { null }), saved, failed)
            }
            else -> {
                if (taskType == CareTaskType.custom && taskCustomTitle.isBlank()) { error = "请输入自定义任务名称"; return@Button }
                isSaving = true
                vm.addTask(CareTask(tankId = tankId, fishId = null, type = taskType, plannedAt = date, recurrenceRule = null, notes = notes.ifBlank { null }, createdAt = now, updatedAt = now, customTitle = taskCustomTitle.trim().ifBlank { null }), saved, failed)
            }
        }
    }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) { Text(if (isSaving) "保存中…" else "保存") } } }) { padding ->
        if (summaryState == TankViewModel.SummaryState.Loading) Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { LoadingState("正在加载鱼缸…") }
        else if (tank == null) Column(Modifier.fillMaxSize().padding(padding).padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(if (summaryState is TankViewModel.SummaryState.Missing) "找不到这个鱼缸" else "鱼缸加载失败", modifier = Modifier.testTag("tank-action-not-found"), style = MaterialTheme.typography.titleLarge)
            TextButton(onClick = onBack) { Text("返回") }
        }
        else Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("关联鱼缸：${tank.name}", style = MaterialTheme.typography.titleSmall)
            when (action) {
                "fish" -> {
                    OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("鱼只名称 *") }, singleLine = true)
                    OutlinedTextField(species, { species = it }, Modifier.fillMaxWidth(), label = { Text("品种") }, singleLine = true)
                    DateField("入缸日期", date) { date = it ?: date }
                    OutlinedTextField(notes, { notes = it }, Modifier.fillMaxWidth(), label = { Text("备注") }, minLines = 2)
                }
                "water" -> {
                    Text("${if (tank.type == TankType.freshwater) "淡水" else "海水"}水质参数")
                    Text("选择本次要测的参数", style = MaterialTheme.typography.titleSmall)
                    parameters.forEach { parameter ->
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = parameter.code in selectedParameters, onCheckedChange = { checked -> selectedParameters = if (checked) selectedParameters + parameter.code else selectedParameters - parameter.code })
                                Text(parameter.label, modifier = Modifier.weight(1f))
                                var unitMenu by remember(parameter.code) { mutableStateOf(false) }
                                Box { TextButton(onClick = { unitMenu = true }) { Text(units[parameter.code] ?: parameter.unit) }; DropdownMenu(unitMenu, { unitMenu = false }) { parameter.units.forEach { option -> DropdownMenuItem(text = { Text(option) }, onClick = { units = units + (parameter.code to option); unitMenu = false }) } } }
                            }
                            if (parameter.code in selectedParameters) {
                                OutlinedTextField(values[parameter.code].orEmpty(), { values = values + (parameter.code to it) }, Modifier.fillMaxWidth(), label = { Text("${parameter.label}（${units[parameter.code] ?: parameter.unit}）") }, singleLine = true)
                                OutlinedTextField(methods[parameter.code].orEmpty(), { methods = methods + (parameter.code to it) }, Modifier.fillMaxWidth(), label = { Text("测量方式（可选）") }, singleLine = true)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(targetMins[parameter.code].orEmpty(), { targetMins = targetMins + (parameter.code to it) }, Modifier.weight(1f), label = { Text("目标下限（可选）") }, singleLine = true)
                                    OutlinedTextField(targetMaxs[parameter.code].orEmpty(), { targetMaxs = targetMaxs + (parameter.code to it) }, Modifier.weight(1f), label = { Text("目标上限（可选）") }, singleLine = true)
                                }
                                Text("目标范围由你设置，仅作记录参考", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                OutlinedTextField(parameterNotes[parameter.code].orEmpty(), { parameterNotes = parameterNotes + (parameter.code to it) }, Modifier.fillMaxWidth(), label = { Text("参数备注（可选）") }, minLines = 1)
                            }
                        }
                    }
                    DateTimeField("检测时间", date) { date = it }
                    OutlinedTextField(notes, { notes = it }, Modifier.fillMaxWidth(), label = { Text("备注") }, minLines = 2)
                }
                "change" -> {
                    tank.capacityValue?.let { Text("鱼缸容量：${it.pretty()} ${tank.capacityUnit.orEmpty()}${if (tank.capacityIsEstimated) "（估算）" else ""}；保存时据此计算比例。", style = MaterialTheme.typography.bodySmall) } ?: Text("鱼缸容量未记录；只保存换水量，不计算比例。", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(volume, { volume = it }, Modifier.fillMaxWidth(), label = { Text("换水量 *") }, singleLine = true)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("L", "gal").forEach { FilterChip(volumeUnit == it, { volumeUnit = it }, label = { Text(it) }) } }
                    DateTimeField("实际完成时间", date) { date = it }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(beforeTemperature, { beforeTemperature = it }, Modifier.weight(1f), label = { Text("换水前水温 °C") }, singleLine = true)
                        OutlinedTextField(afterTemperature, { afterTemperature = it }, Modifier.weight(1f), label = { Text("换水后水温 °C") }, singleLine = true)
                    }
                    if (tank.type == TankType.saltwater) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(beforeSalinity, { beforeSalinity = it }, Modifier.weight(1f), label = { Text("换水前盐度 ppt") }, singleLine = true)
                        OutlinedTextField(afterSalinity, { afterSalinity = it }, Modifier.weight(1f), label = { Text("换水后盐度 ppt") }, singleLine = true)
                    }
                    OutlinedTextField(supplies, { supplies = it }, Modifier.fillMaxWidth(), label = { Text("用品（可选）") }, singleLine = true)
                    DateField("下一次换水计划（可选）", nextPlannedAt) { nextPlannedAt = it }
                    OutlinedTextField(notes, { notes = it }, Modifier.fillMaxWidth(), label = { Text("备注") }, minLines = 2)
                }
                else -> {
                    var menuOpen by remember { mutableStateOf(false) }
                    val availableTaskLabels = taskLabels.filterKeys { it != CareTaskType.salinity_test || (tank.type == TankType.saltwater && tank.salinityManagementEnabled) }
                    Box { OutlinedButton(onClick = { menuOpen = true }) { Text("任务类型：${availableTaskLabels[taskType] ?: "换水"}") }; DropdownMenu(menuOpen, { menuOpen = false }) { availableTaskLabels.forEach { (type, label) -> DropdownMenuItem(text = { Text(label) }, onClick = { taskType = type; menuOpen = false }) } } }
                    if (taskType == CareTaskType.custom) OutlinedTextField(taskCustomTitle, { taskCustomTitle = it }, Modifier.fillMaxWidth(), label = { Text("自定义任务名称 *") }, singleLine = true)
                    DateField("计划时间", date) { date = it ?: date }
                    OutlinedTextField(notes, { notes = it }, Modifier.fillMaxWidth(), label = { Text("备注") }, minLines = 2)
                }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            warning?.let { message ->
                AlertDialog(onDismissRequest = { warning = null; pendingWaterTest = null }, title = { Text("请核对测量") }, text = { Text(message) }, confirmButton = { TextButton(onClick = { warning = null; pendingWaterTest?.let { persistWaterTest(it.first, it.second) }; pendingWaterTest = null }) { Text("已核对，仍然保存") } }, dismissButton = { TextButton(onClick = { warning = null; pendingWaterTest = null }) { Text("返回检查") } })
            }
        }
    }
}

@Composable
private fun WaterQualityPanel(type: TankType, tests: List<WaterTestWithValues>, onRecord: () -> Unit) {
    val parameters = remember(type) { waterParameters(type) }
    val measured = remember(tests) { tests.flatMap { record -> record.values.filter { it.value != null }.map { value -> Triple(record.test, value, value.measuredAt ?: record.test.measuredAt) } } }
    var selectedCode by remember(type) { mutableStateOf(parameters.firstOrNull()?.code.orEmpty()) }
    var fromDate by remember { mutableStateOf<Long?>(null) }
    var toDate by remember { mutableStateOf<Long?>(null) }
    val latestByParameter = measured.groupBy { it.second.parameterCode }.mapValues { (_, values) -> values.maxBy { it.third } }

    Text("最近水质", style = MaterialTheme.typography.titleMedium)
    if (latestByParameter.isEmpty()) {
        InfoCard("尚无测量数据", content = { Text("保存实测值后，这里会显示最近水质摘要和趋势。") })
    } else {
        latestByParameter.toSortedMap().forEach { (code, point) ->
            val (test, value, measuredAt) = point
            InfoCard(parameterLabel(code), content = {
                Text("${value.value?.prettyNumber() ?: "未测量"} ${value.unit}", style = MaterialTheme.typography.titleLarge)
                Text("测量时间：${formatDate(measuredAt)}")
                if (value.targetMin != null || value.targetMax != null) {
                    val range = "${value.targetMin?.pretty() ?: "不限"} - ${value.targetMax?.pretty() ?: "不限"} ${value.unit}"
                    Text("${targetSourceLabel(value.targetSource)}：$range", style = MaterialTheme.typography.bodySmall)
                }
                if (test.method != null || value.method != null) Text("方式：${value.method ?: test.method}", style = MaterialTheme.typography.bodySmall)
            })
        }
    }

    Text("参数趋势", style = MaterialTheme.typography.titleMedium)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        parameters.forEach { parameter -> FilterChip(selected = selectedCode == parameter.code, onClick = { selectedCode = parameter.code }, label = { Text(parameter.label) }) }
    }
    val endOfSelectedDay = toDate?.plus(86_399_999L)
    val trend = measured.filter { it.second.parameterCode == selectedCode && (fromDate == null || it.third >= fromDate!!) && (endOfSelectedDay == null || it.third <= endOfSelectedDay) }.sortedBy { it.third }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        DateField("开始日期", fromDate) { fromDate = it }
        DateField("结束日期", toDate) { toDate = it }
    }
    if (trend.isEmpty()) InfoCard("暂无${parameterLabel(selectedCode)}数据", content = { Text("所选时间范围内没有实测点。") })
    else WaterTrendGraph(trend)

    Text("检测历史", style = MaterialTheme.typography.titleMedium)
    var historyCode by remember(type) { mutableStateOf("all") }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        FilterChip(historyCode == "all", { historyCode = "all" }, label = { Text("全部") })
        parameters.forEach { parameter -> FilterChip(historyCode == parameter.code, { historyCode = parameter.code }, label = { Text(parameter.label) }) }
    }
    val history = tests.filter { record ->
        (fromDate == null || record.test.measuredAt >= fromDate!!) && (endOfSelectedDay == null || record.test.measuredAt <= endOfSelectedDay) && (historyCode == "all" || record.values.any { it.parameterCode == historyCode })
    }
    if (history.isEmpty()) Text("所选条件下暂无检测记录") else history.forEach { record ->
        InfoCard(formatDate(record.test.measuredAt), content = {
            record.values.filter { historyCode == "all" || it.parameterCode == historyCode }.forEach { value ->
                Text("${parameterLabel(value.parameterCode)}：${value.value?.prettyNumber() ?: "未记录"}${value.value?.let { " ${value.unit}" }.orEmpty()}")
                if (value.targetMin != null || value.targetMax != null) Text("${targetSourceLabel(value.targetSource)}：${value.targetMin?.pretty() ?: "不限"} - ${value.targetMax?.pretty() ?: "不限"} ${value.unit}", style = MaterialTheme.typography.bodySmall)
                (value.notes ?: record.test.notes)?.let { Text("备注：$it", style = MaterialTheme.typography.bodySmall) }
            }
        })
    }
    Button(onClick = onRecord, modifier = Modifier.fillMaxWidth()) { Text("添加水质记录") }
}

@Composable
private fun WaterTrendGraph(points: List<Triple<WaterTest, WaterTestValue, Long>>) {
    val sorted = points.sortedBy { it.third }
    val color = MaterialTheme.colorScheme.primary
    val axisColor = MaterialTheme.colorScheme.outlineVariant
    InfoCard("${parameterLabel(sorted.first().second.parameterCode)}趋势", content = {
        Text("单位：${sorted.first().second.unit} · ${sorted.size} 个实测点", style = MaterialTheme.typography.bodySmall)
        Column(Modifier.horizontalScroll(rememberScrollState())) {
            Column {
                Canvas(Modifier.width((sorted.size * 76).coerceAtLeast(260).dp).height(160.dp)) {
                    val values = sorted.mapNotNull { it.second.value }
                    val min = values.minOrNull() ?: return@Canvas
                    val max = values.maxOrNull() ?: min
                    val spread = (max - min).takeIf { it > 0.0 } ?: 1.0
                    val left = 28f
                    val right = size.width - 12f
                    val top = 14f
                    val bottom = size.height - 22f
                    val coordinates = sorted.mapIndexedNotNull { index, item ->
                        val value = item.second.value ?: return@mapIndexedNotNull null
                        val x = if (sorted.size == 1) (left + right) / 2f else left + (right - left) * index / (sorted.size - 1)
                        val y = bottom - ((value - min) / spread).toFloat() * (bottom - top)
                        Offset(x, y)
                    }
                    drawLine(axisColor, Offset(left, bottom), Offset(right, bottom), strokeWidth = 1.dp.toPx())
                    if (coordinates.size > 1) {
                        val path = Path().apply { moveTo(coordinates.first().x, coordinates.first().y); coordinates.drop(1).forEach { lineTo(it.x, it.y) } }
                        drawPath(path, color, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx()))
                    }
                    coordinates.forEach { drawCircle(color, radius = 4.dp.toPx(), center = it) }
                }
                Row(Modifier.width((sorted.size * 76).coerceAtLeast(260).dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    sorted.forEach { (_, value, measuredAt) ->
                        Column(Modifier.width(72.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(formatDate(measuredAt).takeLast(5), style = MaterialTheme.typography.labelSmall)
                            Text(value.value?.prettyNumber().orEmpty(), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    })
}

@Composable
private fun TopAppBarSimple(title: String, onBack: () -> Unit, action: @Composable RowScope.() -> Unit = {}) {
    TopAppBar(title = { Text(title) }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回") } }, actions = action)
}

@Composable
private fun DateField(label: String, value: Long?, onChange: (Long?) -> Unit) {
    val context = LocalContext.current
    OutlinedButton(onClick = {
        val calendar = Calendar.getInstance().apply { if (value != null) timeInMillis = value }
        DatePickerDialog(context, { _, year, month, day -> onChange(Calendar.getInstance().apply { set(year, month, day, 12, 0, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis) }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
    }, modifier = Modifier.fillMaxWidth()) { Text("$label  ${value?.let(::formatDate) ?: "选择日期"}") }
}

@Composable
private fun DateTimeField(label: String, value: Long, onChange: (Long) -> Unit) {
    val context = LocalContext.current
    OutlinedButton(onClick = {
        val initial = Calendar.getInstance().apply { timeInMillis = value }
        DatePickerDialog(context, { _, year, month, day ->
            val selectedDate = Calendar.getInstance().apply {
                timeInMillis = value
                set(Calendar.YEAR, year); set(Calendar.MONTH, month); set(Calendar.DAY_OF_MONTH, day)
            }
            TimePickerDialog(context, { _, hour, minute ->
                selectedDate.set(Calendar.HOUR_OF_DAY, hour); selectedDate.set(Calendar.MINUTE, minute); selectedDate.set(Calendar.SECOND, 0); selectedDate.set(Calendar.MILLISECOND, 0)
                onChange(selectedDate.timeInMillis)
            }, initial.get(Calendar.HOUR_OF_DAY), initial.get(Calendar.MINUTE), true).show()
        }, initial.get(Calendar.YEAR), initial.get(Calendar.MONTH), initial.get(Calendar.DAY_OF_MONTH)).show()
    }, modifier = Modifier.fillMaxWidth()) {
        Text("$label  ${SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(value))}")
    }
}

private fun formatDate(value: Long) = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(value))
private fun shortDate(value: Long) = SimpleDateFormat("MM-dd", Locale.getDefault()).format(Date(value))
private fun Double.pretty() = if (this % 1.0 == 0.0) toLong().toString() else "%.1f".format(Locale.getDefault(), this)
private fun targetSourceLabel(source: String?) = when (source) { "user" -> "目标范围（用户设置）"; "config" -> "目标范围（来源配置）"; else -> "目标范围（来源未记录）" }
private fun FishStatus.toChineseLabel() = when (this) { FishStatus.active -> "在养"; FishStatus.quarantine -> "检疫"; FishStatus.transferred -> "已转出"; FishStatus.deceased -> "已故"; FishStatus.archived -> "已归档" }
private fun CareTaskType.label() = when (this) { CareTaskType.feeding -> "喂食"; CareTaskType.water_change -> "换水"; CareTaskType.water_test -> "水质检测"; CareTaskType.salinity_test -> "盐度检测"; CareTaskType.maintenance -> "设备维护"; CareTaskType.custom -> "自定义" }

@Composable
fun TankRecordsScreen(
    tankId: String,
    section: String,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onOpenFish: (String) -> Unit,
    onQuickAction: (String, String) -> Unit,
    vm: TankViewModel = viewModel(factory = TankViewModel.factory(LocalContext.current.applicationContext as Application)),
) {
    val summaryState by remember(tankId) { vm.summary(tankId) }.collectAsStateWithLifecycle()
    val summary = (summaryState as? TankViewModel.SummaryState.Found)?.summary
    val tank = summary?.tank
    val fishState by remember(tankId) { vm.fishState(tankId) }.collectAsStateWithLifecycle()
    val fish = (fishState as? TankFishState.Success)?.fish.orEmpty()
    val tests by remember(tankId) { vm.tests(tankId) }.collectAsStateWithLifecycle(initialValue = emptyList())
    val changes by remember(tankId) { vm.changes(tankId) }.collectAsStateWithLifecycle(initialValue = emptyList())
    val tasks by remember(tankId) { vm.tasks(tankId) }.collectAsStateWithLifecycle(initialValue = emptyList())
    val feedings by remember(tankId) { vm.feedings(tankId) }.collectAsStateWithLifecycle(initialValue = emptyList())
    val listState = rememberLazyListState()
    val title = when (section) {
        "water" -> "水质与趋势"
        "fish" -> "鱼只"
        "care" -> "养护"
        else -> "全部记录"
    }
    val targetIndex = when (section) {
        "water" -> 1
        "fish" -> 2
        "care" -> 3
        "activity" -> 4
        else -> 0
    }
    LaunchedEffect(tankId, section) { listState.scrollToItem(targetIndex) }

    Scaffold(
        containerColor = DetailTokens.PageBackground,
        topBar = { TopAppBarSimple(title, onBack) },
    ) { padding ->
        if (summaryState == TankViewModel.SummaryState.Loading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { LoadingState("正在加载鱼缸记录…") }
        } else if (summary == null || tank == null) {
            Column(Modifier.fillMaxSize().padding(padding).padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("找不到这个鱼缸", modifier = Modifier.testTag("tank-not-found"), style = MaterialTheme.typography.titleLarge)
                TextButton(onClick = onBack) { Text("返回") }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(key = "summary") {
                    TankRecordsSummary(summary!!, tests.size, changes.size)
                }
                item(key = "water") {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        DetailSectionHeader("水质与趋势", countLabel = "${tests.size} 次检测")
                        WaterQualityPanel(tank.type, tests, onRecord = { onQuickAction("water", tankId) })
                        Text("换水历史 · ${changes.size} 次", style = MaterialTheme.typography.titleMedium, color = DetailTokens.TitleInk)
                        if (changes.isEmpty()) {
                            Text("暂无换水记录。保存实际完成的换水后，记录会显示在这里。", color = DetailTokens.SecondaryInk, fontSize = 12.sp)
                        } else {
                            changes.forEach { change ->
                                InfoCard(formatDate(change.completedAt), content = {
                                    Text("换水量：${change.volumeValue?.let { "${it.prettyNumber()} ${change.volumeUnit.orEmpty()}" } ?: "未记录"}")
                                    if (change.ratio != null) Text("换水比例：${change.ratio.pretty()}%${if (tank.capacityIsEstimated) " · 容量估算" else ""}")
                                    if (change.beforeTemperature != null || change.afterTemperature != null) Text("水温：${change.beforeTemperature?.prettyNumber() ?: "未记录"} → ${change.afterTemperature?.prettyNumber() ?: "未记录"} ${change.temperatureUnit.orEmpty()}")
                                    if (change.beforeSalinity != null || change.afterSalinity != null) Text("盐度：${change.beforeSalinity?.prettyNumber() ?: "未记录"} → ${change.afterSalinity?.prettyNumber() ?: "未记录"} ${change.salinityUnit.orEmpty()}")
                                    change.supplies?.let { Text("用品：$it") }
                                    change.notes?.let { Text("备注：$it") }
                                    TextButton(onClick = { onQuickAction("change/${change.id}", tankId) }) { Text("编辑记录") }
                                })
                            }
                        }
                    }
                }
                item(key = "fish") {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        DetailSectionHeader("鱼只", countLabel = "在养 ${fish.count { it.status == FishStatus.active }} 条", actionLabel = "添加鱼", onAction = { onQuickAction("fish", tankId) })
                        if (fishState is TankFishState.Loading) LoadingState("正在加载鱼只…")
                        else if (fishState is TankFishState.Error) ErrorState("鱼只加载失败。${(fishState as TankFishState.Error).message}", onRetry = vm::retryFish)
                        else if (fish.isEmpty()) EmptyState("暂无鱼只", "添加鱼只档案后，可在这里查看所属鱼缸与状态。")
                        else fish.forEach { record -> TankRecordsFishRow(record, vm, onOpenFish) }
                    }
                }
                item(key = "care") {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        DetailSectionHeader("养护", countLabel = "计划与事实分开", actionLabel = "添加任务", onAction = { onQuickAction("task", tankId) })
                        if (tasks.isEmpty()) Text("暂无养护任务。", color = DetailTokens.SecondaryInk, fontSize = 12.sp)
                        tasks.forEach { task ->
                            InfoCard(task.type.label(), content = {
                                Text("计划：${formatDateTime(task.plannedAt)}")
                                Text("状态：${careTaskStatusLabel(task.status)}")
                                task.notes?.takeIf(String::isNotBlank)?.let { Text("备注：$it") }
                            })
                        }
                        changes.mapNotNull { change -> change.nextPlannedAt?.let { it to change } }.sortedBy { it.first }.forEach { (plannedAt, change) ->
                            InfoCard("换水计划", content = {
                                Text("计划日期：${formatDate(plannedAt)}")
                                Text("对应完成记录：${formatDate(change.completedAt)}")
                                TextButton(onClick = { onQuickAction("change/${change.id}", tankId) }) { Text("编辑记录与计划") }
                            })
                        }
                    }
                }
                item(key = "activity") {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        DetailSectionHeader("全部记录", countLabel = "${tests.size + changes.size + feedings.size} 条")
                        TankTimelineCard(items = buildTankTimeline(tests, changes, feedings), modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
}

@Composable
private fun TankRecordsSummary(summary: TankSummary, testCount: Int, changeCount: Int) {
    DetailGlassCard {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("真实记录统计", color = DetailTokens.TitleInk, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Text("只包含已保存的本机记录", color = DetailTokens.TertiaryInk, fontSize = 10.sp)
            }
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                GlassMetric("在养鱼只", summary.activeFishCount.toString(), "条", Modifier.weight(1f))
                GlassMetric("水质检测", testCount.toString(), "次", Modifier.weight(1f))
                GlassMetric("换水记录", changeCount.toString(), "次", Modifier.weight(1f))
                GlassMetric("容量", summary.tank.capacityValue?.prettyNumber() ?: "未记录", summary.tank.capacityUnit, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun TankRecordsFishRow(fish: Fish, vm: TankViewModel, onOpen: (String) -> Unit) {
    val photo by remember(fish.coverPhotoId) { vm.photo(fish.coverPhotoId) }.collectAsStateWithLifecycle()
    Row(
        Modifier.fillMaxWidth().clip(DetailTokens.SectionShape).background(Color.White).border(1.dp, DetailTokens.GlassBorder, DetailTokens.SectionShape)
            .clickable(role = Role.Button, onClick = { onOpen(fish.id) }).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(52.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF0A2A44))) {
            if (photo != null) ManagedPhotoImage(photo, thumbnail = true, modifier = Modifier.fillMaxSize())
            else Icon(FishIcons.Fish, contentDescription = null, tint = Color(0x99CFE6F7), modifier = Modifier.align(Alignment.Center).size(22.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(fish.name, color = DetailTokens.TitleInk, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
            Text("${fish.species ?: "鱼种未记录"} · ${fishStatusLabel(fish.status)}", color = DetailTokens.SecondaryInk, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("入缸 ${formatDate(fish.addedAt)}", color = DetailTokens.TertiaryInk, fontSize = 10.sp)
        }
        Icon(FishIcons.ArrowRight, contentDescription = null, tint = Color(0xFF9AB2C4), modifier = Modifier.size(18.dp))
    }
}

private fun careTaskStatusLabel(status: CareTaskStatus): String = when (status) {
    CareTaskStatus.pending -> "待完成"
    CareTaskStatus.completed -> "已完成"
    CareTaskStatus.skipped -> "已跳过"
    CareTaskStatus.overdue -> "已逾期"
    CareTaskStatus.cancelled -> "已取消"
}

private fun formatDateTime(value: Long): String = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(value))

private fun fishStatusLabel(status: FishStatus): String = when (status) {
    FishStatus.active -> "在养"
    FishStatus.quarantine -> "隔离"
    FishStatus.transferred -> "已转出"
    FishStatus.deceased -> "已故"
    FishStatus.archived -> "已归档"
}

@Composable
fun TankRecordsPage(
    tankId: String,
    section: String,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onOpenFish: (String) -> Unit,
    onEditFish: (String) -> Unit,
    onQuickAction: (String, String) -> Unit,
    onRecordWaterTemplate: (String) -> Unit = {},
    onEditWaterChange: (String) -> Unit = {},
    initialWaterTab: String? = null,
    vm: TankViewModel = viewModel(factory = TankViewModel.factory(LocalContext.current.applicationContext as Application)),
) {
    when (section) {
        "fish" -> TankFishManagementPage(tankId, onBack, onOpenFish, onEditFish, { onQuickAction("fish", tankId) }, vm)
        "care" -> TankCareSchedulePage(tankId, onBack, { onQuickAction("task", tankId) }, { onQuickAction("water", tankId) }, vm)
        "activity" -> TankActivityPage(tankId, onBack, onOpenFish, vm)
        else -> TankWaterRecordsPage(tankId, onBack, { onQuickAction("water", tankId) }, { onQuickAction("change", tankId) }, onEditWaterChange, onRecordWaterTemplate, initialWaterTab, vm)
    }
}

@Composable
private fun TankPageMessage(title: String, message: String, onBack: () -> Unit, testTag: String? = null) {
    Column(Modifier.fillMaxSize().background(DetailTokens.PageBackground).padding(24.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = if (testTag != null) Modifier.testTag(testTag) else Modifier)
        Text(message, color = DetailTokens.SecondaryInk)
        TextButton(onClick = onBack) { Text("返回") }
    }
}

private enum class TankFishFilter(val label: String) { All("全部"), Active("在养"), Quarantine("隔离"), Other("其他") }

private fun fishFilter(fish: Fish): TankFishFilter = when (fish.status) {
    FishStatus.active -> TankFishFilter.Active
    FishStatus.quarantine -> TankFishFilter.Quarantine
    else -> TankFishFilter.Other
}

@Composable
private fun TankFishManagementPage(
    tankId: String,
    onBack: () -> Unit,
    onOpenFish: (String) -> Unit,
    onEditFish: (String) -> Unit,
    onAddFish: () -> Unit,
    vm: TankViewModel,
) {
    val summaryState by remember(tankId) { vm.summary(tankId) }.collectAsStateWithLifecycle()
    val fishState by remember(tankId) { vm.fishState(tankId) }.collectAsStateWithLifecycle()
    val tank = (summaryState as? TankViewModel.SummaryState.Found)?.summary?.tank
    val fish = (fishState as? TankFishState.Success)?.fish.orEmpty()
    val cover by remember(tank?.photoId) { vm.photo(tank?.photoId) }.collectAsStateWithLifecycle()
    var search by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(TankFishFilter.All) }
    val filtered = fish.filter { f ->
        (filter == TankFishFilter.All || fishFilter(f) == filter) &&
            (search.isBlank() || f.name.contains(search, true) || f.species?.contains(search, true) == true)
    }
    val counts = TankFishFilter.entries.associateWith { f -> if (f == TankFishFilter.All) fish.size else fish.count { fishFilter(it) == f } }
    if (tank == null) {
        if (summaryState is TankViewModel.SummaryState.Loading) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingState("正在加载鱼群") }
        else TankPageMessage("找不到这个鱼缸", "鱼缸可能已被删除或链接已失效。", onBack, "tank-not-found")
        return
    }
    Box(Modifier.fillMaxSize().background(DetailTokens.PageBackground)) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxWidth().height(156.dp)) {
                DetailHeroPhoto(cover, Modifier.fillMaxSize())
                HeroScrim(); HeroWaterEffects()
                Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    HeroOrbButton(Icons.AutoMirrored.Outlined.ArrowBack, "返回", onBack)
                    Spacer(Modifier.weight(1f))
                    HeroOrbButton(Icons.Outlined.Add, "添加鱼", onAddFish)
                }
                Column(Modifier.align(Alignment.BottomStart).padding(start = 18.dp, bottom = 16.dp)) {
                    Text("缸内鱼群", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                    Text("${tank.name} · ${tankTypeLabel(tank.type)} · 运行 ${daysBetween(tank.startedAt)}", color = Color(0xFFD5E9F4), fontSize = 10.5.sp, modifier = Modifier.padding(top = 2.dp))
                }
            }
            Row(Modifier.padding(horizontal = 16.dp).offset(y = (-22).dp).clip(DetailTokens.SectionShape).background(Color.White).border(1.dp, DetailTokens.GlassBorder, DetailTokens.SectionShape).padding(12.dp)) {
                GlassMetric("在养", counts[TankFishFilter.Active].toString(), "条", Modifier.weight(1f))
                GlassMetric("隔离", counts[TankFishFilter.Quarantine].toString(), "条", Modifier.weight(1f))
                GlassMetric("其他", counts[TankFishFilter.Other].toString(), "条", Modifier.weight(1f))
                Text("共 ${fish.size} 条", color = DetailTokens.TertiaryInk, fontSize = 10.sp, modifier = Modifier.align(Alignment.CenterVertically).padding(start = 8.dp))
            }
            Row(Modifier.padding(horizontal = 16.dp).offset(y = (-8).dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                SubpageSearch(search, { search = it }, "搜索鱼名或鱼种", Modifier.weight(1f))
            }
            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TankFishFilter.entries.forEach { f -> FilterPill("${f.label} ${counts[f] ?: 0}", filter == f) { filter = f } }
            }
            if (filtered.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth().padding(16.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        EmptyState(if (fish.isEmpty()) "这个缸还没有鱼" else "没有匹配的鱼", if (fish.isEmpty()) "添加第一条鱼，开始记录它的成长。" else "换个关键词或状态试试。")
                        if (fish.isEmpty()) Button(onClick = onAddFish, shape = DetailTokens.ButtonShape) { Text("添加鱼") }
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
                    horizontalArrangement = Arrangement.spacedBy(11.dp),
                    verticalArrangement = Arrangement.spacedBy(11.dp),
                ) {
                    items(filtered.size, key = { filtered[it].id }) { index -> TankFishGridCard(filtered[index], vm, onOpenFish, onEditFish) }
                }
            }
        }
        SubpageFab("添加鱼", Icons.Outlined.Add, onClick = onAddFish, modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 24.dp))
    }
}

@Composable
private fun TankFishGridCard(fish: Fish, vm: TankViewModel, onOpen: (String) -> Unit, onEdit: (String) -> Unit) {
    val photo by remember(fish.coverPhotoId) { vm.photo(fish.coverPhotoId) }.collectAsStateWithLifecycle()
    Column(Modifier.clip(DetailTokens.SectionShape).background(Color.White).border(1.dp, DetailTokens.GlassBorder, DetailTokens.SectionShape).clickable { onOpen(fish.id) }) {
        Box(Modifier.fillMaxWidth().aspectRatio(4f / 3f).background(Color(0xFF0A2A44))) {
            if (photo != null) ManagedPhotoImage(photo, true, Modifier.fillMaxSize())
            Row(Modifier.align(Alignment.TopStart).padding(7.dp).clip(DetailTokens.PillShape).background(Color(0xB80B554B)).border(1.dp, Color(0x885FE0CD), DetailTokens.PillShape).padding(horizontal = 7.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(4.dp).clip(CircleShape).background(Color(0xFF8FEDDF)))
                Text(fishStatusLabel(fish.status), color = Color(0xFFEAFBF6), fontSize = 9.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 4.dp))
            }
            Box(Modifier.align(Alignment.TopEnd).padding(5.dp).size(25.dp).clip(CircleShape).background(Color(0x88051A2A)).border(1.dp, Color(0x55D6F3FF), CircleShape).clickable { onEdit(fish.id) }, contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.Edit, contentDescription = "编辑鱼只", tint = Color(0xFFEAF4FB), modifier = Modifier.size(13.dp))
            }
        }
        Column(Modifier.padding(horizontal = 9.dp, vertical = 8.dp)) {
            Text(fish.name, color = DetailTokens.TitleInk, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(fish.species?.takeIf { it.isNotBlank() } ?: "鱼种未记录", color = DetailTokens.SecondaryInk, fontSize = 10.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("入缸 ${daysBetween(fish.addedAt)}", color = DetailTokens.TertiaryInk, fontSize = 9.5.sp)
                Text("查看 ›", color = DetailTokens.Primary, fontSize = 9.5.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

private fun tankTypeLabel(type: TankType): String = if (type == TankType.saltwater) "海水缸" else "淡水缸"

private data class TankDoneItem(val at: Long, val title: String, val subtitle: String)

@Composable
private fun TankCareSchedulePage(tankId: String, onBack: () -> Unit, onAddTask: () -> Unit, onRecordWater: () -> Unit, vm: TankViewModel) {
    val summaryState by remember(tankId) { vm.summary(tankId) }.collectAsStateWithLifecycle()
    val tank = (summaryState as? TankViewModel.SummaryState.Found)?.summary?.tank
    val tasks by remember(tankId) { vm.tasks(tankId) }.collectAsStateWithLifecycle(initialValue = emptyList())
    val changes by remember(tankId) { vm.changes(tankId) }.collectAsStateWithLifecycle(initialValue = emptyList())
    val tests by remember(tankId) { vm.tests(tankId) }.collectAsStateWithLifecycle(initialValue = emptyList())
    val feedings by remember(tankId) { vm.feedings(tankId) }.collectAsStateWithLifecycle(initialValue = emptyList())
    val careVm: CareViewModel = viewModel(factory = CareViewModel.factory(LocalContext.current.applicationContext as Application))
    val careRecords by careVm.records.collectAsStateWithLifecycle()
    val fish by remember(tankId) { vm.fish(tankId) }.collectAsStateWithLifecycle(initialValue = emptyList())
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var postponing by remember { mutableStateOf<CareTask?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(tankId) { careVm.refreshOverdue() }
    if (tank == null) {
        if (summaryState is TankViewModel.SummaryState.Loading) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingState("正在加载养护日程") }
        else TankPageMessage("找不到这个鱼缸", "鱼缸可能已被删除或链接已失效。", onBack, "tank-not-found")
        return
    }
    val upcoming = tasks.filter { it.status == CareTaskStatus.pending || it.status == CareTaskStatus.overdue }.sortedBy { it.plannedAt }
    val fishIds = fish.map { it.id }.toSet()
    val done = buildList {
        careRecords.filter { it.tankId == tankId || it.fishId in fishIds }.forEach { add(TankDoneItem(it.completedAt, "养护 · ${careTaskTypeLabel(it.type)}", it.notes ?: "已完成")) }
        tests.forEach { add(TankDoneItem(it.test.measuredAt, "水质检测", "${it.values.size} 项实测值")) }
        feedings.forEach { add(TankDoneItem(it.event.fedAt, "喂食 · ${it.event.foodType ?: "饲料未记录"}", "全缸 ${it.targets.count { target -> target.targetType == FeedingTargetType.tank }.coerceAtLeast(1)} 次")) }
        changes.forEach { add(TankDoneItem(it.completedAt, "换水${it.volumeValue?.let { v -> " · ${v.prettyNumber()} ${it.volumeUnit.orEmpty()}" } ?: ""}", it.ratio?.let { r -> "换水比例 ${r.prettyNumber()}%" } ?: "换水记录")) }
    }.sortedByDescending { it.at }.take(3)
    Scaffold(
        containerColor = DetailTokens.PageBackground,
        topBar = { SubpageTopBar("养护日程", "${tank.name} · ${tankTypeLabel(tank.type)}", onBack, actionIcon = Icons.Outlined.Add, actionDescription = "添加任务", onAction = onAddTask, titleModifier = Modifier.testTag("tank-records-title")) },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = { SubpageFab("添加任务", Icons.Outlined.Add, onClick = onAddTask, modifier = Modifier.navigationBarsPadding()) },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            item { CareWeekCard(tasks) }
            item { DetailSectionHeader("接下来", countLabel = "${upcoming.size} 项") }
            if (upcoming.isEmpty()) {
                item {
                    Column(Modifier.fillMaxWidth().clip(DetailTokens.SectionShape).background(Color.White).border(1.dp, DetailTokens.GlassBorder, DetailTokens.SectionShape).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("这个缸还没有养护计划", color = DetailTokens.TitleInk, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text("把换水、水质检测或喂食安排进来，首页会按计划提醒。", color = DetailTokens.SecondaryInk, fontSize = 11.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = onAddTask, shape = DetailTokens.ButtonShape, contentPadding = PaddingValues(horizontal = 14.dp, vertical = 7.dp)) { Text("添加第一条任务", fontSize = 11.sp) }
                            TextButton(onClick = onRecordWater) { Text("记录换水", fontSize = 11.sp) }
                        }
                    }
                }
            } else {
                items(upcoming.take(5).size, key = { upcoming[it].id }) { index ->
                    CareTaskCard(upcoming[index], onComplete = { task ->
                        careVm.finish(task, "", null, { scope.launch { snackbar.showSnackbar("已完成：${careTaskTypeLabel(task.type)}") } }, { error = it })
                    }, onPostpone = { postponing = it })
                }
            }
            item { DetailSectionHeader("最近完成", actionLabel = "事实记录", mutedAction = true) }
            item {
                if (done.isEmpty()) EmptyState("还没有完成记录", "完成养护任务或记录水质、换水后，会显示在这里。")
                else Column(Modifier.fillMaxWidth().clip(DetailTokens.SectionShape).background(Color.White).border(1.dp, DetailTokens.GlassBorder, DetailTokens.SectionShape)) {
                    done.forEachIndexed { index, item ->
                        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(30.dp).clip(RoundedCornerShape(9.dp)).background(Color(0xFFE4F8F4)), contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Check, contentDescription = null, tint = Color(0xFF0E8E80), modifier = Modifier.size(15.dp)) }
                            Column(Modifier.weight(1f).padding(start = 9.dp)) {
                                Text(item.title, color = DetailTokens.TitleInk, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                Text(item.subtitle, color = DetailTokens.TertiaryInk, fontSize = 9.5.sp)
                            }
                            Text(relativeTimeLabel(item.at), color = DetailTokens.TertiaryInk, fontSize = 9.5.sp)
                        }
                        if (index != done.lastIndex) Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFFEFF5FA)))
                    }
                }
            }
            item { CareTipCard(changes.firstOrNull()?.completedAt) }
        }
    }
    postponing?.let { task ->
        AlertDialog(
            onDismissRequest = { postponing = null },
            title = { Text("延期养护任务") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("${careTaskTypeLabel(task.type)} · 原计划 ${formatDateTime(task.plannedAt)}", color = DetailTokens.SecondaryInk, fontSize = 11.sp)
                    listOf(1L to "明天同一时间", 2L to "后天同一时间").forEach { (days, label) ->
                        TextButton(onClick = {
                            val target = maxOf(task.plannedAt, System.currentTimeMillis()) + days * 86_400_000L
                            careVm.postpone(task.id, target, { postponing = null; scope.launch { snackbar.showSnackbar("已延期到 ${formatDateTime(target)}") } }, { error = it })
                        }, modifier = Modifier.fillMaxWidth()) { Text(label, modifier = Modifier.fillMaxWidth()) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { postponing = null }) { Text("取消") } },
        )
    }
    error?.let { message -> AlertDialog(onDismissRequest = { error = null }, title = { Text("操作失败") }, text = { Text(message) }, confirmButton = { TextButton(onClick = { error = null }) { Text("知道了") } }) }
}

@Composable
private fun CareWeekCard(tasks: List<CareTask>) {
    val today = remember { Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) } }
    val monday = remember {
        Calendar.getInstance().apply { timeInMillis = today.timeInMillis; while (get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY) add(Calendar.DAY_OF_MONTH, -1) }
    }
    val week = (0..6).map { offset -> Calendar.getInstance().apply { timeInMillis = monday.timeInMillis; add(Calendar.DAY_OF_MONTH, offset) } }
    val plannedDays = tasks.filter { it.status == CareTaskStatus.pending || it.status == CareTaskStatus.overdue }.map { task -> week.indexOfFirst { sameDay(it, task.plannedAt) } }.toSet()
    val completedThisWeek = tasks.count { it.status == CareTaskStatus.completed && it.plannedAt >= week.first().timeInMillis && it.plannedAt < week.last().timeInMillis + 86_400_000L }
    val weeklyUpcoming = tasks.count { (it.status == CareTaskStatus.pending || it.status == CareTaskStatus.overdue) && it.plannedAt >= week.first().timeInMillis && it.plannedAt < week.last().timeInMillis + 86_400_000L }
    DeepWaterCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text("本周", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text("${formatShortDate(week.first().timeInMillis)} – ${formatShortDate(week.last().timeInMillis)}", color = Color(0xFF9BC3D7), fontSize = 10.sp, modifier = Modifier.padding(start = 7.dp))
                Spacer(Modifier.weight(1f))
                Text("$weeklyUpcoming 项待办 · $completedThisWeek 项完成", color = Color(0xFF8FEDDF), fontSize = 9.5.sp)
            }
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                week.forEachIndexed { index, day ->
                    val isToday = sameDay(day, System.currentTimeMillis())
                    Column(Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(if (isToday) Color(0x295FE0CD) else Color.Transparent).padding(vertical = 5.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(listOf("一", "二", "三", "四", "五", "六", "日")[index], color = if (isToday) Color(0xFF8FEDDF) else Color(0xFF9BC3D7), fontSize = 8.5.sp)
                        Text(day.get(Calendar.DAY_OF_MONTH).toString(), color = if (isToday) Color.White else Color(0xFFE5F4FB), fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 2.dp))
                        Box(Modifier.padding(top = 3.dp).size(4.dp).clip(CircleShape).background(if (index in plannedDays) Color(0xFF5FE0CD) else Color(0x38FFFFFF)))
                    }
                }
            }
        }
    }
}

@Composable
private fun CareTaskCard(task: CareTask, onComplete: (CareTask) -> Unit, onPostpone: (CareTask) -> Unit) {
    val overdue = task.status == CareTaskStatus.overdue || task.plannedAt < System.currentTimeMillis()
    Row(Modifier.fillMaxWidth().clip(DetailTokens.SectionShape).background(Color.White).border(1.dp, DetailTokens.GlassBorder, DetailTokens.SectionShape).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.width(42.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFFEAF3FA)).padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(SimpleDateFormat("dd", Locale.getDefault()).format(Date(task.plannedAt)), color = DetailTokens.TitleInk, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text(SimpleDateFormat("M月", Locale.getDefault()).format(Date(task.plannedAt)), color = DetailTokens.TertiaryInk, fontSize = 8.sp)
        }
        Column(Modifier.weight(1f).padding(horizontal = 9.dp)) {
            Text(careTaskTypeLabel(task.type), color = DetailTokens.TitleInk, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
            Text("${if (overdue) "已逾期 · " else ""}计划 ${SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(task.plannedAt))}${task.notes?.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""}", color = if (overdue) Color(0xFFB4492F) else DetailTokens.TertiaryInk, fontSize = 9.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Text("完成", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.clip(DetailTokens.PillShape).background(DetailTokens.Primary).clickable { onComplete(task) }.padding(horizontal = 9.dp, vertical = 6.dp))
            Text("延期", color = DetailTokens.SecondaryInk, fontSize = 10.sp, modifier = Modifier.clip(DetailTokens.PillShape).background(Color(0xFFF1F6FA)).border(1.dp, Color(0xFFDDE8F1), DetailTokens.PillShape).clickable { onPostpone(task) }.padding(horizontal = 9.dp, vertical = 6.dp))
        }
    }
}

@Composable
private fun CareTipCard(lastChangeAt: Long?) {
    Row(Modifier.fillMaxWidth().clip(DetailTokens.SectionShape).background(Color.White).border(1.dp, DetailTokens.GlassBorder, DetailTokens.SectionShape).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        Box(Modifier.size(30.dp).clip(RoundedCornerShape(9.dp)).background(Color(0xFFE6F7FA)), contentAlignment = Alignment.Center) { Icon(FishIcons.Droplet, contentDescription = null, tint = Color(0xFF17809A), modifier = Modifier.size(15.dp)) }
        Column {
            Text(if (lastChangeAt == null) "还没有换水记录" else "距上次换水 ${daysBetween(lastChangeAt)}", color = DetailTokens.TitleInk, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Text("是否换水由你决定；这里只展示真实记录，不自动生成结论。", color = DetailTokens.TertiaryInk, fontSize = 9.5.sp, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

private enum class TankActivityFilter(val label: String) { All("全部"), Water("水质"), Feeding("喂食"), Change("换水"), Growth("成长") }

private data class TankActivityItem(val at: Long, val filter: TankActivityFilter, val title: String, val subtitle: String, val chips: List<String> = emptyList())

@Composable
private fun TankActivityPage(tankId: String, onBack: () -> Unit, onOpenFish: (String) -> Unit, vm: TankViewModel) {
    val summaryState by remember(tankId) { vm.summary(tankId) }.collectAsStateWithLifecycle()
    val tank = (summaryState as? TankViewModel.SummaryState.Found)?.summary?.tank
    val tests by remember(tankId) { vm.tests(tankId) }.collectAsStateWithLifecycle(initialValue = emptyList())
    val changes by remember(tankId) { vm.changes(tankId) }.collectAsStateWithLifecycle(initialValue = emptyList())
    val feedings by remember(tankId) { vm.feedings(tankId) }.collectAsStateWithLifecycle(initialValue = emptyList())
    val growth by remember(tankId) { vm.growth(tankId) }.collectAsStateWithLifecycle(initialValue = emptyList())
    val fish by remember(tankId) { vm.fish(tankId) }.collectAsStateWithLifecycle(initialValue = emptyList())
    var filter by remember { mutableStateOf(TankActivityFilter.All) }
    val fishNames = fish.associate { it.id to it.name }
    val items = remember(tests, changes, feedings, growth, fishNames) {
        buildList {
            tests.forEach { add(TankActivityItem(it.test.measuredAt, TankActivityFilter.Water, "水质检测", "${it.values.size} 项实测值", it.values.take(4).map { v -> "${parameterLabel(v.parameterCode)} ${v.value?.prettyNumber() ?: "—"}${v.unit.orEmpty()}" })) }
            feedings.forEach { add(TankActivityItem(it.event.fedAt, TankActivityFilter.Feeding, "喂食 · ${it.event.foodType ?: "饲料未记录"}", "全缸 ${it.targets.count { target -> target.targetType == FeedingTargetType.tank }.coerceAtLeast(1)} 次")) }
            changes.forEach { add(TankActivityItem(it.completedAt, TankActivityFilter.Change, "换水${it.volumeValue?.let { v -> " · ${v.prettyNumber()} ${it.volumeUnit.orEmpty()}" } ?: ""}", it.ratio?.let { r -> "换水比例 ${r.prettyNumber()}%" } ?: "换水记录")) }
            growth.forEach { add(TankActivityItem(it.measuredAt, TankActivityFilter.Growth, "成长 · ${fishNames[it.fishId] ?: "鱼只"}", listOfNotNull(it.lengthValue?.let { v -> "体长 ${v.prettyNumber()} ${it.lengthUnit.orEmpty()}" }, it.weightValue?.let { v -> "体重 ${v.prettyNumber()} ${it.weightUnit.orEmpty()}" }).joinToString(" · "))) }
        }.sortedByDescending { it.at }
    }
    val visible = if (filter == TankActivityFilter.All) items else items.filter { it.filter == filter }
    val grouped = visible.groupBy { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(it.at)) }
    val counts = TankActivityFilter.entries.associateWith { f -> if (f == TankActivityFilter.All) items.size else items.count { it.filter == f } }
    if (tank == null) {
        if (summaryState is TankViewModel.SummaryState.Loading) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingState("正在加载缸内动态") }
        else TankPageMessage("找不到这个鱼缸", "鱼缸可能已被删除或链接已失效。", onBack, "tank-not-found")
        return
    }
    Scaffold(
        containerColor = DetailTokens.PageBackground,
        topBar = { SubpageTopBar("缸内动态", "${tank.name} · ${tankTypeLabel(tank.type)}", onBack, titleModifier = Modifier.testTag("tank-records-title")) },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 30.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                DeepWaterCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text("记录概览", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            Text("按真实记录统计", color = Color(0xFF9BC3D7), fontSize = 10.sp, modifier = Modifier.padding(start = 7.dp))
                            Spacer(Modifier.weight(1f))
                            Text("共 ${items.size} 条", color = Color(0xFF8FEDDF), fontSize = 10.sp)
                        }
                        Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            DeepMetric("水质检测", counts[TankActivityFilter.Water].toString(), "次", Modifier.weight(1f))
                            DeepMetric("喂食", counts[TankActivityFilter.Feeding].toString(), "次", Modifier.weight(1f))
                            DeepMetric("换水", counts[TankActivityFilter.Change].toString(), "次", Modifier.weight(1f))
                            DeepMetric("成长", counts[TankActivityFilter.Growth].toString(), "条", Modifier.weight(1f))
                        }
                    }
                }
            }
            item {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TankActivityFilter.entries.forEach { f -> FilterPill("${f.label} ${counts[f] ?: 0}", filter == f) { filter = f } }
                }
            }
            if (items.isEmpty()) item { EmptyState("还没有动态", "记录水质、喂食、换水或成长后，这里会按日期汇总。") }
            grouped.forEach { (day, list) ->
                item { Text(dayLabel(day), color = DetailTokens.SecondaryInk, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp)) }
                items(list.size, key = { index -> "${list[index].at}-${list[index].filter}-${list[index].title}" }) { index -> TankActivityRow(list[index], onOpenFish) }
            }
            item { Text("只展示真实记录，时间线不合并计划任务。", color = DetailTokens.TertiaryInk, fontSize = 9.5.sp, modifier = Modifier.padding(top = 6.dp)) }
        }
    }
}

@Composable
private fun TankActivityRow(item: TankActivityItem, onOpenFish: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Box(Modifier.width(34.dp).fillMaxHeight().drawBehind {
            drawLine(Color(0xFFDCE9F3), Offset(size.width / 2f, 0f), Offset(size.width / 2f, size.height), 1.dp.toPx())
        }, contentAlignment = Alignment.TopCenter) {
            Box(Modifier.padding(top = 2.dp).size(30.dp).clip(RoundedCornerShape(9.dp)).background(
                when (item.filter) { TankActivityFilter.Water -> Color(0xFFE6F7FA); TankActivityFilter.Feeding -> Color(0xFFE4F8F4); TankActivityFilter.Change -> Color(0xFFFCF3E2); else -> Color(0xFFE8F1FC) }
            ).border(1.dp, Color(0xFFDCE9F3), RoundedCornerShape(9.dp)), contentAlignment = Alignment.Center) {
                Icon(
                    when (item.filter) { TankActivityFilter.Water -> FishIcons.Droplet; TankActivityFilter.Feeding -> Icons.Outlined.Favorite; TankActivityFilter.Change -> FishIcons.WaterLevel; else -> FishIcons.Ruler },
                    contentDescription = null,
                    tint = when (item.filter) { TankActivityFilter.Water -> Color(0xFF17809A); TankActivityFilter.Feeding -> Color(0xFF0E8E80); TankActivityFilter.Change -> Color(0xFFB77E2B); else -> DetailTokens.Primary },
                    modifier = Modifier.size(15.dp),
                )
            }
        }
        Column(Modifier.weight(1f).padding(start = 8.dp, bottom = 8.dp).clip(DetailTokens.SectionShape).background(Color.White).border(1.dp, DetailTokens.GlassBorder, DetailTokens.SectionShape).padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(item.title, color = DetailTokens.TitleInk, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(item.at)), color = DetailTokens.TertiaryInk, fontSize = 9.5.sp)
            }
            if (item.subtitle.isNotBlank()) Text(item.subtitle, color = DetailTokens.TertiaryInk, fontSize = 9.5.sp, modifier = Modifier.padding(top = 2.dp))
            if (item.chips.isNotEmpty()) Row(Modifier.padding(top = 6.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                item.chips.forEach { chip -> Text(chip, color = DetailTokens.SecondaryInk, fontSize = 9.sp, modifier = Modifier.clip(RoundedCornerShape(7.dp)).background(Color(0xFFF1F7FB)).padding(horizontal = 7.dp, vertical = 4.dp)) }
            }
        }
    }
}

@Composable
private fun LegacyTankWaterRecordsPage(tankId: String, onBack: () -> Unit, onEdit: () -> Unit, onRecord: () -> Unit, vm: TankViewModel) {
    val summaryState by remember(tankId) { vm.summary(tankId) }.collectAsStateWithLifecycle()
    val summary = (summaryState as? TankViewModel.SummaryState.Found)?.summary
    val tests by remember(tankId) { vm.tests(tankId) }.collectAsStateWithLifecycle(initialValue = emptyList())
    val changes by remember(tankId) { vm.changes(tankId) }.collectAsStateWithLifecycle(initialValue = emptyList())
    val tank = summary?.tank
    if (tank == null) {
        if (summaryState is TankViewModel.SummaryState.Loading) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingState("正在加载水质记录") }
        else TankPageMessage("找不到这个鱼缸", "鱼缸可能已被删除或链接已失效。", onBack, "tank-not-found")
        return
    }
    Scaffold(
        containerColor = DetailTokens.PageBackground,
        topBar = { SubpageTopBar("水质与趋势", "${tank.name} · ${tankTypeLabel(tank.type)}", onBack, actionIcon = Icons.Outlined.Edit, actionDescription = "编辑鱼缸", onAction = onEdit, titleModifier = Modifier.testTag("tank-records-title")) },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 30.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { TankRecordsSummary(summary!!, tests.size, changes.size) }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    DetailSectionHeader("水质与趋势", countLabel = "${tests.size} 次检测")
                    WaterQualityPanel(tank.type, tests, onRecord = onRecord)
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text("换水历史 · ${changes.size} 次", style = MaterialTheme.typography.titleMedium, color = DetailTokens.TitleInk)
                    if (changes.isEmpty()) Text("暂无换水记录。保存实际完成的换水后，记录会显示在这里。", color = DetailTokens.SecondaryInk, fontSize = 12.sp)
                    else changes.forEach { change ->
                        Row(Modifier.fillMaxWidth().clip(DetailTokens.SectionShape).background(Color.White).border(1.dp, DetailTokens.GlassBorder, DetailTokens.SectionShape).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("换水 ${change.volumeValue?.let { "${it.prettyNumber()} ${change.volumeUnit.orEmpty()}" } ?: "体积未记录"}", color = DetailTokens.TitleInk, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                Text(change.ratio?.let { "比例 ${it.prettyNumber()}%" } ?: "比例未记录", color = DetailTokens.TertiaryInk, fontSize = 9.5.sp)
                            }
                            Text(formatShortDate(change.completedAt), color = DetailTokens.TertiaryInk, fontSize = 9.5.sp)
                        }
                    }
                }
            }
        }
    }
}

private fun careTaskTypeLabel(type: CareTaskType): String = when (type) {
    CareTaskType.feeding -> "喂食"; CareTaskType.water_change -> "换水"; CareTaskType.water_test -> "水质检测"
    CareTaskType.salinity_test -> "盐度检测"; CareTaskType.maintenance -> "设备维护"; CareTaskType.custom -> "自定义养护"
}

private fun careTaskTypeLabel(type: CareRecordType): String = when (type) {
    CareRecordType.feeding -> "喂食"; CareRecordType.water_change -> "换水"; CareRecordType.water_test -> "水质检测"
    CareRecordType.salinity_test -> "盐度检测"; CareRecordType.maintenance -> "设备维护"; CareRecordType.custom -> "自定义养护"
}

private fun daysBetween(value: Long?): String {
    if (value == null) return "—"
    return "${((System.currentTimeMillis() - value).coerceAtLeast(0L) / 86_400_000L).toInt()} 天"
}

private fun sameDay(a: Calendar, b: Long): Boolean {
    val other = Calendar.getInstance().apply { timeInMillis = b }
    return a.get(Calendar.YEAR) == other.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == other.get(Calendar.DAY_OF_YEAR)
}

private fun dayLabel(day: String): String {
    val parser = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val today = parser.format(Date())
    val yesterday = parser.format(Date(System.currentTimeMillis() - 86_400_000L))
    val date = parser.parse(day) ?: return day
    return when (day) {
        today -> "今天 · ${SimpleDateFormat("M月d日", Locale.getDefault()).format(date)}"
        yesterday -> "昨天"
        else -> SimpleDateFormat("M 月 d 日", Locale.getDefault()).format(date)
    }
}

@Composable
private fun WaterRecordScreen(
    tankId: String,
    onBack: () -> Unit,
    onOpenHistory: () -> Unit,
    vm: TankViewModel,
    templateTestId: String? = null,
) {
    val summaryState by remember(tankId) { vm.summary(tankId) }.collectAsStateWithLifecycle()
    val tank = (summaryState as? TankViewModel.SummaryState.Found)?.summary?.tank
    val tests by remember(tankId) { vm.tests(tankId) }.collectAsStateWithLifecycle(initialValue = emptyList())
    val latestTest = tests.firstOrNull()
    val parameters = remember(tank?.type) { tank?.type?.let(::waterParameters).orEmpty() }
    var selected by remember(tankId) { mutableStateOf<Set<String>>(emptySet()) }
    var values by remember(tankId) { mutableStateOf<Map<String, String>>(emptyMap()) }
    var units by remember(tankId) { mutableStateOf<Map<String, String>>(emptyMap()) }
    var methods by remember(tankId) { mutableStateOf<Map<String, String>>(emptyMap()) }
    var targetMins by remember(tankId) { mutableStateOf<Map<String, String>>(emptyMap()) }
    var targetMaxs by remember(tankId) { mutableStateOf<Map<String, String>>(emptyMap()) }
    var parameterNotes by remember(tankId) { mutableStateOf<Map<String, String>>(emptyMap()) }
    var notes by remember { mutableStateOf("") }
    var notesOpen by remember { mutableStateOf(false) }
    val initialDate = remember(tankId) { System.currentTimeMillis() }
    var date by remember(tankId) { mutableLongStateOf(initialDate) }
    var initialized by remember(tankId) { mutableStateOf(false) }
    var advancedCode by remember { mutableStateOf<String?>(null) }
    var unitMenuCode by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var warning by remember { mutableStateOf<String?>(null) }
    var pendingWaterTest by remember { mutableStateOf<Pair<WaterTest, List<WaterTestValue>>?>(null) }
    var pendingSuspected by remember { mutableStateOf<Pair<WaterTest, List<WaterTestValue>>?>(null) }
    var saving by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    LaunchedEffect(tank?.id, latestTest?.test?.id, templateTestId, tests.size) {
        if (tank == null || initialized) return@LaunchedEffect
        if (templateTestId != null && tests.isEmpty()) return@LaunchedEffect
        val template = templateTestId?.let { id -> tests.firstOrNull { it.test.id == id } }
        val source = template ?: latestTest
        values = parameters.associate { parameter ->
            parameter.code to (source?.values?.firstOrNull { it.parameterCode == parameter.code }?.value?.prettyNumber() ?: "")
        }
        units = parameters.associate { parameter ->
            parameter.code to (source?.values?.firstOrNull { it.parameterCode == parameter.code }?.unit ?: parameter.unit)
        }
        methods = parameters.associate { parameter ->
            parameter.code to (source?.values?.firstOrNull { it.parameterCode == parameter.code }?.method.orEmpty())
        }
        targetMins = parameters.associate { parameter ->
            parameter.code to (source?.values?.firstOrNull { it.parameterCode == parameter.code }?.targetMin?.prettyNumber().orEmpty())
        }
        targetMaxs = parameters.associate { parameter ->
            parameter.code to (source?.values?.firstOrNull { it.parameterCode == parameter.code }?.targetMax?.prettyNumber().orEmpty())
        }
        parameterNotes = parameters.associate { it.code to "" }
        selected = source?.values?.map { it.parameterCode }?.filter { code -> parameters.any { it.code == code } }?.toSet()
            ?.ifEmpty { defaultWaterCodes(tank.type, parameters) }
            ?: defaultWaterCodes(tank.type, parameters)
        initialized = true
    }
    if (tank == null) {
        if (summaryState is TankViewModel.SummaryState.Loading) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingState("正在加载鱼缸…") }
        else Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("找不到这个鱼缸", modifier = Modifier.testTag("tank-action-not-found"), style = MaterialTheme.typography.titleLarge)
            Text("鱼缸可能已被删除或链接已失效。", color = DetailTokens.SecondaryInk)
            TextButton(onClick = onBack) { Text("返回") }
        }
        return
    }
    val hasUnsavedChanges = date != initialDate || values.any { it.value.isNotBlank() } || notes.isNotBlank() || methods.any { it.value.isNotBlank() } || targetMins.any { it.value.isNotBlank() } || targetMaxs.any { it.value.isNotBlank() } || parameterNotes.any { it.value.isNotBlank() }
    val leaveForm = rememberLeaveFormHandler(initialized && hasUnsavedChanges, onBack)
    val selectedParameters = parameters.filter { it.code in selected }
    val entered = selectedParameters.filter { values[it.code].orEmpty().isNotBlank() }
    val lastValueByCode = latestTest?.values?.associateBy { it.parameterCode }.orEmpty()
    fun normalizeRecords(): Pair<WaterTest, List<WaterTestValue>> {
        val test = WaterTest(tankId = tankId, measuredAt = date, method = null, notes = notes.ifBlank { null })
        val records = entered.map { parameter ->
            WaterTestValue(
                waterTestId = test.id,
                parameterCode = parameter.code,
                value = values[parameter.code]?.toDoubleOrNull(),
                unit = units[parameter.code] ?: parameter.unit,
                targetMin = targetMins[parameter.code]?.takeIf(String::isNotBlank)?.toDoubleOrNull(),
                targetMax = targetMaxs[parameter.code]?.takeIf(String::isNotBlank)?.toDoubleOrNull(),
                measuredAt = date,
                method = methods[parameter.code]?.ifBlank { null },
                notes = parameterNotes[parameter.code]?.ifBlank { null },
                targetSource = if (targetMins[parameter.code]?.isNotBlank() == true || targetMaxs[parameter.code]?.isNotBlank() == true) "user" else null,
            )
        }
        return test to records
    }
    fun saveNow(test: WaterTest, records: List<WaterTestValue>) {
        saving = true
        vm.addWaterTest(test, records, { saving = false; onBack() }, { saving = false; error = it })
    }
    fun persist(test: WaterTest, records: List<WaterTestValue>) {
        val outside = records.filter { isOutsideWaterTarget(it.value!!, it.targetMin, it.targetMax) }
        if (outside.isNotEmpty()) {
            warning = "${outside.joinToString { parameterLabel(it.parameterCode) }} 超出你设置的目标范围；请核对测量并考虑复测。不会自动调整鱼缸。"
            pendingWaterTest = test to records
            return
        }
        saveNow(test, records)
    }
    fun attemptSave(allowSuspected: Boolean) {
        if (saving) return
        if (entered.isEmpty()) { error = "至少填写一项实测水质参数"; return }
        if (entered.any { !isValidWaterMeasurement(values[it.code].orEmpty(), targetMins[it.code].orEmpty(), targetMaxs[it.code].orEmpty()) }) {
            error = "检测值和目标范围请输入有效数字，并确认下限不高于上限"
            return
        }
        val (test, records) = normalizeRecords()
        val suspected = records.any { it.parameterCode == "salinity" && looksLikeSpecificGravity(it.value, it.unit) }
        if (suspected && !allowSuspected) { pendingSuspected = test to records; return }
        persist(test, records)
    }
    Scaffold(
        containerColor = DetailTokens.PageBackground,
        topBar = {
            SubpageTopBar(
                "记录水质",
                "${tank.name} · ${tankTypeLabel(tank.type)}",
                leaveForm,
                actionIcon = FishIcons.Calendar,
                actionDescription = "水质历史",
                onAction = onOpenHistory,
                titleModifier = Modifier.testTag("water-record-title"),
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            Surface(color = Color(0xFAFFFFFF), shadowElevation = 12.dp) {
                Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text("已填 ${entered.size} 项", color = DetailTokens.TitleInk, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text(if (selectedParameters.isEmpty()) "先选择要测的参数" else "共 ${selectedParameters.size} 项 · 未测项保持为空", color = DetailTokens.TertiaryInk, fontSize = 9.5.sp)
                    }
                    Button(
                        onClick = { attemptSave(false) },
                        enabled = entered.isNotEmpty() && !saving,
                        shape = DetailTokens.PillShape,
                        modifier = Modifier.height(48.dp),
                        contentPadding = PaddingValues(horizontal = 22.dp),
                    ) { Text(if (saving) "保存中…" else "保存记录", fontSize = 13.sp, fontWeight = FontWeight.Bold) }
                }
            }
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
            item {
                DeepWaterCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("采样时间", color = Color(0xFF8FB8CE), fontSize = 10.sp)
                            Text(formatDateTime(date), color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp).clickable { openDateTimePicker(context, date) { date = it } })
                            Spacer(Modifier.weight(1f))
                            Text("现在", color = Color(0xFF8FEDDF), fontSize = 9.5.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.clip(DetailTokens.PillShape).background(Color(0x295FE0CD)).border(1.dp, Color(0x665FE0CD), DetailTokens.PillShape).clickable { date = System.currentTimeMillis() }.padding(horizontal = 9.dp, vertical = 5.dp))
                        }
                        Text(
                            latestTest?.let { "上次检测 ${relativeTimeLabel(it.test.measuredAt)} · ${it.values.size} 项实测值" } ?: "上次检测：还没有记录",
                            color = Color(0xFF9BC3D7),
                            fontSize = 9.5.sp,
                            modifier = Modifier.padding(top = 5.dp),
                        )
                    }
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.width(4.dp).height(16.dp).clip(RoundedCornerShape(2.dp)).background(Brush.verticalGradient(listOf(Color(0xFF5FE0CD), Color(0xFF1D6FD0)))))
                    Text("本次检测", color = DetailTokens.TitleInk, fontSize = 14.5.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 7.dp))
                    Text("已选 ${selectedParameters.size} 项", color = DetailTokens.TertiaryInk, fontSize = 9.5.sp, modifier = Modifier.padding(start = 8.dp))
                    Spacer(Modifier.weight(1f))
                    Text(if (selected.size == parameters.size) "取消全选" else "全选", color = DetailTokens.Primary, fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable { selected = if (selected.size == parameters.size) emptySet() else parameters.map { it.code }.toSet() })
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    parameters.chunked(4).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            row.forEach { parameter ->
                                FilterPill(parameter.label, parameter.code in selected) {
                                    selected = if (parameter.code in selected) selected - parameter.code else selected + parameter.code
                                }
                            }
                        }
                    }
                }
            }
            if (selectedParameters.isEmpty()) {
                item { EmptyState("至少选择一项要测的参数", "点上方参数加入本次检测；未测项目保持为空。") }
            } else {
                selectedParameters.chunked(2).forEach { pair ->
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            pair.forEach { parameter ->
                                WaterMetricCard(
                                    parameter = parameter,
                                    value = values[parameter.code].orEmpty(),
                                    unit = units[parameter.code] ?: parameter.unit,
                                    last = lastValueByCode[parameter.code],
                                    suspiciousSalinity = parameter.code == "salinity" && looksLikeSpecificGravity(values[parameter.code]?.toDoubleOrNull(), units[parameter.code]),
                                    modifier = Modifier.weight(1f),
                                    onValue = { new -> values = values + (parameter.code to new.take(10)) },
                                    onUnit = { unitMenuCode = parameter.code },
                                    onMore = { advancedCode = parameter.code },
                                    onFillLast = lastValueByCode[parameter.code]?.let { last ->
                                        {
                                            values = values + (parameter.code to (last.value?.prettyNumber() ?: ""))
                                            if (units[parameter.code] != last.unit) units = units + (parameter.code to (last.unit ?: parameter.unit))
                                            scope.launch { snackbar.showSnackbar("已填入上次值，请核对") }
                                        }
                                    },
                                    onConvertToGravity = if (parameter.code == "salinity" && parameters.any { it.code == "specific_gravity" }) {
                                        {
                                            val raw = values["salinity"].orEmpty()
                                            values = values + ("salinity" to "") + ("specific_gravity" to raw)
                                            units = units + ("specific_gravity" to "SG")
                                            selected = (selected - "salinity") + "specific_gravity"
                                            scope.launch { snackbar.showSnackbar("已改记比重，请核对单位") }
                                        }
                                    } else null,
                                )
                            }
                            if (pair.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFFEAF3FA)).padding(10.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    Icon(Icons.Outlined.MoreVert, contentDescription = null, tint = Color(0xFF17809A), modifier = Modifier.size(14.dp))
                    Text("只填实测值；未测项目保持为空，不用默认值代替。目标范围、测量方式和单项备注在每张卡的「⋯」里。", color = DetailTokens.SecondaryInk, fontSize = 9.5.sp, lineHeight = 14.sp)
                }
            }
            item {
                Column(Modifier.fillMaxWidth().clip(DetailTokens.SectionShape).background(Color.White).border(1.dp, DetailTokens.GlassBorder, DetailTokens.SectionShape).padding(12.dp)) {
                    Row(Modifier.fillMaxWidth().clickable { notesOpen = !notesOpen }, verticalAlignment = Alignment.CenterVertically) {
                        Text("检测备注（可选）", color = DetailTokens.TitleInk, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Text(if (notesOpen) "收起" else "填写", color = DetailTokens.Primary, fontSize = 10.sp)
                    }
                    if (notesOpen) OutlinedTextField(notes, { notes = it }, Modifier.fillMaxWidth().padding(top = 8.dp), placeholder = { Text("例如：换水后 30 分钟，鱼只状态正常") }, minLines = 2)
                }
            }
            item {
                val show = latestTest?.values?.take(2).orEmpty()
                if (show.isNotEmpty()) Row(Modifier.fillMaxWidth().clip(DetailTokens.SectionShape).background(Color.White).border(1.dp, DetailTokens.GlassBorder, DetailTokens.SectionShape).padding(11.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text("上次实测", color = DetailTokens.TertiaryInk, fontSize = 9.5.sp)
                    show.forEach { value -> Text("${parameterLabel(value.parameterCode)} ${value.value?.prettyNumber() ?: "—"}${value.unit.orEmpty()}", color = DetailTokens.SecondaryInk, fontSize = 9.sp, modifier = Modifier.clip(RoundedCornerShape(7.dp)).background(Color(0xFFF1F7FB)).padding(horizontal = 7.dp, vertical = 4.dp)) }
                    Spacer(Modifier.weight(1f))
                    Text(latestTest?.test?.measuredAt?.let { formatShortDate(it) }.orEmpty(), color = DetailTokens.TertiaryInk, fontSize = 8.5.sp)
                }
            }
        }
    }
    advancedCode?.let { code ->
        val parameter = parameters.firstOrNull { it.code == code }
        if (parameter != null) ModalBottomSheet(onDismissRequest = { advancedCode = null }) {
            Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 26.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("${parameter.label} · 更多设置", color = DetailTokens.TitleInk, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(values[code].orEmpty().ifBlank { "—" }, color = DetailTokens.TitleInk, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    Text(units[code] ?: parameter.unit, color = DetailTokens.TertiaryInk, fontSize = 10.sp, modifier = Modifier.padding(start = 3.dp, bottom = 4.dp))
                }
                OutlinedTextField(methods[code].orEmpty(), { methods = methods + (code to it) }, Modifier.fillMaxWidth(), label = { Text("测量方式（可选）") }, singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(targetMins[code].orEmpty(), { targetMins = targetMins + (code to it) }, Modifier.weight(1f), label = { Text("目标下限") }, singleLine = true)
                    OutlinedTextField(targetMaxs[code].orEmpty(), { targetMaxs = targetMaxs + (code to it) }, Modifier.weight(1f), label = { Text("目标上限") }, singleLine = true)
                }
                OutlinedTextField(parameterNotes[code].orEmpty(), { parameterNotes = parameterNotes + (code to it) }, Modifier.fillMaxWidth(), label = { Text("参数备注（可选）") }, minLines = 1)
                Text("目标范围由你设置，仅作记录参考；不会自动判断鱼缸状态。", color = DetailTokens.TertiaryInk, fontSize = 9.5.sp)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = {
                        values = values + (code to "")
                        methods = methods + (code to "")
                        targetMins = targetMins + (code to "")
                        targetMaxs = targetMaxs + (code to "")
                        parameterNotes = parameterNotes + (code to "")
                        advancedCode = null
                    }) { Text("清除此项", color = Color(0xFFB4492F)) }
                    Spacer(Modifier.weight(1f))
                    Button(onClick = { advancedCode = null }, shape = DetailTokens.PillShape) { Text("完成") }
                }
            }
        }
    }
    if (unitMenuCode != null) {
        val parameter = parameters.firstOrNull { it.code == unitMenuCode }
        if (parameter != null) AlertDialog(
            onDismissRequest = { unitMenuCode = null },
            title = { Text("${parameter.label}单位") },
            text = { Column { parameter.units.forEach { option -> Text(option, modifier = Modifier.fillMaxWidth().clickable { units = units + (parameter.code to option); unitMenuCode = null }.padding(vertical = 10.dp)) } } },
            confirmButton = { TextButton(onClick = { unitMenuCode = null }) { Text("取消") } },
        )
    }
    warning?.let { message ->
        AlertDialog(
            onDismissRequest = { warning = null; pendingWaterTest = null },
            title = { Text("请核对测量") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = { warning = null; pendingWaterTest?.let { (test, records) -> saveNow(test, records) }; pendingWaterTest = null }) { Text("已核对，仍然保存") } },
            dismissButton = { TextButton(onClick = { warning = null; pendingWaterTest = null }) { Text("返回检查") } },
        )
    }
    pendingSuspected?.let { (test, records) ->
        AlertDialog(
            onDismissRequest = { pendingSuspected = null },
            title = { Text("这个盐度值更像比重") },
            text = { Text("盐度通常约 30–35 ppt；1.026 更像比重（SG）。要改记比重，还是仍按盐度保存？") },
            confirmButton = {
                TextButton(onClick = {
                    val converted = records.map { if (it.parameterCode == "salinity") it.copy(parameterCode = "specific_gravity", unit = "SG", targetMin = null, targetMax = null, targetSource = null) else it }
                    pendingSuspected = null
                    persist(test, converted)
                }) { Text("改记比重") }
            },
            dismissButton = { TextButton(onClick = { pendingSuspected = null; persist(test, records) }) { Text("仍按盐度保存") } },
        )
    }
    error?.let { message ->
        AlertDialog(onDismissRequest = { error = null }, title = { Text("请检查") }, text = { Text(message) }, confirmButton = { TextButton(onClick = { error = null }) { Text("知道了") } })
    }
}

private fun defaultWaterCodes(type: TankType, parameters: List<WaterParameter>): Set<String> {
    val preferred = if (type == TankType.saltwater) listOf("temperature", "salinity", "ph") else listOf("temperature", "ph", "ammonia")
    return preferred.filter { code -> parameters.any { it.code == code } }.toSet()
}

private fun looksLikeSpecificGravity(value: Double?, unit: String?): Boolean =
    value != null && (unit == "ppt" || unit == "PSU") && value in 1.0..1.05

@Composable
private fun WaterMetricCard(
    parameter: WaterParameter,
    value: String,
    unit: String,
    last: WaterTestValue?,
    suspiciousSalinity: Boolean,
    modifier: Modifier = Modifier,
    onValue: (String) -> Unit,
    onUnit: () -> Unit,
    onMore: () -> Unit,
    onFillLast: (() -> Unit)?,
    onConvertToGravity: (() -> Unit)?,
) {
    Column(
        modifier.clip(DetailTokens.SectionShape).background(Color.White)
            .border(1.dp, if (value.isNotBlank()) Color(0xFFA9DCD5) else DetailTokens.GlassBorder, DetailTokens.SectionShape)
            .padding(11.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(parameter.label, color = DetailTokens.TitleInk, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Text(unit, color = DetailTokens.SecondaryInk, fontSize = 9.sp, modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(Color(0xFFF1F7FB)).clickable(onClick = onUnit).padding(horizontal = 6.dp, vertical = 4.dp))
            Text("⋯", color = DetailTokens.TertiaryInk, fontSize = 15.sp, modifier = Modifier.padding(start = 5.dp).clickable(onClick = onMore))
        }
        BasicTextField(
            value = value,
            onValueChange = { new -> if (new.length <= 10 && new.all { it.isDigit() || it == '.' }) onValue(new) },
            singleLine = true,
            textStyle = TextStyle(color = DetailTokens.TitleInk, fontSize = 25.sp, fontWeight = FontWeight.Bold),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            cursorBrush = androidx.compose.ui.graphics.SolidColor(DetailTokens.Primary),
            modifier = Modifier.fillMaxWidth().padding(top = 7.dp),
            decorationBox = { inner ->
                Box(Modifier.height(32.dp), contentAlignment = Alignment.CenterStart) {
                    if (value.isBlank()) Text("输入", color = Color(0xFFB7C9D6), fontSize = 16.sp)
                    inner()
                }
            },
        )
        if (suspiciousSalinity) {
            Column(Modifier.padding(top = 3.dp)) {
                Text("$value 更像比重（SG）", color = Color(0xFFB77E2B), fontSize = 8.5.sp)
                onConvertToGravity?.let { Text("改记比重 ›", color = Color(0xFFB77E2B), fontSize = 8.5.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable(onClick = it).padding(top = 1.dp)) }
            }
        } else {
            Text(
                last?.let { "上次 ${it.value?.prettyNumber() ?: "—"} ${it.unit.orEmpty()}" } ?: "上次未记录",
                color = DetailTokens.TertiaryInk,
                fontSize = 8.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 5.dp).then(if (onFillLast != null && value.isBlank()) Modifier.clickable(onClick = onFillLast) else Modifier),
            )
        }
    }
}

internal fun openDateTimePicker(context: android.content.Context, value: Long, onChange: (Long) -> Unit) {
    val calendar = Calendar.getInstance().apply { timeInMillis = value }
    DatePickerDialog(context, { _, year, month, day ->
        val selectedDate = Calendar.getInstance().apply { timeInMillis = value; set(year, month, day) }
        TimePickerDialog(context, { _, hour, minute ->
            selectedDate.set(Calendar.HOUR_OF_DAY, hour); selectedDate.set(Calendar.MINUTE, minute); selectedDate.set(Calendar.SECOND, 0); selectedDate.set(Calendar.MILLISECOND, 0)
            onChange(selectedDate.timeInMillis)
        }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), true).show()
    }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
}
