@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.guanyji.app.feature.fish

import android.app.Application
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.request.ImageRequest
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.ScrollMoveMode
import sh.calvin.reorderable.rememberReorderableLazyGridState
import com.guanyji.app.R
import com.guanyji.app.core.designsystem.*
import com.guanyji.app.data.local.*
import com.guanyji.app.data.repository.applySavedOrder
import com.guanyji.app.data.repository.mergeVisibleReorder
import com.guanyji.app.data.repository.movedItem
import com.guanyji.app.feature.overview.DashboardTokens
import com.guanyji.app.ui.theme.AquariumCardTokens
import com.guanyji.app.feature.overview.FishIcons
import com.guanyji.app.feature.overview.dashboardParameterLabel
import com.guanyji.app.feature.overview.prettyNumber
import com.guanyji.app.feature.overview.relativeTimeLabel
import com.guanyji.app.feature.photo.*
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.math.max
import kotlin.math.min
private enum class FishSort(val label: String) { added("最近添加"), tankDate("入缸日期"), measured("最近测量"), custom("自定义顺序") }
private val statuses = listOf(null to "全部", FishStatus.active to "在养", FishStatus.quarantine to "隔离", FishStatus.transferred to "转出", FishStatus.deceased to "已故", FishStatus.archived to "归档")

/**
 * 我的鱼列表：顶部为水族主题海报头部，下方为自适应双列鱼只卡牌墙。
 * 搜索、鱼缸筛选、状态筛选、排序与新增 / 编辑行为均保持原有实现，仅调整视觉与布局。
 */
@Composable
fun FishListScreen(onCreate: () -> Unit, onOpen: (String) -> Unit, onEdit: (String) -> Unit, vm: FishViewModel = viewModel(factory = FishViewModel.factory(LocalContext.current.applicationContext as Application))) {
    val data by vm.fish.collectAsStateWithLifecycle()
    val tankData by vm.tanks.collectAsStateWithLifecycle()
    val lengthUnit by vm.lengthUnit.collectAsStateWithLifecycle()
    val tanks = tankData.orEmpty()
    var search by rememberSaveable { mutableStateOf("") }
    var tankId by rememberSaveable { mutableStateOf<String?>(null) }
    var status by rememberSaveable { mutableStateOf<FishStatus?>(null) }
    // 鱼种筛选：选项来自本地已有记录的鱼种，不凭空造分类。
    var species by rememberSaveable { mutableStateOf<String?>(null) }
    var showFilterSheet by remember { mutableStateOf(false) }
    // 排序方式也落库：否则重启后回到「最近添加」，用户会以为拖拽白做了。
    val savedSortName by vm.listSort.collectAsStateWithLifecycle()
    var sortChoice by rememberSaveable { mutableStateOf<String?>(null) }
    val sort = remember(sortChoice, savedSortName) {
        FishSort.entries.firstOrNull { it.name == (sortChoice ?: savedSortName) } ?: FishSort.added
    }
    var sortMenu by remember { mutableStateOf(false) }
    // 自定义顺序：已落库的顺序 + 拖拽期间的本地覆盖。
    // 拖拽库要求 onMove 内同步改列表，而落库是异步的，所以本地覆盖要撑到设置流追平为止。
    val savedOrder by vm.listOrder.collectAsStateWithLifecycle()
    var dragOrder by remember { mutableStateOf<List<String>?>(null) }
    LaunchedEffect(savedOrder, dragOrder) {
        val pending = dragOrder
        if (pending != null && pending == savedOrder) dragOrder = null
    }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    // 只做一次入场动画的鱼只，避免滚动回收后重复播放。
    val entranceShown = remember { mutableSetOf<String>() }
    val tankNames = tanks.associate { it.id to it.name }
    val filtered = data.orEmpty().filter { row ->
        (status == null || row.fish.status == status) && (tankId == null || row.fish.tankId == tankId) &&
            (species == null || row.fish.species?.trim() == species) &&
            (row.fish.name.contains(search.trim(), true) || row.fish.species.orEmpty().contains(search.trim(), true))
    }
    // 鱼种选项只列出本地记录里真实出现过的鱼种。
    val speciesOptions = remember(data) {
        data.orEmpty().mapNotNull { it.fish.species?.trim()?.takeIf(String::isNotEmpty) }.distinct().sorted()
    }
    val filtersActive = tankId != null || status != null || species != null
    val appliedChips = buildList {
        tankId?.let { id -> tankNames[id]?.let { add(AppliedFilterChip(it) { tankId = null }) } }
        status?.let { value ->
            statuses.firstOrNull { it.first == value }?.second?.let { add(AppliedFilterChip(it) { status = null }) }
        }
        species?.let { value -> add(AppliedFilterChip(value) { species = null }) }
    }
    // 自定义顺序的兜底就是「最近添加」：没排过的鱼只（含新增）永远追加在末尾。
    val defaultOrder = remember(data) { data.orEmpty().sortedByDescending { it.fish.createdAt }.map { it.fish.id } }
    val fullOrder = remember(defaultOrder, dragOrder, savedOrder) {
        defaultOrder.applySavedOrder(dragOrder ?: savedOrder) { it }
    }
    val rows = when (sort) {
        FishSort.added -> filtered.sortedByDescending { it.fish.createdAt }
        FishSort.tankDate -> filtered.sortedByDescending { it.fish.addedAt }
        FishSort.measured -> filtered.sortedByDescending { it.lastMeasuredAt ?: Long.MIN_VALUE }
        FishSort.custom -> filtered.associateBy { it.fish.id }.let { byId -> fullOrder.mapNotNull(byId::get) }
    }
    val visibleIds = rows.map { it.fish.id }
    // 搜索结果是临时视图，"改了哪一条"说不清，因此搜索状态下不开放拖拽。
    val dragEnabled = search.isBlank()
    val gridState = rememberLazyGridState()

    fun selectSort(next: FishSort) {
        if (next == sort) return
        sortChoice = next.name
        vm.saveListSort(next.name)
    }

    /** 切到自定义顺序时，沿用用户当前看到的顺序，列表不会跳动。 */
    fun adoptVisibleOrder(): List<String> {
        val seeded = mergeVisibleReorder(fullOrder, visibleIds)
        dragOrder = seeded
        if (sort != FishSort.custom) selectSort(FishSort.custom)
        return seeded
    }

    fun moveDragged(fromKey: Any?, toKey: Any?) {
        val fromId = fromKey as? String ?: return
        val toId = toKey as? String ?: return
        val from = visibleIds.indexOf(fromId)
        val to = visibleIds.indexOf(toId)
        if (from < 0 || to < 0) return
        val base = if (sort == FishSort.custom) fullOrder else adoptVisibleOrder()
        dragOrder = mergeVisibleReorder(base, visibleIds.movedItem(from, to))
    }

    fun persistOrder(ids: List<String>) {
        vm.saveListOrder(ids) { message -> scope.launch { snackbar.showSnackbar(message) } }
    }

    /** 无障碍「上移 / 下移」：不依赖拖拽手势，TalkBack 用户也能改顺序。 */
    fun moveByOne(id: String, delta: Int): Boolean {
        val from = visibleIds.indexOf(id)
        if (from < 0) return false
        val to = (from + delta).coerceIn(0, visibleIds.lastIndex)
        if (to == from) return false
        val base = if (sort == FishSort.custom) fullOrder else adoptVisibleOrder()
        val merged = mergeVisibleReorder(base, visibleIds.movedItem(from, to))
        dragOrder = merged
        persistOrder(merged)
        return true
    }

    val reorderState = rememberReorderableLazyGridState(
        lazyGridState = gridState,
        scrollMoveMode = ScrollMoveMode.INSERT,
        onMove = { from, to -> moveDragged(from.key, to.key) },
    )
    val activeCount = data?.count { it.fish.status == FishStatus.active || it.fish.status == FishStatus.quarantine }
    // 筛选面板：把所有筛选维度一次性摆开，替代原先横滚的 chip 行。
    if (showFilterSheet) {
        ListFilterSheet(
            onDismiss = { showFilterSheet = false },
            onReset = { tankId = null; status = null; species = null },
            resultLabel = "查看 ${rows.size} 条结果",
            onApply = { showFilterSheet = false },
        ) {
            FilterSection(
                label = "鱼缸",
                options = buildList {
                    add(FilterOption(null, "全部"))
                    tanks.forEach { add(FilterOption(it.id, it.name)) }
                },
                selectedKey = tankId,
                onSelect = { tankId = it },
            )
            FilterSection(
                label = "状态",
                options = statuses.map { (value, label) -> FilterOption(value?.name, label) },
                selectedKey = status?.name,
                onSelect = { key -> status = key?.let { name -> FishStatus.entries.firstOrNull { it.name == name } } },
            )
            FilterSection(
                label = "鱼种",
                options = buildList {
                    if (speciesOptions.isNotEmpty()) add(FilterOption(null, "全部"))
                    speciesOptions.forEach { add(FilterOption(it, it)) }
                },
                selectedKey = species,
                onSelect = { species = it },
            )
        }
    }

    Scaffold(
        containerColor = DashboardTokens.PageBackground,
        snackbarHost = { SnackbarHost(snackbar) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).testTag("fish-list-screen")) {
            // 头部 + 工具条：两个列表页结构一致，首屏到第一张卡片固定 200dp。
            Box(Modifier.fillMaxWidth().height(ListToolbarTokens.HeaderHeight + ListToolbarTokens.ToolbarOverhang)) {
                AquariumListHeader(
                    title = "我的鱼",
                    subtitle = "记录每一条鱼的成长",
                    countLabel = activeCount?.let { "在养 $it 条" } ?: "读取中",
                    icon = FishIcons.Fish,
                    addContentDescription = "添加鱼",
                    onAdd = onCreate,
                    modifier = Modifier.align(Alignment.TopCenter),
                )
                ListSearchToolbar(
                    query = search,
                    onQueryChange = { search = it },
                    placeholder = "搜索鱼名或鱼种",
                    filterActive = filtersActive,
                    onFilterClick = { showFilterSheet = true },
                    modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 16.dp),
                )
            }
            ListResultRow(
                countText = if (data == null) "正在读取本地记录" else "共 ${rows.size} 条",
                modifier = Modifier.padding(top = 10.dp),
            ) {
                Box {
                    Row(
                        Modifier
                            .clip(FishCardTokens.ChipShape)
                            .clickable(onClickLabel = "更改排序方式", role = Role.Button) { sortMenu = true }
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = sort.label,
                            color = DashboardTokens.DeepBlue,
                            fontSize = 12.5.sp,
                            lineHeight = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                        )
                        Icon(
                            imageVector = ChevronDownIcon,
                            contentDescription = null,
                            tint = DashboardTokens.DeepBlue,
                            modifier = Modifier.size(12.dp),
                        )
                    }
                    DropdownMenu(sortMenu, { sortMenu = false }) {
                        FishSort.entries.forEach { item ->
                            DropdownMenuItem(text = { Text(item.label) }, onClick = { selectSort(item); sortMenu = false })
                        }
                    }
                }
            }
            if (appliedChips.isNotEmpty()) {
                AppliedFilterRow(
                    chips = appliedChips,
                    onClearAll = { tankId = null; status = null; species = null },
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 148.dp),
                modifier = Modifier.fillMaxWidth().weight(1f),
                state = gridState,
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(11.dp),
                verticalArrangement = Arrangement.spacedBy(11.dp),
            ) {
                if (data == null) {
                    item(key = "loading", span = { GridItemSpan(maxLineSpan) }) {
                        Box(Modifier.fillMaxWidth().padding(vertical = 56.dp), contentAlignment = Alignment.Center) { LoadingState() }
                    }
                } else if (rows.isEmpty()) {
                    item(key = "empty", span = { GridItemSpan(maxLineSpan) }) {
                        Column(Modifier.fillMaxWidth().padding(top = 40.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            val hasCondition = search.isNotBlank() || filtersActive
                            EmptyState(if (hasCondition) "没有匹配的鱼只" else "还没有鱼只档案", "添加鱼只后，照片、成长和喂食记录会在这里汇总。")
                            if (hasCondition) {
                                TextButton(onClick = { search = ""; tankId = null; status = null; species = null }) { Text("清除筛选") }
                            }
                            Button(onClick = onCreate) { Text("添加鱼") }
                        }
                    }
                } else {
                    itemsIndexed(rows, key = { _, row -> row.fish.id }) { index, row ->
                        ReorderableItem(reorderState, key = row.fish.id) { isDragging ->
                            val photo by remember(row.fish.coverPhotoId) { vm.photo(row.fish.coverPhotoId) }.collectAsStateWithLifecycle()
                            val model = remember(row, tanks, lengthUnit) { fishCardModel(row, tanks, lengthUnit) }
                            FishTradingCard(
                                model = model,
                                photo = photo,
                                onClick = { onOpen(row.fish.id) },
                                onEdit = { onEdit(row.fish.id) },
                                animateEntrance = index < 8 && !entranceShown.contains(row.fish.id),
                                onEntranceShown = { shown -> entranceShown.add(shown) },
                                dragging = isDragging,
                                dragHandleModifier = if (dragEnabled) {
                                    Modifier.longPressDraggableHandle(
                                        onDragStarted = {
                                            val switched = sort != FishSort.custom
                                            adoptVisibleOrder()
                                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                            if (switched) {
                                                scope.launch { snackbar.showSnackbar("已切换到「自定义顺序」，松手即保存") }
                                            }
                                        },
                                        onDragStopped = {
                                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                            dragOrder?.let(::persistOrder)
                                        },
                                    )
                                } else {
                                    Modifier
                                },
                                modifier = Modifier.semantics {
                                    if (dragEnabled && rows.size > 1) {
                                        customActions = listOf(
                                            CustomAccessibilityAction("上移") { moveByOne(row.fish.id, -1) },
                                            CustomAccessibilityAction("下移") { moveByOne(row.fish.id, 1) },
                                        )
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FishFormScreen(fishId: String?, onBack: () -> Unit, onSaved: (String) -> Unit, initialTankId: String? = null, vm: FishViewModel = viewModel(factory = FishViewModel.factory(LocalContext.current.applicationContext as Application))) {
    val allFish by vm.fish.collectAsStateWithLifecycle()
    val tanks by vm.tanks.collectAsStateWithLifecycle()
    val availableTanks = tanks.orEmpty()
    val existing = allFish?.firstOrNull { it.fish.id == fishId }?.fish
    val photoVm: PhotoViewModel = viewModel(key = "fish-photo-$fishId", factory = PhotoViewModelFactory(vm.photoRepository))
    var initialized by remember(fishId) { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }; var species by remember { mutableStateOf("") }; var tankId by remember { mutableStateOf("") }
    var addedAt by remember { mutableLongStateOf(todayStart()) }; var birthDate by remember { mutableStateOf<Long?>(null) }
    var length by remember { mutableStateOf("") }; var weight by remember { mutableStateOf("") }; var sex by remember { mutableStateOf("") }
    var source by remember { mutableStateOf("") }; var notes by remember { mutableStateOf("") }; var busy by remember { mutableStateOf(false) }; var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(existing?.id, tanks, initialTankId) {
        if (fishId != null && existing != null && !initialized) {
            name = existing.name; species = existing.species.orEmpty(); tankId = existing.tankId; addedAt = existing.addedAt; birthDate = existing.birthDate
            sex = existing.sex.orEmpty(); source = existing.source.orEmpty(); notes = existing.notes.orEmpty()
            photoVm.load(existing.coverPhotoId); initialized = true
        } else if (fishId == null && tanks != null && !initialized) {
            val requestedTank = availableTanks.firstOrNull { it.id == initialTankId && it.status == TankStatus.active }
            tankId = requestedTank?.id ?: availableTanks.firstOrNull { it.status == TankStatus.active }?.id.orEmpty()
            initialized = true
        } else if (fishId != null && allFish != null && existing == null) {
            initialized = true
        }
    }
    val photoState by photoVm.state.collectAsStateWithLifecycle()
    val tank = availableTanks.firstOrNull { it.id == tankId }
    val hasUnsavedChanges = photoState.isBusy || if (fishId == null) {
        name.isNotBlank() || species.isNotBlank() || length.isNotBlank() || weight.isNotBlank() || sex.isNotBlank() || source.isNotBlank() || notes.isNotBlank() || photoState.photo != null
    } else initialized && existing != null && (
        name != existing.name || species != existing.species.orEmpty() || tankId != existing.tankId || addedAt != existing.addedAt || birthDate != existing.birthDate ||
            sex != existing.sex.orEmpty() || source != existing.source.orEmpty() || notes != existing.notes.orEmpty() || photoState.photo?.id != existing.coverPhotoId
        )
    val leaveForm = rememberLeaveFormHandler(hasUnsavedChanges, onBack)
    Scaffold(
        topBar = { FishTopBar(if (fishId == null) "添加鱼" else "编辑鱼", leaveForm) },
        bottomBar = {
            Surface(shadowElevation = 4.dp) {
                Button(enabled = !busy && !photoState.isBusy, onClick = {
        if (busy) return@Button
        val cleanName = name.trim(); val lengthValue = length.toDoubleOrNull(); val weightValue = weight.toDoubleOrNull()
        if (cleanName.isBlank()) { error = "鱼名为必填项"; return@Button }
        if (tank?.status != TankStatus.active) { error = "请选择一个在用鱼缸"; return@Button }
        if (todayStartOf(addedAt) > todayStart()) { error = "入缸日期不能晚于今天"; return@Button }
        if (birthDate != null && todayStartOf(birthDate!!) > todayStartOf(addedAt)) { error = "出生日期不能晚于入缸日期"; return@Button }
        if (length.isNotBlank() && (lengthValue == null || !lengthValue.isFinite() || lengthValue <= 0)) { error = "初始体长请输入大于 0 的有效数字"; return@Button }
        if (weight.isNotBlank() && (weightValue == null || !weightValue.isFinite() || weightValue <= 0)) { error = "初始体重请输入大于 0 的有效数字"; return@Button }
        val now = System.currentTimeMillis(); val id = existing?.id ?: UUID.randomUUID().toString()
        val value = Fish(id = id, tankId = tankId, name = cleanName, species = species.trim().ifBlank { null }, coverPhotoId = photoState.photo?.id ?: existing?.coverPhotoId, addedAt = addedAt, birthDate = birthDate, status = existing?.status ?: FishStatus.active, statusChangedAt = existing?.statusChangedAt, notes = notes.trim().ifBlank { null }, sex = sex.ifBlank { null }, source = source.trim().ifBlank { null }, deathDateKnown = existing?.deathDateKnown ?: true, deathDate = existing?.deathDate, createdAt = existing?.createdAt ?: now, updatedAt = now)
        busy = true; error = null
        vm.save(value, if (fishId == null) lengthValue else null, if (fishId == null) weightValue else null, { busy = false; onSaved(id) }, { busy = false; error = it })
                }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) { Text(if (busy) "保存中…" else "保存") }
            }
        },
    ) { padding ->
        if (fishId != null && allFish == null) Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { LoadingState("正在加载鱼只档案") }
        else if (fishId != null && existing == null) Column(Modifier.fillMaxSize().padding(padding).padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("找不到这条鱼", modifier = Modifier.testTag("fish-edit-not-found"), style = MaterialTheme.typography.titleLarge)
            TextButton(onClick = onBack) { Text("返回") }
        }
        else Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            PhotoPickerField(photoVm)
            OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("鱼名 *") }, singleLine = true)
            OutlinedTextField(species, { species = it }, Modifier.fillMaxWidth(), label = { Text("鱼种（可选）") }, singleLine = true)
            var tankMenu by remember { mutableStateOf(false) }
            Box { OutlinedButton(onClick = { tankMenu = true }, modifier = Modifier.fillMaxWidth()) { Text("所属鱼缸 *：${tank?.name ?: "选择鱼缸"} · ${tank?.type?.let { if (it == TankType.freshwater) "淡水" else "海水" }.orEmpty()}") }; DropdownMenu(tankMenu, { tankMenu = false }) { availableTanks.filter { it.status == TankStatus.active }.forEach { t -> DropdownMenuItem(text = { Text("${t.name} · ${if (t.type == TankType.freshwater) "淡水" else "海水"}") }, onClick = { tankId = t.id; tankMenu = false }) } } }
            DateField("入缸日期 *", addedAt) { it?.let { value -> addedAt = value } }
            DateField("出生日期（可选）", birthDate, true) { birthDate = it }
            OutlinedTextField(length, { length = it }, Modifier.fillMaxWidth(), label = { Text(if (fishId == null) "初始体长（cm，可选）" else "初始体长仅首次建档时填写") }, singleLine = true, enabled = fishId == null)
            OutlinedTextField(weight, { weight = it }, Modifier.fillMaxWidth(), label = { Text(if (fishId == null) "初始体重（g，可选）" else "初始体重仅首次建档时填写") }, singleLine = true, enabled = fishId == null)
            OutlinedTextField(sex, { sex = it }, Modifier.fillMaxWidth(), label = { Text("性别（可选）") }, singleLine = true)
            OutlinedTextField(source, { source = it }, Modifier.fillMaxWidth(), label = { Text("来源（可选）") }, singleLine = true)
            OutlinedTextField(notes, { notes = it }, Modifier.fillMaxWidth(), label = { Text("备注（可选）") }, minLines = 2)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
}

@Composable
fun FishDetailScreen(
    fishId: String,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onOpenRecords: (String) -> Unit = {},
    onOpenTank: (String) -> Unit = {},
    onRecordWater: (String) -> Unit = {},
    vm: FishViewModel = viewModel(factory = FishViewModel.factory(LocalContext.current.applicationContext as Application)),
) {
    val detailState by remember(fishId) { vm.detailState(fishId) }.collectAsStateWithLifecycle()
    val summary = (detailState as? FishLoadState.Success)?.value
    val tanks by vm.tanks.collectAsStateWithLifecycle()
    val availableTanks = tanks.orEmpty()
    val events by remember(fishId) { vm.events(fishId) }.collectAsStateWithLifecycle()
    val growth by remember(fishId) { vm.growth(fishId) }.collectAsStateWithLifecycle()
    val latestWeight by remember(fishId) { vm.latestWeight(fishId) }.collectAsStateWithLifecycle()
    val fish = summary?.fish
    val tank = availableTanks.firstOrNull { it.id == fish?.tankId }
    val tankId = tank?.id
    val photo by remember(fish?.coverPhotoId) { vm.photo(fish?.coverPhotoId) }.collectAsStateWithLifecycle()
    val tankPhoto by remember(tank?.photoId) { vm.photo(tank?.photoId) }.collectAsStateWithLifecycle()
    val waterTests by remember(tankId) {
        if (tankId == null) flowOf(emptyList()) else vm.waterTests(tankId)
    }.collectAsStateWithLifecycle(initialValue = emptyList())
    val latestWaterTest = remember(waterTests) { waterTests.maxByOrNull { it.test.measuredAt } }
    val waterMetrics = remember(latestWaterTest) { fishWaterMetrics(latestWaterTest?.values.orEmpty(), 4) }
    val feedingsFlow = remember(fishId, tankId) {
        if (tankId == null) flowOf(emptyList<FeedingEvent>()) else vm.feedings(fishId, tankId)
    }
    val feedings by feedingsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val growthPoints = remember(growth) { growthPoints(growth, true, GrowthRange.All, System.currentTimeMillis()) }
    val latestPoint = growthPoints.lastOrNull()
    val lastFeeding = remember(feedings) { feedings.maxByOrNull { it.fedAt } }
    val todayFeedings = summary?.todayFeedings ?: 0
    val statusStyle = fish?.status?.let(::fishStatusStyle)

    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    var showMore by remember { mutableStateOf(false) }
    var showGrowth by remember { mutableStateOf(false) }
    var showPhotoViewer by remember { mutableStateOf(false) }
    var feedingBusy by remember { mutableStateOf(false) }
    var growthBusy by remember { mutableStateOf(false) }
    var action by remember { mutableStateOf<FishStatus?>(null) }
    var showArchive by remember { mutableStateOf(false) }
    var unknownDeath by remember { mutableStateOf(false) }
    var showMove by remember { mutableStateOf(false) }
    var targetTankId by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var date by remember { mutableLongStateOf(todayStart()) }

    val photoVm: PhotoViewModel = viewModel(
        key = "fish-detail-photo-$fishId",
        factory = PhotoViewModelFactory(vm.photoRepository),
    )
    val photoState by photoVm.state.collectAsStateWithLifecycle()
    var coverSaving by remember { mutableStateOf(false) }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) photoVm.import(uri)
    }
    LaunchedEffect(photoState.photo?.id, fish?.coverPhotoId) {
        val selected = photoState.photo ?: return@LaunchedEffect
        val current = fish ?: return@LaunchedEffect
        if (selected.id == current.coverPhotoId || coverSaving) return@LaunchedEffect
        coverSaving = true
        vm.save(
            current.copy(coverPhotoId = selected.id, updatedAt = System.currentTimeMillis()),
            length = null,
            weight = null,
            onSaved = {
                coverSaving = false
                photoVm.clearSelection()
                scope.launch { snackbar.showSnackbar("照片已更新") }
            },
            onError = {
                coverSaving = false
                scope.launch { snackbar.showSnackbar(it) }
            },
        )
    }
    LaunchedEffect(photoState.message) {
        photoState.message?.let { message -> snackbar.showSnackbar(message.label()) }
    }

    fun recordOneTapFeeding() {
        val current = fish ?: return
        if (feedingBusy) return
        feedingBusy = true
        vm.recordQuickFeeding(
            fish = current,
            foodType = lastFeeding?.foodType,
            onSaved = { eventId ->
                feedingBusy = false
                scope.launch {
                    val result = snackbar.showSnackbar(
                        message = "已记录一次喂食",
                        actionLabel = "撤销",
                        withDismissAction = false,
                        duration = SnackbarDuration.Short,
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        vm.undoFeeding(
                            id = eventId,
                            onDone = { scope.launch { snackbar.showSnackbar("已撤销这次喂食") } },
                            onError = { scope.launch { snackbar.showSnackbar(it) } },
                        )
                    }
                }
            },
            onError = {
                feedingBusy = false
                scope.launch { snackbar.showSnackbar(it) }
            },
        )
    }

    val manageable = fish?.status == FishStatus.active || fish?.status == FishStatus.quarantine
    val heroHeight = 280.dp
    val overlap = 28.dp

    Scaffold(
        containerColor = DetailTokens.PageBackground,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (fish != null && manageable) {
                DetailRecordDock(
                    primaryText = "记录喂食",
                    primaryIcon = FishIcons.Fish,
                    onPrimary = ::recordOneTapFeeding,
                    primaryEnabled = !feedingBusy,
                    secondary = listOf(
                        DockAction("测量", FishIcons.Ruler) { showGrowth = true },
                        DockAction("照片", FishIcons.Camera) {
                            gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                    ),
                )
            }
        },
    ) { padding ->
        when (val state = detailState) {
            FishLoadState.Loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { LoadingState("正在加载鱼只档案") }
            FishLoadState.NotFound -> Column(Modifier.fillMaxSize().padding(padding).padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("找不到这条鱼", modifier = Modifier.testTag("fish-not-found"), style = MaterialTheme.typography.titleLarge)
                Text("鱼只可能已被删除或链接已失效。")
                TextButton(onClick = onBack) { Text("返回") }
            }
            is FishLoadState.Error -> Column(Modifier.fillMaxSize().padding(padding).padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("鱼只加载失败", modifier = Modifier.testTag("fish-detail-error"), style = MaterialTheme.typography.titleLarge)
                Text(state.message)
                TextButton(onClick = onBack) { Text("返回") }
            }
            is FishLoadState.Success -> if (fish != null && statusStyle != null) {
                Box(Modifier.fillMaxSize().padding(padding)) {
                    Column(Modifier.fillMaxSize().verticalScroll(scrollState)) {
                        Box(Modifier.fillMaxWidth()) {
                            FishDetailHero(
                                fish = fish,
                                tank = tank,
                                photo = photo,
                                statusStyle = statusStyle,
                                onBack = onBack,
                                onMore = { showMore = true },
                                onOpenTank = { tank?.id?.let(onOpenTank) },
                                onOpenPhoto = { if (photo != null) showPhotoViewer = true },
                                modifier = Modifier.fillMaxWidth().height(heroHeight),
                            )
                            Column(Modifier.fillMaxWidth().padding(top = heroHeight - overlap, bottom = 24.dp)) {
                                FishTodayCard(
                                    todayFeedings = todayFeedings,
                                    lastFeeding = lastFeeding,
                                    durationDays = durationDays(fish.addedAt, fish.deathDate, fish.status == FishStatus.deceased),
                                    latestPoint = latestPoint,
                                    latestWeight = latestWeight,
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                )
                                FishTankWaterCard(
                                    tank = tank,
                                    tankPhoto = tankPhoto,
                                    metrics = waterMetrics,
                                    measuredAt = latestWaterTest?.test?.measuredAt,
                                    onOpenTank = { tank?.id?.let(onOpenTank) },
                                    onRecordWater = { tank?.id?.let(onRecordWater) },
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                )
                                DetailSectionHeader(
                                    title = "成长",
                                    countLabel = "${growth.size} 次测量",
                                    actionLabel = "全部记录",
                                    onAction = { onOpenRecords("growth") },
                                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp),
                                )
                                FishGrowthCard(
                                    points = growthPoints,
                                    latestWeight = latestWeight,
                                    onOpenRecords = { onOpenRecords("growth") },
                                    onRecord = { showGrowth = true },
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                )
                                DetailSectionHeader(
                                    title = "最近动态",
                                    actionLabel = "全部动态",
                                    onAction = { onOpenRecords("timeline") },
                                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp),
                                )
                                FishTimelineCard(
                                    items = buildFishTimeline(events, growth, feedings, availableTanks),
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                )
                                DetailSectionHeader(
                                    title = "档案",
                                    actionLabel = "仅展示已记录",
                                    mutedAction = true,
                                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp),
                                )
                                FishProfileCard(
                                    fish = fish,
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
                            title = fish.name,
                            statusText = statusStyle.label,
                            statusColor = statusStyle.dot,
                            onBack = onBack,
                            onMore = { showMore = true },
                        )
                    }
                }
            }
        }
    }

    if (showMore && fish != null) {
        ModalBottomSheet(
            onDismissRequest = { showMore = false },
            containerColor = Color.White,
            contentColor = DetailTokens.TitleInk,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            FishMoreSheet(
                fish = fish,
                manageable = manageable,
                onMove = { showMore = false; showMove = true },
                onStatus = { showMore = false; action = it },
                onArchive = { showMore = false; showArchive = true },
                onDismiss = { showMore = false },
            )
        }
    }

    if (showGrowth) {
        GrowthRecordDialog(
            fishId = fishId,
            record = null,
            vm = vm,
            busy = growthBusy,
            onBusy = { growthBusy = it },
            onError = {
                growthBusy = false
                scope.launch { snackbar.showSnackbar(it) }
            },
            onDismiss = { if (!growthBusy) { showGrowth = false } },
            onSaved = {
                growthBusy = false
                showGrowth = false
                scope.launch { snackbar.showSnackbar("已记录成长") }
            },
        )
    }

    if (showPhotoViewer && photo != null) {
        Dialog(onDismissRequest = { showPhotoViewer = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Surface(modifier = Modifier.fillMaxWidth().padding(16.dp), shape = MaterialTheme.shapes.large, color = Color.White) {
                Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.End) {
                    ManagedPhotoImage(photo, thumbnail = false, modifier = Modifier.fillMaxWidth().heightIn(max = 620.dp).aspectRatio(1f))
                    TextButton(onClick = { showPhotoViewer = false }) { Text("关闭") }
                }
            }
        }
    }

    if (action == FishStatus.deceased) AlertDialog(
        onDismissRequest = { action = null },
        title = { Text("标记为已故") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("请确认发生日期；日期不详时可明确记录为未知。")
                DateField("发生日期", date) { it?.let { value -> date = value } }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(unknownDeath, { unknownDeath = it })
                    Text("日期不详")
                }
            }
        },
        confirmButton = {
            TextButton(enabled = !busy, onClick = {
                busy = true
                vm.setStatus(fishId, FishStatus.deceased, if (unknownDeath) null else date, !unknownDeath, {
                    busy = false; action = null
                }, { busy = false; error = it })
            }) { Text(if (busy) "保存中" else "确认") }
        },
        dismissButton = { TextButton(onClick = { action = null }) { Text("取消") } },
    ) else if (action == FishStatus.transferred) AlertDialog(
        onDismissRequest = { action = null },
        title = { Text("标记已转出") },
        text = { DateField("转出日期", date) { it?.let { value -> date = value } } },
        confirmButton = {
            TextButton(enabled = !busy, onClick = {
                busy = true
                vm.setStatus(fishId, FishStatus.transferred, date, true, { busy = false; action = null }, { busy = false; error = it })
            }) { Text(if (busy) "保存中" else "确认转出") }
        },
        dismissButton = { TextButton(onClick = { action = null }) { Text("取消") } },
    ) else if (action == FishStatus.active || action == FishStatus.quarantine) AlertDialog(
        onDismissRequest = { action = null },
        title = { Text("更改鱼只状态") },
        text = { Text("${fish?.name}将标记为${action!!.label()}。") },
        confirmButton = {
            TextButton(enabled = !busy, onClick = {
                busy = true
                vm.setStatus(fishId, action!!, System.currentTimeMillis(), onSaved = { busy = false; action = null }, onError = { busy = false; error = it })
            }) { Text("确认") }
        },
        dismissButton = { TextButton(onClick = { action = null }) { Text("取消") } },
    )

    if (showArchive) ConfirmDialog(
        "归档鱼只？",
        "归档后停止该鱼只的未来任务，档案与历史记录仍可查询。",
        "归档",
        onConfirm = {
            busy = true
            vm.archive(fishId, { busy = false; showArchive = false; onBack() }, { busy = false; error = it })
            showArchive = false
        },
        onDismiss = { showArchive = false },
    )

    if (showMove) AlertDialog(
        onDismissRequest = { showMove = false },
        title = { Text("转缸") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                var menu by remember { mutableStateOf(false) }
                Box {
                    OutlinedButton(onClick = { menu = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("目标鱼缸：${availableTanks.firstOrNull { it.id == targetTankId }?.name ?: "选择鱼缸"}")
                    }
                    DropdownMenu(menu, { menu = false }) {
                        availableTanks.filter { it.status == TankStatus.active && it.id != fish?.tankId }.forEach { candidate ->
                            DropdownMenuItem(text = { Text(candidate.name) }, onClick = { targetTankId = candidate.id; menu = false })
                        }
                    }
                }
                DateField("转缸日期", date) { it?.let { value -> date = value } }
            }
        },
        confirmButton = {
            TextButton(enabled = !busy && targetTankId.isNotBlank(), onClick = {
                busy = true
                vm.move(fishId, targetTankId, date, { busy = false; showMove = false }, { busy = false; error = it })
            }) { Text(if (busy) "保存中" else "确认转缸") }
        },
        dismissButton = { TextButton(onClick = { showMove = false }) { Text("取消") } },
    )
    LaunchedEffect(error) {
        val message = error ?: return@LaunchedEffect
        snackbar.showSnackbar(message)
        error = null
    }
}

private data class FishStatusStyle(val label: String, val dot: Color, val background: Color, val border: Color)

private fun fishStatusStyle(status: FishStatus): FishStatusStyle = when (status) {
    FishStatus.active -> FishStatusStyle("在养", Color(0xFF5FE0CD), Color(0x7A12806C), Color(0x7A6DEBD2))
    FishStatus.quarantine -> FishStatusStyle("隔离", Color(0xFFE2A03F), Color(0x7AB8771E), Color(0x8AE2A03F))
    FishStatus.transferred -> FishStatusStyle("已转出", Color(0xFF8C7BEC), Color(0x7A5A55C4), Color(0x8A8C7BEC))
    FishStatus.deceased -> FishStatusStyle("已故", Color(0xFF93A3B1), Color(0x7A3F4A57), Color(0x8A93A3B1))
    FishStatus.archived -> FishStatusStyle("已归档", Color(0xFF6D8BB0), Color(0x7A3A4A63), Color(0x8A6D8BB0))
}

@Composable
private fun FishDetailHero(
    fish: Fish,
    tank: Tank?,
    photo: Photo?,
    statusStyle: FishStatusStyle,
    onBack: () -> Unit,
    onMore: () -> Unit,
    onOpenTank: () -> Unit,
    onOpenPhoto: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val statusBarInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    Box(modifier) {
        DetailHeroPhoto(photo, Modifier.fillMaxSize().clickable(onClick = onOpenPhoto))
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
            Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(start = 18.dp, end = 18.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                HeroStatusPill(statusStyle.label, statusStyle.dot, statusStyle.background, statusStyle.border)
                HeroGhostPill(listOfNotNull(tank?.type?.let(::waterTypeLabel), fish.species?.trim()?.takeIf(String::isNotBlank)).joinToString(" · ").ifBlank { "档案信息" })
            }
            Text(
                fish.name,
                color = Color.White,
                fontSize = 31.sp,
                lineHeight = 37.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(fish.species?.trim()?.takeIf(String::isNotBlank) ?: "鱼种未记录", color = DetailTokens.HeroSecondary, fontSize = 12.5.sp)
                Box(Modifier.size(3.dp).clip(CircleShape).background(Color(0x99CBE4F2)))
                Row(
                    Modifier.clip(DetailTokens.PillShape).clickable(enabled = tank != null, role = Role.Button, onClick = onOpenTank).padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(tank?.name ?: "鱼缸未找到", color = Color(0xFFEAF7FF), fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                    Icon(FishIcons.ArrowRight, contentDescription = null, tint = Color(0xFFEAF7FF), modifier = Modifier.size(12.dp))
                }
            }
        }
    }
}

@Composable
private fun FishTodayCard(
    todayFeedings: Int,
    lastFeeding: FeedingEvent?,
    durationDays: Long,
    latestPoint: GrowthPoint?,
    latestWeight: GrowthRecord?,
    modifier: Modifier = Modifier,
) {
    val today = lastFeeding?.takeIf { isToday(it.fedAt) }
    DeepWaterCard(modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 13.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text("今天", color = Color(0xFF8EC8DF), fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.4.sp)
                    Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 3.dp)) {
                        Text(
                            if (todayFeedings > 0) "已喂食 $todayFeedings 次" else "今天还没喂",
                            color = Color.White,
                            fontSize = 21.sp,
                            lineHeight = 26.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        today?.let {
                            Text(
                                SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(it.fedAt)),
                                color = Color(0xFFA9CCDE),
                                fontSize = 12.sp,
                                modifier = Modifier.padding(start = 7.dp, bottom = 2.dp),
                            )
                        }
                    }
                    Text(
                        today?.let { listOfNotNull(it.foodType?.takeIf(String::isNotBlank), it.result?.takeIf(String::isNotBlank)).joinToString(" · ").ifBlank { "喂食记录已保存" } }
                            ?: "记录一次喂食，会自动关联当前鱼与所属鱼缸",
                        color = Color(0xFF9FC4D8),
                        fontSize = 11.5.sp,
                        lineHeight = 15.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                Row(
                    Modifier.clip(DetailTokens.PillShape).background(if (todayFeedings > 0) Color(0x265FE0CD) else Color(0x26E7C27C)).border(
                        1.dp,
                        if (todayFeedings > 0) Color(0x6B5FE0CD) else Color(0x6BE7C27C),
                        DetailTokens.PillShape,
                    ).padding(horizontal = 9.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Box(Modifier.size(5.dp).clip(CircleShape).background(if (todayFeedings > 0) DetailTokens.AquaBright else DetailTokens.Sun))
                    Text(if (todayFeedings > 0) "今日已照顾" else "待喂食", color = if (todayFeedings > 0) Color(0xFF9FF3E6) else Color(0xFFFFD9A0), fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            Box(Modifier.fillMaxWidth().padding(top = 10.dp).height(1.dp).background(Color(0x24FFFFFF)))
            Row(Modifier.fillMaxWidth().padding(top = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                DeepMetric("入缸", durationDays.toString(), "天", Modifier.weight(1f))
                Box(Modifier.width(1.dp).height(34.dp).background(Color(0x24FFFFFF)))
                DeepMetric("最近体长", latestPoint?.value?.prettyNumber() ?: "未记录", latestPoint?.unit, Modifier.weight(1f).padding(start = 12.dp))
                Box(Modifier.width(1.dp).height(34.dp).background(Color(0x24FFFFFF)))
                DeepMetric("最近体重", latestWeight?.weightValue?.pretty() ?: "未记录", latestWeight?.weightUnit, Modifier.weight(1f).padding(start = 12.dp))
            }
        }
    }
}

@Composable
private fun FishTankWaterCard(
    tank: Tank?,
    tankPhoto: Photo?,
    metrics: List<FishWaterMetric>,
    measuredAt: Long?,
    onOpenTank: () -> Unit,
    onRecordWater: () -> Unit,
    modifier: Modifier = Modifier,
) {
    DetailGlassCard(modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 13.dp, vertical = 12.dp)) {
            Row(
                Modifier.fillMaxWidth().clickable(enabled = tank != null, onClick = onOpenTank),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    Modifier.size(44.dp).clip(RoundedCornerShape(13.dp)).background(Color(0xFF0A2A44)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (tankPhoto != null) ManagedPhotoImage(tankPhoto, thumbnail = true, modifier = Modifier.fillMaxSize())
                    else Icon(FishIcons.Aquarium, contentDescription = null, tint = Color(0x99CFE6F7), modifier = Modifier.size(22.dp))
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(tank?.name ?: "鱼缸未找到", color = DetailTokens.TitleInk, fontSize = 14.5.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        tank?.let { "${waterTypeLabel(it.type)}缸 · ${it.capacityValue?.let { value -> "${value.pretty()} ${it.capacityUnit.orEmpty()}" } ?: "容量未记录"}" } ?: "无法读取所属鱼缸",
                        color = DetailTokens.SecondaryInk,
                        fontSize = 11.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Icon(FishIcons.ArrowRight, contentDescription = null, tint = Color(0xFF9AB2C4), modifier = Modifier.size(18.dp))
            }
            Box(Modifier.fillMaxWidth().padding(top = 10.dp).height(1.dp).background(Color(0xFFEEF5FA)))
            Row(Modifier.fillMaxWidth().padding(top = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                val codes = if (tank?.type == TankType.saltwater) listOf("temperature", "salinity", "ph") else listOf("temperature", "ph", "ammonia")
                codes.forEachIndexed { index, code ->
                    val metric = metrics.firstOrNull { it.code == code }
                    val parts = metricValueParts(metric?.value)
                    GlassMetric(
                        label = dashboardParameterLabel(code),
                        value = parts.first,
                        unit = parts.second,
                        modifier = Modifier.weight(1f).then(if (index > 0) Modifier.padding(start = 11.dp) else Modifier),
                    )
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    measuredAt?.let { "检测于 ${relativeTimeLabel(it)}" } ?: "尚无测量数据",
                    color = DetailTokens.TertiaryInk,
                    fontSize = 10.5.sp,
                    modifier = Modifier.weight(1f),
                )
                Row(
                    Modifier.clip(DetailTokens.PillShape).clickable(enabled = tank != null, role = Role.Button, onClick = onRecordWater).padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(1.dp),
                ) {
                    Text("去记录", color = DetailTokens.Primary, fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold)
                    Icon(FishIcons.ArrowRight, contentDescription = null, tint = DetailTokens.Primary, modifier = Modifier.size(11.dp))
                }
            }
        }
    }
}

@Composable
private fun FishGrowthCard(
    points: List<GrowthPoint>,
    latestWeight: GrowthRecord?,
    onOpenRecords: () -> Unit,
    onRecord: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val latest = points.lastOrNull()
    val now = System.currentTimeMillis()
    val recent = points.filter { it.record.measuredAt >= now - 90L * 86_400_000L }
    val delta = if (recent.size >= 2) recent.last().value - recent.first().value else null
    DetailGlassCard(modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(latest?.value?.prettyNumber() ?: "未记录", color = Color(0xFF0F3450), fontSize = 28.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold)
                    latest?.unit?.let { Text(it, color = Color(0xFF5F7F96), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 3.dp, bottom = 3.dp)) }
                }
                if (delta != null && delta > 0.0) {
                    Row(
                        Modifier.padding(start = 8.dp, bottom = 2.dp).clip(DetailTokens.PillShape).background(DetailTokens.AquaTint).padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text("近 90 天 +${delta.prettyNumber()}", color = Color(0xFF0E8E80), fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.End) {
                    Text("最近测量", color = DetailTokens.TertiaryInk, fontSize = 9.5.sp)
                    Text(latest?.let { formatDate(it.record.measuredAt) } ?: "--", color = Color(0xFF34566F), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            if (points.isEmpty()) {
                Text(
                    "还没有成长记录。记录第一次体长或体重后，这里会显示真实变化。",
                    color = DetailTokens.SecondaryInk,
                    fontSize = 11.5.sp,
                    lineHeight = 16.sp,
                    modifier = Modifier.padding(top = 12.dp),
                )
            } else {
                FishGrowthChart(points, Modifier.fillMaxWidth().padding(top = 4.dp))
            }
            Box(Modifier.fillMaxWidth().padding(top = 6.dp).height(1.dp).background(Color(0xFFEFF5FA)))
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("仅使用真实测量点 · 单位 ${latest?.unit ?: "cm"}", color = DetailTokens.TertiaryInk, fontSize = 10.sp, modifier = Modifier.weight(1f))
                Row(
                    Modifier.clip(DetailTokens.PillShape).background(Color(0xFFF7FBFF)).border(1.dp, Color(0xFFC9DDF0), DetailTokens.PillShape)
                        .clickable(role = Role.Button, onClick = onRecord).padding(horizontal = 11.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = null, tint = DetailTokens.Primary, modifier = Modifier.size(12.dp))
                    Text("记录测量", color = DetailTokens.Primary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
            latestWeight?.weightValue?.let { value ->
                Text("最近体重 ${value.pretty()} ${latestWeight.weightUnit.orEmpty()}", color = DetailTokens.TertiaryInk, fontSize = 10.sp, modifier = Modifier.padding(top = 2.dp))
            }
        }
    }
}

@Composable
private fun FishGrowthChart(points: List<GrowthPoint>, modifier: Modifier = Modifier) {
    Column(modifier) {
        Canvas(Modifier.fillMaxWidth().height(88.dp)) {
            val gridColor = Color(0xFFEDF4F9)
            listOf(0.22f, 0.52f, 0.82f).forEach { fraction ->
                drawLine(gridColor, Offset(0f, size.height * fraction), Offset(size.width, size.height * fraction), 1.dp.toPx())
            }
            if (points.isEmpty()) return@Canvas
            val values = points.map { it.value }
            val low = values.minOrNull() ?: return@Canvas
            val high = values.maxOrNull() ?: return@Canvas
            val spread = (high - low).coerceAtLeast(0.1)
            val left = 4.dp.toPx()
            val right = size.width - 4.dp.toPx()
            val top = 8.dp.toPx()
            val bottom = size.height - 8.dp.toPx()
            fun x(index: Int): Float = if (points.size == 1) size.width / 2f else left + (right - left) * index / (points.size - 1)
            fun y(value: Double): Float = bottom - ((value - low) / spread).toFloat() * (bottom - top)
            val coordinates = points.mapIndexed { index, point -> Offset(x(index), y(point.value)) }
            if (coordinates.size > 1) {
                val area = Path().apply {
                    moveTo(coordinates.first().x, bottom)
                    coordinates.forEach { lineTo(it.x, it.y) }
                    lineTo(coordinates.last().x, bottom)
                    close()
                }
                drawPath(area, Brush.verticalGradient(listOf(Color(0x382AC9B9), Color(0x002AC9B9)), startY = top, endY = bottom))
                val line = Path().apply {
                    moveTo(coordinates.first().x, coordinates.first().y)
                    coordinates.drop(1).forEach { lineTo(it.x, it.y) }
                }
                drawPath(line, Brush.horizontalGradient(listOf(DetailTokens.AquaBright, Color(0xFF28B9C6), DetailTokens.Primary)), style = Stroke(2.4.dp.toPx(), cap = StrokeCap.Round))
            }
            coordinates.forEach { dot -> drawCircle(Color.White, 3.dp.toPx(), dot); drawCircle(DetailTokens.Aqua, 3.dp.toPx(), dot, style = Stroke(1.8.dp.toPx())) }
            coordinates.lastOrNull()?.let { last ->
                drawCircle(DetailTokens.Primary.copy(alpha = 0.12f), 7.dp.toPx(), last)
                drawCircle(Color.White, 4.dp.toPx(), last)
                drawCircle(DetailTokens.Primary, 4.dp.toPx(), last, style = Stroke(2.2.dp.toPx()))
            }
        }
        val labels = when {
            points.isEmpty() -> emptyList()
            points.size <= 4 -> points
            else -> listOf(points.first(), points[points.size / 3], points[points.size * 2 / 3], points.last())
        }
        Row(Modifier.fillMaxWidth().padding(top = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            labels.forEach { point -> Text(shortMonthDay(point.record.measuredAt), color = DetailTokens.TertiaryInk, fontSize = 9.sp) }
        }
    }
}

@Composable
private fun FishTimelineCard(items: List<FishTimelineItem>, modifier: Modifier = Modifier) {
    DetailGlassCard(modifier.fillMaxWidth()) {
        if (items.isEmpty()) {
            Text("暂无动态。建立档案、记录成长或喂食后，会在这里形成一条真实时间线。", color = DetailTokens.SecondaryInk, fontSize = 11.5.sp, lineHeight = 16.sp, modifier = Modifier.padding(16.dp))
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
private fun FishProfileCard(fish: Fish, onEdit: () -> Unit, modifier: Modifier = Modifier) {
    val cells = listOf(
        "入缸日期" to formatDate(fish.addedAt),
        "来源" to (fish.source?.trim()?.takeIf(String::isNotBlank) ?: "未记录"),
        "性别" to (fish.sex?.trim()?.takeIf(String::isNotBlank) ?: "未记录"),
        "出生日期" to (fish.birthDate?.let(::formatDate) ?: "未记录"),
        "备注" to (fish.notes?.trim()?.takeIf(String::isNotBlank) ?: "未记录"),
        "鱼种" to (fish.species?.trim()?.takeIf(String::isNotBlank) ?: "未记录"),
    )
    DetailGlassCard(modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 11.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("基础信息", color = Color(0xFF173A54), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Text(
                    "编辑档案",
                    modifier = Modifier.clip(DetailTokens.PillShape).clickable(role = Role.Button, onClick = onEdit).padding(horizontal = 4.dp, vertical = 4.dp),
                    color = DetailTokens.Primary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                cells.chunked(3).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { (label, value) ->
                            Column(Modifier.weight(1f)) {
                                Text(label, color = Color(0xFF90A7B9), fontSize = 9.5.sp)
                                Text(
                                    value,
                                    color = if (value == "未记录") Color(0xFFA3B6C5) else DetailTokens.BodyInk,
                                    fontSize = 12.sp,
                                    fontWeight = if (value == "未记录") FontWeight.Normal else FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(top = 2.dp),
                                )
                            }
                        }
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
            Box(Modifier.fillMaxWidth().padding(top = 9.dp).height(1.dp).background(Color(0xFFEFF5FA)))
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("未知信息保持为空，不显示为 0", color = DetailTokens.TertiaryInk, fontSize = 10.sp)
            }
        }
    }
}

private data class FishTimelineItem(
    val at: Long,
    val icon: ImageVector,
    val tint: Color,
    val background: Color,
    val title: String,
    val subtitle: String,
    val trailing: String?,
    val trailingSub: String?,
)

private fun buildFishTimeline(
    events: List<FishEvent>,
    growth: List<GrowthRecord>,
    feedings: List<FeedingEvent>,
    tanks: List<Tank>,
): List<FishTimelineItem> {
    val items = ArrayList<FishTimelineItem>()
    events.forEach { event ->
        items += FishTimelineItem(
            at = event.occurredAt,
            icon = FishIcons.Tag,
            tint = DetailTokens.SunInk,
            background = DetailTokens.SunTint,
            title = fishEventLabel(event, tanks),
            subtitle = event.notes?.takeIf(String::isNotBlank) ?: "档案事件",
            trailing = relativeTimeLabel(event.occurredAt),
            trailingSub = null,
        )
    }
    growth.forEach { record ->
        val length = record.lengthValue?.let { "体长 ${it.pretty()} ${record.lengthUnit.orEmpty()}" }
        val weight = record.weightValue?.let { "体重 ${it.pretty()} ${record.weightUnit.orEmpty()}" }
        items += FishTimelineItem(
            at = record.measuredAt,
            icon = FishIcons.Ruler,
            tint = DetailTokens.Primary,
            background = DetailTokens.BlueEventTint,
            title = "成长 · ${length ?: weight ?: "测量记录"}",
            subtitle = record.method?.takeIf(String::isNotBlank) ?: "真实测量",
            trailing = record.lengthValue?.let { "${it.pretty()} ${record.lengthUnit.orEmpty()}" } ?: record.weightValue?.let { "${it.pretty()} ${record.weightUnit.orEmpty()}" },
            trailingSub = relativeTimeLabel(record.measuredAt),
        )
    }
    feedings.forEach { event ->
        items += FishTimelineItem(
            at = event.fedAt,
            icon = FishIcons.Fish,
            tint = Color(0xFF0E8E80),
            background = DetailTokens.AquaTint,
            title = "喂食 · ${event.foodType?.takeIf(String::isNotBlank) ?: "饲料未记录"}",
            subtitle = event.result?.takeIf(String::isNotBlank) ?: "已记录",
            trailing = "1 次",
            trailingSub = relativeTimeLabel(event.fedAt),
        )
    }
    return items.sortedByDescending { it.at }.take(4)
}

@Composable
private fun FishMoreSheet(
    fish: Fish,
    manageable: Boolean,
    onMove: () -> Unit,
    onStatus: (FishStatus) -> Unit,
    onArchive: () -> Unit,
    onDismiss: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
        Text("管理 ${fish.name}", color = DetailTokens.TitleInk, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text("低频操作集中在这里，避免占用首屏", color = DetailTokens.SecondaryInk, fontSize = 11.5.sp, modifier = Modifier.padding(top = 3.dp, bottom = 8.dp))
        if (manageable) {
            MoreSheetAction("转缸", "记录转缸日期和目标鱼缸，历史事件保留原归属", onMove)
            MoreSheetAction(if (fish.status == FishStatus.quarantine) "恢复在养" else "隔离观察", "状态变更会写入时间线", { onStatus(if (fish.status == FishStatus.quarantine) FishStatus.active else FishStatus.quarantine) })
            MoreSheetAction("标记转出", "保留历史，不再参与当前在养统计", { onStatus(FishStatus.transferred) })
            MoreSheetAction("标记已故", "需要确认日期；日期不详可明确记录", { onStatus(FishStatus.deceased) }, danger = true)
        }
        if (fish.status != FishStatus.archived) {
            MoreSheetAction("归档鱼只", "停止未来任务，档案与历史仍可查询", onArchive, danger = true)
        }
        TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) { Text("取消") }
    }
}

@Composable
private fun MoreSheetAction(label: String, description: String, onClick: () -> Unit, danger: Boolean = false) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 4.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier.size(32.dp).clip(RoundedCornerShape(10.dp)).background(if (danger) Color(0xFFFDECEA) else Color(0xFFF1F6FA)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(if (danger) FishIcons.Alert else FishIcons.Tag, contentDescription = null, tint = if (danger) DetailTokens.Danger else DetailTokens.Primary, modifier = Modifier.size(16.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(label, color = if (danger) DetailTokens.Danger else DetailTokens.TitleInk, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(description, color = DetailTokens.TertiaryInk, fontSize = 10.5.sp, lineHeight = 14.sp, modifier = Modifier.padding(top = 2.dp))
        }
        Icon(FishIcons.ArrowRight, contentDescription = null, tint = Color(0xFF9AB2C4), modifier = Modifier.size(16.dp))
    }
}

private fun metricValueParts(text: String?): Pair<String, String?> {
    val clean = text?.trim().orEmpty()
    if (clean.isBlank()) return "未记录" to null
    val match = Regex("^(-?\\d+(?:\\.\\d+)?)\\s*(.*)$").find(clean)
    if (match == null) return clean to null
    return match.groupValues[1] to match.groupValues[2].ifBlank { null }
}

private fun shortMonthDay(value: Long): String = SimpleDateFormat("M/d", Locale.getDefault()).format(Date(value))

private fun isToday(value: Long): Boolean {
    val now = Calendar.getInstance()
    val then = Calendar.getInstance().apply { timeInMillis = value }
    return now.get(Calendar.YEAR) == then.get(Calendar.YEAR) &&
        now.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR)
}

private fun fishEventLabel(event: FishEvent, tanks: List<Tank>): String = when (event.type) {
    FishEventType.created -> "建立档案"
    FishEventType.moved -> {
        val from = tanks.firstOrNull { it.id == event.tankId }?.name ?: "原鱼缸"
        val to = tanks.firstOrNull { it.id == event.targetTankId }?.name ?: "目标鱼缸"
        "转缸：$from → $to"
    }
    FishEventType.statusChanged -> event.status?.label() ?: "状态变更"
    FishEventType.archived -> "归档"
}

@Composable
fun FishRecordsScreen(fishId: String, section: String, onBack: () -> Unit, vm: FishViewModel = viewModel(factory = FishViewModel.factory(LocalContext.current.applicationContext as Application))) {
    val state by remember(fishId) { vm.recordsState(fishId) }.collectAsStateWithLifecycle()
    val fishName = (state as? FishRecordsState.Success)?.fish?.name ?: "鱼只"
    val title = when (section) { "growth" -> "成长记录"; "feeding" -> "喂食记录"; else -> "鱼只时间线" }
    Scaffold(topBar = { FishTopBar("$fishName · $title", onBack) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("$fishName · $title", modifier = Modifier.testTag("fish-records-title"), style = MaterialTheme.typography.titleMedium)
            when (val recordsState = state) {
                FishRecordsState.Loading -> LoadingState("正在加载鱼只记录")
                FishRecordsState.NotFound -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("找不到这条鱼", modifier = Modifier.testTag("fish-not-found"))
                    Text("鱼只可能已被删除或链接已失效。")
                    TextButton(onClick = onBack) { Text("返回") }
                }
                is FishRecordsState.Error -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("鱼只记录加载失败", modifier = Modifier.testTag("fish-records-error"))
                    Text(recordsState.message)
                    TextButton(onClick = onBack) { Text("返回") }
                }
                is FishRecordsState.Success -> when (section) {
                "growth" -> {
                    GrowthHistoryContent(fishId, recordsState.growth, vm)
                }
                "feeding" -> {
                    if (recordsState.feedings.isEmpty()) EmptyState("暂无喂食记录", "指定鱼只和所属鱼缸的喂食事件会显示在这里。")
                    recordsState.feedings.forEach { record -> InfoCard(formatDateTime(record.fedAt), { Text("${record.foodType ?: "饲料未记录"} · ${record.amountValue?.let { "${it.pretty()} ${record.amountUnit.orEmpty()}" } ?: "投喂量未记录"}"); record.result?.let { Text("摄食情况：$it") }; record.notes?.let { Text(it) } }) }
                }
                else -> {
                    val timeline = buildList {
                        recordsState.events.forEach { event ->
                            add(
                                Triple(
                                    event.occurredAt,
                                    when (event.type) {
                                        FishEventType.created -> "建立档案"
                                        FishEventType.moved -> "转缸"
                                        FishEventType.statusChanged -> event.status?.label() ?: "状态变更"
                                        FishEventType.archived -> "归档"
                                    },
                                    if (event.dateKnown) "" else "发生日期未知",
                                ),
                            )
                        }
                        recordsState.growth.forEach { record ->
                            add(Triple(record.measuredAt, "成长 · ${growthMeasurementSummary(record)}", record.method?.takeIf(String::isNotBlank) ?: "真实测量"))
                        }
                        recordsState.feedings.forEach { event ->
                            add(
                                Triple(
                                    event.fedAt,
                                    "喂食 · ${event.foodType?.takeIf(String::isNotBlank) ?: "饲料未记录"}",
                                    listOfNotNull(
                                        event.amountValue?.let { "${it.pretty()} ${event.amountUnit.orEmpty()}" },
                                        event.result?.takeIf(String::isNotBlank),
                                        event.notes?.takeIf(String::isNotBlank),
                                    ).joinToString(" · ").ifBlank { "已记录" },
                                ),
                            )
                        }
                    }.sortedByDescending { it.first }
                    if (timeline.isEmpty()) EmptyState("暂无动态", "建立档案、记录成长或喂食后，这里会形成一条真实时间线。")
                    timeline.forEach { (at, title, detail) ->
                        InfoCard(formatDateTime(at), {
                            Text(title)
                            if (detail.isNotBlank()) Text(detail, style = MaterialTheme.typography.bodySmall)
                        })
                    }
                }
                }
            }
        }
    }
}

@Composable
private fun GrowthHistoryContent(fishId: String, records: List<GrowthRecord>, vm: FishViewModel) {
    var range by remember(fishId) { mutableStateOf(GrowthRange.Days30) }
    var editing by remember { mutableStateOf<GrowthRecord?>(null) }
    var creating by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<GrowthRecord?>(null) }
    var selected by remember { mutableStateOf<GrowthPoint?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val latest = records.maxByOrNull { it.measuredAt }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        latest?.let { record -> InfoCard("最近测量", { Text(growthMeasurementSummary(record)); Text(formatDateTime(record.measuredAt), style = MaterialTheme.typography.bodySmall) }) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GrowthRange.entries.forEach { option -> FilterChip(selected = range == option, onClick = { range = option; selected = null }, label = { Text(option.label) }) }
        }
        if (records.isEmpty()) {
            EmptyState("暂无成长记录", "记录体长或体重后，这里会显示真实变化趋势。")
        } else {
            GrowthTrend("体长趨势", growthPoints(records, true, range, System.currentTimeMillis()), selected?.takeIf { it.record.lengthValue != null }, { selected = it })
            GrowthTrend("体重趋势", growthPoints(records, false, range, System.currentTimeMillis()), selected?.takeIf { it.record.weightValue != null }, { selected = it })
            Button(enabled = !busy, onClick = { editing = null; creating = true }, modifier = Modifier.fillMaxWidth()) { Text("记录成长") }
            Text("历史记录 · ${records.size} 条", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            records.forEach { record ->
                InfoCard(formatDateTime(record.measuredAt), {
                    Text(growthMeasurementSummary(record).ifBlank { "无有效测量" })
                    record.method?.let { Text("测量方式：$it") }
                    record.notes?.takeIf(String::isNotBlank)?.let { Text(it) }
                    record.photoId?.let { photoId ->
                        val photo by remember(photoId) { vm.photo(photoId) }.collectAsStateWithLifecycle()
                        ManagedPhotoImage(photo, true, Modifier.fillMaxWidth().height(160.dp))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { editing = record; creating = true }) { Text("查看 / 更正") }
                        TextButton(enabled = !busy, onClick = { deleting = record }) { Text("删除") }
                    }
                })
            }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
    if (creating) GrowthRecordDialog(
        fishId = fishId,
        record = editing,
        vm = vm,
        busy = busy,
        onBusy = { busy = it },
        onError = { error = it },
        onDismiss = { if (!busy) creating = false },
        onSaved = { busy = false; creating = false; error = null },
    )
    deleting?.let { record ->
        ConfirmDialog("删除成长记录？", "此操作会移除此测量记录，并更新成长趋势与最近测量摘要。", "删除", onConfirm = {
            if (!busy) { busy = true; vm.deleteGrowth(record.id, { busy = false; deleting = null }, { busy = false; error = it }) }
        }, onDismiss = { deleting = null })
    }
}

@Composable
private fun GrowthTrend(title: String, points: List<GrowthPoint>, selected: GrowthPoint?, onSelect: (GrowthPoint) -> Unit) {
    InfoCard(title, {
        when (points.size) {
            0 -> Text("此范围暂无${if (title.startsWith("体长")) "体长" else "体重"}测量数据")
            1 -> Text("仅有一条测量记录，暂不形成趋势")
            else -> Text("${points.size} 个真实测量点 · 单位 ${points.first().unit}", style = MaterialTheme.typography.bodySmall)
        }
        if (points.isNotEmpty()) {
            GrowthChart(points, onSelect, Modifier.fillMaxWidth().height(150.dp))
            selected?.let { point -> Text("${formatDateTime(point.record.measuredAt)} · ${point.value.pretty()} ${point.unit}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
        }
    })
}

@Composable
private fun GrowthChart(points: List<GrowthPoint>, onSelect: (GrowthPoint) -> Unit, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary) {
    var size by remember { mutableStateOf(androidx.compose.ui.geometry.Size.Zero) }
    val chartColor = color
    Canvas(modifier.clickable {
        if (points.isNotEmpty() && size.width > 0f) {
            val step = if (points.size == 1) 0f else size.width / (points.size - 1)
            val index = (0 until points.size).minByOrNull { i -> kotlin.math.abs(i * step - size.width / 2f) } ?: 0
            onSelect(points[index])
        }
    }) {
        size = this.size
        val values = points.map { it.value }
        val low = values.minOrNull() ?: return@Canvas
        val high = values.maxOrNull() ?: return@Canvas
        val spread = max(high - low, max(kotlin.math.abs(high) * 0.08, 0.1))
        val bottom = this.size.height - 12.dp.toPx()
        val top = 12.dp.toPx()
        val coordinates = points.mapIndexed { index, point ->
            val x = if (points.size == 1) this.size.width / 2f else index * this.size.width / (points.size - 1)
            val y = bottom - ((point.value - low) / spread * (bottom - top)).toFloat()
            Offset(x, y)
        }
        if (coordinates.size > 1) {
            val path = Path().apply { moveTo(coordinates.first().x, coordinates.first().y); coordinates.drop(1).forEach { lineTo(it.x, it.y) } }
            drawPath(path, chartColor, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
        }
        coordinates.forEach { drawCircle(chartColor, radius = 5.dp.toPx(), center = it) }
    }
}

@Composable
private fun GrowthRecordDialog(
    fishId: String,
    record: GrowthRecord?,
    vm: FishViewModel,
    busy: Boolean,
    onBusy: (Boolean) -> Unit,
    onError: (String) -> Unit,
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
) {
    val photoVm: PhotoViewModel = viewModel(key = "growth-photo-${record?.id ?: "new-$fishId"}", factory = PhotoViewModelFactory(vm.photoRepository))
    var initialized by remember(record?.id) { mutableStateOf(false) }
    var measuredAt by remember(record?.id) { mutableLongStateOf(record?.measuredAt ?: System.currentTimeMillis()) }
    var length by remember(record?.id) { mutableStateOf(record?.lengthValue?.toString().orEmpty()) }
    var lengthUnit by remember(record?.id) { mutableStateOf(record?.lengthUnit ?: "cm") }
    var weight by remember(record?.id) { mutableStateOf(record?.weightValue?.toString().orEmpty()) }
    var weightUnit by remember(record?.id) { mutableStateOf(record?.weightUnit ?: "g") }
    var method by remember(record?.id) { mutableStateOf(record?.method.orEmpty()) }
    var notes by remember(record?.id) { mutableStateOf(record?.notes.orEmpty()) }
    var validation by remember(record?.id) { mutableStateOf<String?>(null) }
    val photoState by photoVm.state.collectAsStateWithLifecycle()
    LaunchedEffect(record?.id) { if (!initialized) { photoVm.load(record?.photoId); initialized = true } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (record == null) "记录成长" else "查看 / 更正成长记录") },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 560.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DateTimeField("测量时间", measuredAt) { measuredAt = it }
                OutlinedTextField(length, { length = it }, Modifier.fillMaxWidth(), label = { Text("体长（可选）") }, singleLine = true)
                UnitChoice(lengthUnit, listOf("cm", "mm", "in")) { lengthUnit = it }
                OutlinedTextField(weight, { weight = it }, Modifier.fillMaxWidth(), label = { Text("体重（可选）") }, singleLine = true)
                UnitChoice(weightUnit, listOf("g", "kg", "mg")) { weightUnit = it }
                OutlinedTextField(method, { method = it }, Modifier.fillMaxWidth(), label = { Text("测量方式（可选）") }, singleLine = true)
                OutlinedTextField(notes, { notes = it }, Modifier.fillMaxWidth(), label = { Text("备注（可选）") }, minLines = 2)
                PhotoPickerField(photoVm)
                validation?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(enabled = !busy && !photoState.isBusy, onClick = {
                val cleanLength = length.trim().toDoubleOrNull()
                val cleanWeight = weight.trim().toDoubleOrNull()
                validation = when {
                    length.isNotBlank() && (cleanLength == null || !cleanLength.isFinite() || cleanLength <= 0) -> "体长必须是大于 0 的有限数字"
                    weight.isNotBlank() && (cleanWeight == null || !cleanWeight.isFinite() || cleanWeight <= 0) -> "体重必须是大于 0 的有限数字"
                    cleanLength == null && cleanWeight == null -> "体长或体重至少填写一项有效数据"
                    measuredAt > System.currentTimeMillis() -> "测量时间不能晚于当前时间"
                    else -> null
                }
                if (validation == null) {
                    onBusy(true)
                    val value = GrowthRecord(
                        id = record?.id ?: UUID.randomUUID().toString(), fishId = fishId, measuredAt = measuredAt,
                        lengthValue = cleanLength, lengthUnit = cleanLength?.let { lengthUnit },
                        weightValue = cleanWeight, weightUnit = cleanWeight?.let { weightUnit },
                        method = method.trim().ifBlank { null }, photoId = photoState.photo?.id,
                        notes = notes.trim().ifBlank { null },
                    )
                    vm.saveGrowth(value, onSaved, { onBusy(false); onError(it); validation = it })
                }
            }) { Text(if (busy) "保存中…" else "保存") }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun UnitChoice(selected: String, options: List<String>, onSelect: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { unit -> FilterChip(selected = selected == unit, onClick = { onSelect(unit) }, label = { Text(unit) }) }
    }
}

@Composable
private fun DateTimeField(label: String, value: Long, onChange: (Long) -> Unit) {
    val context = LocalContext.current
    OutlinedButton(onClick = {
        val calendar = Calendar.getInstance().apply { timeInMillis = value }
        DatePickerDialog(context, { _, year, month, day ->
            val selectedDate = Calendar.getInstance().apply { timeInMillis = value; set(year, month, day) }
            TimePickerDialog(context, { _, hour, minute -> selectedDate.set(Calendar.HOUR_OF_DAY, hour); selectedDate.set(Calendar.MINUTE, minute); selectedDate.set(Calendar.SECOND, 0); selectedDate.set(Calendar.MILLISECOND, 0); onChange(selectedDate.timeInMillis) }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), true).show()
        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
    }, modifier = Modifier.fillMaxWidth()) { Text("$label：${formatDateTime(value)}") }
}

@Composable
private fun FishTopBar(title: String, onBack: () -> Unit, action: @Composable RowScope.() -> Unit = {}) {
    TopAppBar(title = { Text(title) }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回") } }, actions = action)
}

@Composable
private fun DateField(label: String, value: Long?, optional: Boolean = false, onChange: (Long?) -> Unit) {
    val context = LocalContext.current
    OutlinedButton(onClick = { val cal = Calendar.getInstance().apply { value?.let { timeInMillis = it } }; DatePickerDialog(context, { _, y, m, d -> onChange(Calendar.getInstance().apply { set(y,m,d,12,0,0); set(Calendar.MILLISECOND,0) }.timeInMillis) }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show() }, modifier = Modifier.fillMaxWidth()) { Text("$label：${value?.let(::formatDate) ?: if (optional) "未选择" else "选择日期"}") }
    if (optional && value != null) TextButton(onClick = { onChange(null) }) { Text("清除日期") }
}

private fun todayStart(): Long = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY,0); set(Calendar.MINUTE,0); set(Calendar.SECOND,0); set(Calendar.MILLISECOND,0) }.timeInMillis
internal fun durationDays(addedAt: Long, deathDate: Long?, deceased: Boolean, today: Long = System.currentTimeMillis()): Long {
    val end = if (deceased) deathDate ?: today else today
    return ((todayStartOf(end) - todayStartOf(addedAt)) / 86_400_000L).coerceAtLeast(0)
}
private fun todayStartOf(value: Long) = Calendar.getInstance().apply { timeInMillis = value; set(Calendar.HOUR_OF_DAY,0); set(Calendar.MINUTE,0); set(Calendar.SECOND,0); set(Calendar.MILLISECOND,0) }.timeInMillis
private fun formatDate(value: Long) = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(value))
private fun formatDateTime(value: Long) = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(value))
private fun Double.pretty() = if (this % 1.0 == 0.0) toLong().toString() else "%.1f".format(Locale.getDefault(), this)

@Composable
fun FishRecordsPage(
    fishId: String,
    section: String,
    onBack: () -> Unit,
    vm: FishViewModel = viewModel(factory = FishViewModel.factory(LocalContext.current.applicationContext as Application)),
) {
    when (section) {
        "growth" -> FishGrowthRecordsPage(fishId, onBack, vm)
        "timeline" -> FishTimelineRecordsPage(fishId, onBack, vm)
        else -> FishFeedingRecordsPage(fishId, onBack, vm)
    }
}

@Composable
private fun FishFeedingRecordsPage(fishId: String, onBack: () -> Unit, vm: FishViewModel) {
    val state by remember(fishId) { vm.recordsState(fishId) }.collectAsStateWithLifecycle()
    val fishName = (state as? FishRecordsState.Success)?.fish?.name ?: "鱼只"
    Scaffold(
        containerColor = DetailTokens.PageBackground,
        topBar = { SubpageTopBar("喂食记录", fishName, onBack, titleModifier = Modifier.testTag("fish-records-title")) },
    ) { padding ->
        when (val s = state) {
            FishRecordsState.Loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { LoadingState("正在加载喂食记录") }
            FishRecordsState.NotFound -> FishRecordMessage(padding, "找不到这条鱼", "鱼只可能已被删除或链接已失效。", onBack)
            is FishRecordsState.Error -> FishRecordMessage(padding, "鱼只记录加载失败", s.message, onBack)
            is FishRecordsState.Success -> LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 96.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                if (s.feedings.isEmpty()) {
                    item { EmptyState("暂无喂食记录", "指定鱼只和所属鱼缸的喂食事件会显示在这里。") }
                } else {
                    items(s.feedings.size) { index ->
                        val record = s.feedings[index]
                        InfoCard(formatDateTime(record.fedAt), {
                            Text("${record.foodType ?: "饲料未记录"} · ${record.amountValue?.let { "${it.pretty()} ${record.amountUnit.orEmpty()}" } ?: "投喂量未记录"}", fontWeight = FontWeight.SemiBold)
                            record.result?.let { Text("摄食情况：$it", style = MaterialTheme.typography.bodySmall) }
                            record.notes?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                        })
                    }
                }
            }
        }
    }
}

@Composable
private fun FishRecordMessage(padding: PaddingValues, title: String, message: String, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(padding).padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, modifier = Modifier.testTag(if (title.startsWith("找不到")) "fish-not-found" else "fish-records-error"), style = MaterialTheme.typography.titleLarge)
        Text(message, color = DetailTokens.SecondaryInk)
        TextButton(onClick = onBack) { Text("返回") }
    }
}

@Composable
private fun FishGrowthRecordsPage(fishId: String, onBack: () -> Unit, vm: FishViewModel) {
    val state by remember(fishId) { vm.recordsState(fishId) }.collectAsStateWithLifecycle()
    when (val s = state) {
        FishRecordsState.Loading -> Box(Modifier.fillMaxSize().background(DetailTokens.PageBackground), contentAlignment = Alignment.Center) { LoadingState("正在加载成长记录") }
        FishRecordsState.NotFound -> FishRecordMessage(PaddingValues(0.dp), "找不到这条鱼", "鱼只可能已被删除或链接已失效。", onBack)
        is FishRecordsState.Error -> FishRecordMessage(PaddingValues(0.dp), "成长记录加载失败", s.message, onBack)
        is FishRecordsState.Success -> FishGrowthContent(s.fish, s.growth, onBack, vm)
    }
}

@Composable
private fun FishGrowthContent(fish: Fish, records: List<GrowthRecord>, onBack: () -> Unit, vm: FishViewModel) {
    val photo by remember(fish.coverPhotoId) { vm.photo(fish.coverPhotoId) }.collectAsStateWithLifecycle()
    var range by remember { mutableStateOf(GrowthRange.All) }
    var showWeight by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<GrowthPoint?>(null) }
    var addOpen by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<GrowthRecord?>(null) }
    var deleting by remember { mutableStateOf<GrowthRecord?>(null) }
    var menuFor by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val points = remember(records, range, showWeight) { growthPoints(records, !showWeight, range, System.currentTimeMillis()) }
    val latest = points.lastOrNull()
    val first = points.firstOrNull()
    val delta = if (points.size >= 2 && latest != null && first != null) latest.value - first.value else null
    val grouped = remember(records) {
        records.sortedByDescending { it.measuredAt }.groupBy { SimpleDateFormat("yyyy 年 M 月", Locale.getDefault()).format(Date(it.measuredAt)) }
    }
    Scaffold(
        containerColor = DetailTokens.PageBackground,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        floatingActionButton = {
            SubpageFab("记录测量", Icons.Outlined.Add, onClick = { addOpen = true }, modifier = Modifier.navigationBarsPadding())
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 96.dp)) {
            item {
                Box(Modifier.fillMaxWidth().height(138.dp)) {
                    DetailHeroPhoto(photo = photo, modifier = Modifier.fillMaxSize())
                    HeroScrim()
                    HeroWaterEffects()
                    Row(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 12.dp, end = 12.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        HeroOrbButton(Icons.AutoMirrored.Outlined.ArrowBack, "返回", onBack)
                        Column(Modifier.weight(1f).padding(start = 10.dp)) {
                            Text("成长档案", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.testTag("fish-records-title"))
                            Text("${fish.name} · ${fish.species ?: "鱼种未记录"}", color = Color(0xFFD5E9F4), fontSize = 10.5.sp)
                        }
                        HeroStatusPill("入缸 ${daysBetweenLabel(fish.addedAt)}", Color(0xFF5FE0CD), Color(0x6620A79B), Color(0x885FE0CD))
                    }
                }
            }
            item {
                DeepWaterCard(Modifier.padding(horizontal = 16.dp).offset(y = (-24).dp)) {
                    Column(Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(if (showWeight) "体重趋势" else "体长趋势", color = Color(0xFFB9D8E8), fontSize = 12.sp)
                            Spacer(Modifier.weight(1f))
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                GrowthRange.entries.forEach { option ->
                                    Text(
                                        option.label,
                                        modifier = Modifier.clip(DetailTokens.PillShape)
                                            .background(if (range == option) Color(0x24FFFFFF) else Color.Transparent)
                                            .border(1.dp, if (range == option) Color(0x44FFFFFF) else Color.Transparent, DetailTokens.PillShape)
                                            .clickable { range = option }
                                            .padding(horizontal = 8.dp, vertical = 5.dp),
                                        color = if (range == option) Color.White else Color(0xFF91B7CB),
                                        fontSize = 9.5.sp,
                                    )
                                }
                            }
                        }
                        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 4.dp)) {
                            Text(latest?.value?.pretty() ?: "—", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                            Text(latest?.unit ?: if (showWeight) "g" else "cm", color = Color(0xFFA9CCDE), fontSize = 12.sp, modifier = Modifier.padding(start = 3.dp, bottom = 3.dp))
                            if (delta != null && delta != 0.0) {
                                Text("${if (delta > 0) "+" else ""}${delta.pretty()}", color = Color(0xFF8FEDDF), fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 7.dp, bottom = 3.dp))
                            }
                            Spacer(Modifier.weight(1f))
                            Column(horizontalAlignment = Alignment.End) {
                                Text("最近测量", color = Color(0xFF8FB8CE), fontSize = 9.sp)
                                Text(latest?.let { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(it.record.measuredAt)) } ?: "未记录", color = Color(0xFFE5F4FB), fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                        if (points.isEmpty()) {
                            Box(Modifier.fillMaxWidth().height(94.dp), contentAlignment = Alignment.Center) {
                                Text(if (showWeight) "还没有体重记录" else "还没有体长记录", color = Color(0xFF9BC3D7), fontSize = 11.sp)
                            }
                        } else {
                            GrowthChart(points, { selected = it }, Modifier.fillMaxWidth().height(126.dp), Color(0xFF5FE0CD))
                            if (points.size == 1) Text("仅 1 条测量记录，再记录一次即可形成趋势", color = Color(0xFF8FB8CE), fontSize = 9.sp, modifier = Modifier.padding(top = 2.dp))
                        }
                        selected?.let {
                            Text("${formatDateTime(it.record.measuredAt)} · ${it.value.pretty()} ${it.unit}", color = Color(0xFF8FEDDF), fontSize = 9.5.sp, modifier = Modifier.padding(top = 2.dp))
                        }
                        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            GrowthStat("测量次数", "${records.size}", "次")
                            GrowthStat("首次", first?.value?.pretty() ?: "—", first?.unit ?: "")
                            GrowthStat("变化", delta?.let { "${if (it > 0) "+" else ""}${it.pretty()}" } ?: "—", if (delta != null) latest?.unit.orEmpty() else "")
                            GrowthStat("最近", latest?.value?.pretty() ?: "—", latest?.unit ?: "")
                        }
                    }
                }
            }
            item {
                Row(Modifier.padding(horizontal = 16.dp).offset(y = (-10).dp).clip(DetailTokens.ButtonShape).background(Color(0xFFE9F1F7)).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(false to "体长", true to "体重").forEach { (weight, label) ->
                        Text(
                            label,
                            modifier = Modifier.weight(1f).clip(RoundedCornerShape(9.dp)).background(if (showWeight == weight) Color.White else Color.Transparent)
                                .clickable { showWeight = weight; selected = null }.padding(vertical = 7.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            color = if (showWeight == weight) DetailTokens.Primary else DetailTokens.SecondaryInk,
                            fontSize = 11.5.sp,
                            fontWeight = if (showWeight == weight) FontWeight.Bold else FontWeight.Normal,
                        )
                    }
                }
            }
            item { DetailSectionHeader("测量记录", countLabel = "${records.size} 条", modifier = Modifier.padding(horizontal = 16.dp)) }
            if (records.isEmpty()) {
                item { EmptyState("还没有成长记录", "记录第一次体长或体重后，这里会出现趋势和测量历史。") }
            }
            grouped.forEach { (month, items) ->
                item { Text(month, color = DetailTokens.TertiaryInk, fontSize = 10.5.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 18.dp, vertical = 3.dp)) }
                items(items.size) { index ->
                    val record = items[index]
                    GrowthRecordRow(
                        record,
                        vm,
                        onOpen = { editing = record },
                        onDelete = { deleting = record },
                        menuOpen = menuFor == record.id,
                        onMenu = { menuFor = if (menuFor == record.id) null else record.id },
                    )
                }
            }
        }
    }
    if (addOpen) GrowthRecordDialog(fish.id, null, vm, busy, { busy = it }, { error = it }, { addOpen = false }, { addOpen = false; busy = false; error = null })
    editing?.let { record -> GrowthRecordDialog(fish.id, record, vm, busy, { busy = it }, { error = it }, { editing = null; busy = false }, { editing = null; busy = false; error = null }) }
    deleting?.let { record ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("删除这条测量？") },
            text = { Text("删除后趋势与历史会按剩余真实记录重算，无法恢复。") },
            confirmButton = { TextButton(onClick = { vm.deleteGrowth(record.id, { deleting = null }, { error = it }) }) { Text("删除") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("取消") } },
        )
    }
    error?.let {
        AlertDialog(onDismissRequest = { error = null }, confirmButton = { TextButton(onClick = { error = null }) { Text("知道了") } }, title = { Text("操作失败") }, text = { Text(it) })
    }
}

@Composable
private fun GrowthStat(label: String, value: String, unit: String) {
    Column {
        Text(label, color = Color(0xFF8FB8CE), fontSize = 9.sp)
        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 1.dp)) {
            Text(value, color = Color(0xFFF4FBFF), fontSize = 13.sp, fontWeight = FontWeight.Bold)
            if (unit.isNotBlank()) Text(unit, color = Color(0xFF9BC3D7), fontSize = 8.5.sp, modifier = Modifier.padding(start = 2.dp, bottom = 1.dp))
        }
    }
}

@Composable
private fun GrowthRecordRow(record: GrowthRecord, vm: FishViewModel, onOpen: () -> Unit, onDelete: () -> Unit, menuOpen: Boolean, onMenu: () -> Unit) {
    val photo by remember(record.photoId) { vm.photo(record.photoId) }.collectAsStateWithLifecycle()
    val day = SimpleDateFormat("dd", Locale.getDefault()).format(Date(record.measuredAt))
    val month = SimpleDateFormat("M 月", Locale.getDefault()).format(Date(record.measuredAt))
    Box {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).clip(DetailTokens.SectionShape).background(Color.White)
                .border(1.dp, DetailTokens.GlassBorder, DetailTokens.SectionShape).clickable(onClick = onOpen).padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(Modifier.width(38.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(day, color = DetailTokens.TitleInk, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text(month, color = DetailTokens.TertiaryInk, fontSize = 8.5.sp)
            }
            Column(Modifier.weight(1f)) {
                Text(growthMeasurementSummary(record), color = DetailTokens.TitleInk, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                record.method?.takeIf { it.isNotBlank() }?.let { Text(it, color = DetailTokens.TertiaryInk, fontSize = 9.5.sp) }
                record.notes?.takeIf { it.isNotBlank() }?.let { Text(it, color = DetailTokens.TertiaryInk, fontSize = 9.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
            if (photo != null) ManagedPhotoImage(photo, true, Modifier.size(38.dp).clip(RoundedCornerShape(10.dp)))
            IconButton(onClick = onMenu, modifier = Modifier.size(30.dp)) { Icon(Icons.Outlined.MoreVert, contentDescription = "更多", tint = DetailTokens.TertiaryInk, modifier = Modifier.size(17.dp)) }
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = onMenu) {
            DropdownMenuItem(text = { Text("编辑") }, onClick = { onMenu(); onOpen() })
            DropdownMenuItem(text = { Text("删除", color = MaterialTheme.colorScheme.error) }, onClick = { onMenu(); onDelete() })
        }
    }
}

private enum class FishTimelineFilter(val label: String) { All("全部"), Growth("成长"), Feeding("喂食"), Status("状态") }

private data class FishTimelineEntry(
    val at: Long,
    val filter: FishTimelineFilter,
    val title: String,
    val detail: String,
    val photoId: String? = null,
    val amount: String? = null,
)

@Composable
private fun FishTimelineRecordsPage(fishId: String, onBack: () -> Unit, vm: FishViewModel) {
    val state by remember(fishId) { vm.recordsState(fishId) }.collectAsStateWithLifecycle()
    when (val s = state) {
        FishRecordsState.Loading -> Box(Modifier.fillMaxSize().background(DetailTokens.PageBackground), contentAlignment = Alignment.Center) { LoadingState("正在加载鱼只时间线") }
        FishRecordsState.NotFound -> FishRecordMessage(PaddingValues(0.dp), "找不到这条鱼", "鱼只可能已被删除或链接已失效。", onBack)
        is FishRecordsState.Error -> FishRecordMessage(PaddingValues(0.dp), "时间线加载失败", s.message, onBack)
        is FishRecordsState.Success -> FishTimelineContent(s.fish, s.events, s.growth, s.feedings, onBack, vm)
    }
}

@Composable
private fun FishTimelineContent(fish: Fish, events: List<FishEvent>, growth: List<GrowthRecord>, feedings: List<FeedingEvent>, onBack: () -> Unit, vm: FishViewModel) {
    val photo by remember(fish.coverPhotoId) { vm.photo(fish.coverPhotoId) }.collectAsStateWithLifecycle()
    var filter by remember { mutableStateOf(FishTimelineFilter.All) }
    val entries = remember(events, growth, feedings, fish.addedAt) {
        buildList {
            add(FishTimelineEntry(fish.addedAt, FishTimelineFilter.Status, "入缸 · 小苏的鱼缸", "从这一天开始计算入缸天数"))
            events.forEach { event ->
                add(FishTimelineEntry(event.occurredAt, FishTimelineFilter.Status, when (event.type) {
                    FishEventType.created -> "建立档案"
                    FishEventType.moved -> "转缸"
                    FishEventType.statusChanged -> event.status?.label() ?: "状态变更"
                    FishEventType.archived -> "归档"
                }, if (event.dateKnown) "状态事件" else "发生日期未知"))
            }
            growth.forEach { record -> add(FishTimelineEntry(record.measuredAt, FishTimelineFilter.Growth, "成长 · ${growthMeasurementSummary(record)}", record.method?.takeIf { it.isNotBlank() } ?: "真实测量", record.photoId)) }
            feedings.forEach { event -> add(FishTimelineEntry(event.fedAt, FishTimelineFilter.Feeding, "喂食 · ${event.foodType?.takeIf { it.isNotBlank() } ?: "饲料未记录"}", event.result?.takeIf { it.isNotBlank() } ?: "已记录", amount = event.amountValue?.let { "${it.pretty()} ${event.amountUnit.orEmpty()}" })) }
        }.sortedByDescending { it.at }
    }
    val visible = if (filter == FishTimelineFilter.All) entries else entries.filter { it.filter == filter }
    val grouped = visible.groupBy { SimpleDateFormat("yyyy 年 M 月", Locale.getDefault()).format(Date(it.at)) }
    val counts = FishTimelineFilter.entries.associateWith { f -> if (f == FishTimelineFilter.All) entries.size else entries.count { it.filter == f } }
    LazyColumn(Modifier.fillMaxSize().background(DetailTokens.PageBackground), contentPadding = PaddingValues(bottom = 30.dp)) {
        item {
            Box(Modifier.fillMaxWidth().height(188.dp)) {
                DetailHeroPhoto(photo = photo, modifier = Modifier.fillMaxSize())
                HeroScrim(); HeroWaterEffects()
                Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    HeroOrbButton(Icons.AutoMirrored.Outlined.ArrowBack, "返回", onBack)
                    Column(Modifier.padding(start = 10.dp)) {
                        Text("时间线", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.testTag("fish-records-title"))
                        Text("${fish.name} · 全部动态", color = Color(0xFFD5E9F4), fontSize = 10.5.sp)
                    }
                }
                Row(Modifier.fillMaxWidth().align(Alignment.BottomStart).padding(start = 16.dp, end = 16.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(52.dp).clip(RoundedCornerShape(15.dp)).border(1.dp, Color(0x80FFFFFF), RoundedCornerShape(15.dp))) { if (photo != null) ManagedPhotoImage(photo, true, Modifier.fillMaxSize()) }
                    Column(Modifier.weight(1f).padding(start = 9.dp)) {
                        Text(fish.name, color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                        Text("${fish.species ?: "鱼种未记录"} · 入缸 ${daysBetweenLabel(fish.addedAt)}", color = Color(0xFFCBE4F2), fontSize = 10.sp)
                    }
                    HeroStatusPill("${entries.size} 条记录", Color(0xFF5FE0CD), Color(0x6620A79B), Color(0x885FE0CD))
                }
            }
        }
        item {
            Row(Modifier.padding(horizontal = 16.dp).offset(y = (-14).dp).clip(DetailTokens.SectionShape).background(Color.White).border(1.dp, DetailTokens.GlassBorder, DetailTokens.SectionShape).padding(12.dp)) {
                GlassMetric("入缸", daysBetweenLabel(fish.addedAt).removeSuffix(" 天"), "天", Modifier.weight(1f))
                GlassMetric("记录", entries.size.toString(), "条", Modifier.weight(1f))
                GlassMetric("最近喂食", feedings.maxByOrNull { it.fedAt }?.let { shortMonthDay(it.fedAt) } ?: "—", null, Modifier.weight(1f))
            }
        }
        item {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FishTimelineFilter.entries.forEach { f -> FilterPill("${f.label} ${counts[f] ?: 0}", filter == f) { filter = f } }
            }
        }
        if (entries.isEmpty()) item { EmptyState("还没有动态", "记录成长、喂食或状态变化后，这里会形成真实时间线。") }
        grouped.forEach { (month, list) ->
            item { Text(month, color = DetailTokens.TertiaryInk, fontSize = 10.5.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 18.dp, vertical = 5.dp)) }
            items(list.size) { index -> FishTimelineRow(list[index], vm) }
        }
        item {
            Text("事件按实际发生时间排序；日期未知的记录会明确标注。", color = DetailTokens.TertiaryInk, fontSize = 9.5.sp, modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp))
        }
    }
}

@Composable
private fun FishTimelineRow(entry: FishTimelineEntry, vm: FishViewModel) {
    val photo by remember(entry.photoId) { vm.photo(entry.photoId) }.collectAsStateWithLifecycle()
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(IntrinsicSize.Min)) {
        Column(Modifier.width(42.dp).padding(top = 3.dp), horizontalAlignment = Alignment.End) {
            Text(SimpleDateFormat("MM/dd", Locale.getDefault()).format(Date(entry.at)), color = DetailTokens.SecondaryInk, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            Text(SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(entry.at)), color = DetailTokens.TertiaryInk, fontSize = 8.5.sp)
        }
        Box(Modifier.width(30.dp).fillMaxHeight().drawBehind {
            drawLine(Color(0xFFDCE9F3), Offset(size.width / 2f, 0f), Offset(size.width / 2f, size.height), 1.dp.toPx())
        }, contentAlignment = Alignment.TopCenter) {
            Box(Modifier.padding(top = 2.dp).size(25.dp).clip(RoundedCornerShape(8.dp)).background(if (entry.filter == FishTimelineFilter.Growth) Color(0xFFE8F1FC) else if (entry.filter == FishTimelineFilter.Feeding) Color(0xFFE4F8F4) else Color(0xFFF1F7FC)).border(1.dp, Color(0xFFDCE9F3), RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                Icon(if (entry.filter == FishTimelineFilter.Growth) Icons.Outlined.Edit else if (entry.filter == FishTimelineFilter.Feeding) Icons.Outlined.Add else Icons.Outlined.MoreVert, contentDescription = null, tint = if (entry.filter == FishTimelineFilter.Growth) DetailTokens.Primary else if (entry.filter == FishTimelineFilter.Feeding) Color(0xFF0E8E80) else DetailTokens.SecondaryInk, modifier = Modifier.size(13.dp))
            }
        }
        Column(Modifier.weight(1f).padding(bottom = 9.dp, start = 3.dp).clip(DetailTokens.SectionShape).background(Color.White).border(1.dp, DetailTokens.GlassBorder, DetailTokens.SectionShape).padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(entry.title, color = DetailTokens.TitleInk, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                entry.amount?.let { Text(it, color = DetailTokens.SecondaryInk, fontSize = 9.5.sp) }
            }
            if (entry.detail.isNotBlank()) Text(entry.detail, color = DetailTokens.TertiaryInk, fontSize = 9.5.sp, modifier = Modifier.padding(top = 2.dp))
            if (photo != null) ManagedPhotoImage(photo, true, Modifier.padding(top = 6.dp).size(44.dp).clip(RoundedCornerShape(10.dp)))
        }
    }
}

private fun daysBetweenLabel(addedAt: Long): String {
    val days = ((System.currentTimeMillis() - addedAt).coerceAtLeast(0L) / 86_400_000L).toInt()
    return "${days} 天"
}