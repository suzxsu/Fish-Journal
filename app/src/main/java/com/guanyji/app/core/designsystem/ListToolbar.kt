@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.guanyji.app.core.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.request.ImageRequest
import com.guanyji.app.R
import com.guanyji.app.feature.overview.DashboardTokens

/**
 * 「我的鱼」「鱼缸」两个列表页共用的顶部结构。
 *
 * 页面自上而下只有三段固定高度：海报头部 128dp → 工具条 56dp（压住海报 22dp）→ 结果行 20dp，
 * 首屏到第一张卡片固定为 200dp，两个页面完全一致。搜索框、筛选按钮、结果/排序、已选条件
 * 都收敛到这里，页面里不再各自堆叠控件。
 */
object ListToolbarTokens {
    /** 海报头部总高（含状态栏）。 */
    val HeaderHeight = 128.dp
    /** 工具条高度。 */
    val ToolbarHeight = 56.dp
    /** 工具条压住海报的高度：头部 + 这个值 = 工具条实际占位。 */
    val ToolbarOverhang = 34.dp
    val ToolbarRadius = 18.dp
    val FieldRadius = 12.dp
    val ControlSize = 44.dp
    val ResultRowHeight = 20.dp

    val FieldBackground = Color(0xFFF1F6FA)
    val ToolbarBorder = Color(0xFFE6EEF5)
    val Divider = Color(0xFFE4EBF2)
    val PlaceholderInk = Color(0xFF7F94AC)
    val IconIdle = Color(0xFF5B7690)
    val ActiveTint = Color(0xFFE7F1FC)
    val ChipBorder = Color(0xFFDDE8F1)
    val ChipInk = Color(0xFF45607A)
    val SheetLabelInk = Color(0xFF8CA3B8)
    val OptionBackground = Color(0xFFF1F6FA)
}

/** 筛选面板里的一个可选项；[key] 为 null 表示「全部」。 */
data class FilterOption(val key: String?, val label: String)

/** 已生效的筛选条件，点击整颗胶囊即移除该项。 */
data class AppliedFilterChip(val label: String, val onRemove: () -> Unit)

/* ------------------------------- 图标 ------------------------------- */

/** 筛选（滑杆）。material-icons-core 里没有 Tune，按项目习惯自绘。 */
val FilterIcon: ImageVector by lazy {
    ImageVector.Builder("guanyji:list-filter", 24.dp, 24.dp, 24f, 24f).apply {
        path(stroke = SolidColor(Color.Black), strokeLineWidth = 1.9f, strokeLineCap = StrokeCap.Round) {
            moveTo(4f, 7f); horizontalLineTo(14f)
            moveTo(18f, 7f); horizontalLineTo(20f)
            moveTo(4f, 17f); horizontalLineTo(8f)
            moveTo(12f, 17f); horizontalLineTo(20f)
        }
        path(stroke = SolidColor(Color.Black), strokeLineWidth = 1.9f) {
            moveTo(16f, 4.8f)
            curveTo(17.2f, 4.8f, 18.2f, 5.8f, 18.2f, 7f)
            curveTo(18.2f, 8.2f, 17.2f, 9.2f, 16f, 9.2f)
            curveTo(14.8f, 9.2f, 13.8f, 8.2f, 13.8f, 7f)
            curveTo(13.8f, 5.8f, 14.8f, 4.8f, 16f, 4.8f)
            close()
            moveTo(10f, 14.8f)
            curveTo(11.2f, 14.8f, 12.2f, 15.8f, 12.2f, 17f)
            curveTo(12.2f, 18.2f, 11.2f, 19.2f, 10f, 19.2f)
            curveTo(8.8f, 19.2f, 7.8f, 18.2f, 7.8f, 17f)
            curveTo(7.8f, 15.8f, 8.8f, 14.8f, 10f, 14.8f)
            close()
        }
    }.build()
}

val CloseIcon: ImageVector by lazy {
    ImageVector.Builder("guanyji:list-close", 24.dp, 24.dp, 24f, 24f).apply {
        path(stroke = SolidColor(Color.Black), strokeLineWidth = 2.3f, strokeLineCap = StrokeCap.Round) {
            moveTo(6f, 6f); lineTo(18f, 18f)
            moveTo(18f, 6f); lineTo(6f, 18f)
        }
    }.build()
}

val ChevronDownIcon: ImageVector by lazy {
    ImageVector.Builder("guanyji:list-chevron", 24.dp, 24.dp, 24f, 24f).apply {
        path(stroke = SolidColor(Color.Black), strokeLineWidth = 2.4f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
            moveTo(6f, 9.5f); lineTo(12f, 15.5f); lineTo(18f, 9.5f)
        }
    }.build()
}

/* ------------------------------ 海报头部 ------------------------------ */

/**
 * 压缩后的海报头部：标题 + 计数胶囊 + 新增按钮占一行，副标题占一行。
 * 计数原先单独占一行（头部 152 / 186dp），现在并入标题行，头部固定 128dp。
 */
@Composable
fun AquariumListHeader(
    title: String,
    subtitle: String,
    countLabel: String?,
    icon: ImageVector,
    addContentDescription: String,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val statusBarInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    Box(modifier.fillMaxWidth().height(ListToolbarTokens.HeaderHeight)) {
        SubcomposeAsyncImage(
            model = ImageRequest.Builder(LocalContext.current).data(R.drawable.dashboard_header_reef).crossfade(240).build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alignment = Alignment.TopCenter,
            modifier = Modifier.matchParentSize(),
            loading = { HeaderFallback() },
            error = { HeaderFallback() },
            success = { SubcomposeAsyncImageContent() },
        )
        Box(
            Modifier.matchParentSize().background(
                Brush.verticalGradient(
                    0.00f to Color(0x94102A44),
                    0.42f to Color(0x4D102A44),
                    0.66f to Color(0x0D102A44),
                    0.88f to Color(0xCCF2F7FB),
                    1.00f to DashboardTokens.PageBackground,
                ),
            ),
        )
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 12.dp, top = statusBarInset + 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(8.dp))
            Row(
                Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(Modifier.weight(1f, fill = false)) {
                    Text(
                        text = title,
                        color = Color.White,
                        fontSize = 26.sp,
                        lineHeight = 32.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("list-header-title"),
                    )
                    Text(
                        text = subtitle,
                        color = Color(0xDEFFFFFF),
                        fontSize = 12.5.sp,
                        lineHeight = 17.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 1.dp),
                    )
                }
                if (countLabel != null) CountPill(countLabel)
            }
            Spacer(Modifier.width(6.dp))
            Box(
                Modifier
                    .size(ListToolbarTokens.ControlSize)
                    .clip(CircleShape)
                    .background(Color(0x2EFFFFFF))
                    .border(1.dp, Color(0x57FFFFFF), CircleShape)
                    .clickable(onClickLabel = addContentDescription, role = Role.Button, onClick = onAdd)
                    .semantics { contentDescription = addContentDescription },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = PlusIcon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun CountPill(label: String) {
    Text(
        text = label,
        color = Color(0xFFEAF4FB),
        fontSize = 11.sp,
        lineHeight = 14.sp,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Color(0x29FFFFFF))
            .border(1.dp, Color(0x57FFFFFF), RoundedCornerShape(999.dp))
            .padding(horizontal = 9.dp, vertical = 4.dp),
    )
}

@Composable
private fun HeaderFallback() {
    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(Color(0xFF0C3A63), Color(0xFF124E77))),
        ),
    )
}

/* ------------------------------ 工具条 ------------------------------ */

/**
 * 一体化工具条：左侧搜索（占满剩余宽度），右侧筛选按钮。
 * 排序不再是按钮，见 [ListResultRow]。
 */
@Composable
fun ListSearchToolbar(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    filterActive: Boolean,
    onFilterClick: () -> Unit,
    modifier: Modifier = Modifier,
    searchFieldModifier: Modifier = Modifier,
    filterDescription: String = if (filterActive) "筛选，已有条件生效" else "筛选",
) {
    SurfaceCard(modifier) {
        Row(
            Modifier.fillMaxSize().padding(6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = searchFieldModifier
                    .weight(1f)
                    .fillMaxSize()
                    .clip(RoundedCornerShape(ListToolbarTokens.FieldRadius))
                    .background(ListToolbarTokens.FieldBackground)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = SearchIcon,
                    contentDescription = null,
                    tint = ListToolbarTokens.PlaceholderInk,
                    modifier = Modifier.size(18.dp),
                )
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) {
                        Text(
                            text = placeholder,
                            color = ListToolbarTokens.PlaceholderInk,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = onQueryChange,
                        singleLine = true,
                        textStyle = TextStyle(
                            fontSize = 13.sp,
                            lineHeight = 17.sp,
                            color = DashboardTokens.TitleInk,
                        ),
                        cursorBrush = SolidColor(DashboardTokens.DeepBlue),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics { contentDescription = placeholder },
                    )
                }
                if (query.isNotEmpty()) {
                    Icon(
                        imageVector = CloseIcon,
                        contentDescription = "清除搜索",
                        tint = ListToolbarTokens.PlaceholderInk,
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .clickable(role = Role.Button) { onQueryChange("") },
                    )
                }
            }
            Box(Modifier.width(1.dp).height(22.dp).background(ListToolbarTokens.Divider))
            Box(
                Modifier
                    .size(ListToolbarTokens.ControlSize)
                    .clip(RoundedCornerShape(ListToolbarTokens.FieldRadius))
                    .background(if (filterActive) ListToolbarTokens.ActiveTint else Color.Transparent)
                    .clickable(onClickLabel = filterDescription, role = Role.Button, onClick = onFilterClick)
                    .semantics { contentDescription = filterDescription },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = FilterIcon,
                    contentDescription = null,
                    tint = if (filterActive) DashboardTokens.DeepBlue else ListToolbarTokens.IconIdle,
                    modifier = Modifier.size(20.dp),
                )
                if (filterActive) {
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 8.dp, end = 8.dp)
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFE4832F)),
                    )
                }
            }
        }
    }
}

@Composable
private fun SurfaceCard(modifier: Modifier, content: @Composable () -> Unit) {
    androidx.compose.material3.Surface(
        modifier = modifier.fillMaxWidth().height(ListToolbarTokens.ToolbarHeight),
        shape = RoundedCornerShape(ListToolbarTokens.ToolbarRadius),
        color = Color.White,
        shadowElevation = 6.dp,
        border = BorderStroke(1.dp, ListToolbarTokens.ToolbarBorder),
        content = content,
    )
}

/* ------------------------------ 结果行 ------------------------------ */

/**
 * 一行 20dp 的次级信息：左侧结果数量，右侧交给调用方（排序入口等）。
 * 排序从"按钮"降级成这行里的可点文字，省掉一个 48dp 按钮。
 */
@Composable
fun ListResultRow(
    countText: String,
    modifier: Modifier = Modifier,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .heightIn(min = ListToolbarTokens.ResultRowHeight),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = countText,
            color = DashboardTokens.SecondaryInk,
            fontSize = 12.5.sp,
            lineHeight = 16.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.testTag("list-result-count"),
        )
        trailing()
    }
}

/* ---------------------------- 已选条件 ---------------------------- */

/** 只在有筛选时出现：让"当前在看什么"始终可见，并且能逐个移除或一键清除。 */
@Composable
fun AppliedFilterRow(
    chips: List<AppliedFilterChip>,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        chips.forEach { chip ->
            Row(
                Modifier
                    .height(30.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color.White)
                    .border(1.dp, ListToolbarTokens.ChipBorder, RoundedCornerShape(999.dp))
                    .clickable(onClickLabel = "移除「${chip.label}」筛选", role = Role.Button) { chip.onRemove() }
                    .padding(start = 10.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = chip.label,
                    color = ListToolbarTokens.ChipInk,
                    fontSize = 12.5.sp,
                    maxLines = 1,
                )
                Icon(
                    imageVector = CloseIcon,
                    contentDescription = null,
                    tint = ListToolbarTokens.PlaceholderInk,
                    modifier = Modifier.size(11.dp),
                )
            }
        }
        Text(
            text = "清除",
            color = DashboardTokens.DeepBlue,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .clickable(onClickLabel = "清除全部筛选", role = Role.Button, onClick = onClearAll)
                .padding(horizontal = 8.dp, vertical = 6.dp),
        )
    }
}

/* ---------------------------- 筛选面板 ---------------------------- */

/**
 * 底部筛选面板：把原来横滚的 chip 行换成一次性可见的全部选项。
 * 顶部固定"筛选 + 重置"，底部固定"查看 N 条结果"。
 */
@Composable
fun ListFilterSheet(
    onDismiss: () -> Unit,
    onReset: () -> Unit,
    resultLabel: String,
    onApply: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color.White,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = {
            Box(
                Modifier
                    .padding(top = 10.dp, bottom = 8.dp)
                    .width(40.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(0xFFE0E8EF)),
            )
        },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                // 鱼种可能很多：面板内容可滚动，避免选项被屏幕高度截断。
                .verticalScroll(rememberScrollState()),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "筛选",
                    color = DashboardTokens.TitleInk,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "重置",
                    color = DashboardTokens.DeepBlue,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .clickable(role = Role.Button, onClick = onReset)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
            content()
            Spacer(Modifier.height(16.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(DashboardTokens.DeepBlue)
                    .clickable(role = Role.Button, onClick = onApply),
                contentAlignment = Alignment.Center,
            ) {
                Text(resultLabel, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FilterSection(
    label: String,
    options: List<FilterOption>,
    selectedKey: String?,
    onSelect: (String?) -> Unit,
) {
    if (options.isEmpty()) return
    Column(Modifier.fillMaxWidth().padding(top = 14.dp)) {
        Text(label, color = ListToolbarTokens.SheetLabelInk, fontSize = 12.5.sp)
        Spacer(Modifier.height(8.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            options.forEach { option ->
                val selected = option.key == selectedKey
                Box(
                    Modifier
                        .height(34.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(if (selected) DashboardTokens.DeepBlue else ListToolbarTokens.OptionBackground)
                        .border(
                            1.dp,
                            if (selected) DashboardTokens.DeepBlue else ListToolbarTokens.ToolbarBorder,
                            RoundedCornerShape(999.dp),
                        )
                        .clickable(role = Role.RadioButton) { onSelect(option.key) }
                        .semantics { this.selected = selected }
                        .padding(horizontal = 13.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = option.label,
                        color = if (selected) Color.White else ListToolbarTokens.ChipInk,
                        fontSize = 13.sp,
                        maxLines = 1,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    )
                }
            }
        }
    }
}

/* ------------------------------ 图标资源 ------------------------------ */

val SearchIcon: ImageVector by lazy {
    ImageVector.Builder("guanyji:list-search", 24.dp, 24.dp, 24f, 24f).apply {
        path(stroke = SolidColor(Color.Black), strokeLineWidth = 2f) {
            moveTo(17f, 10.5f)
            curveTo(17f, 14.1f, 14.1f, 17f, 10.5f, 17f)
            curveTo(6.9f, 17f, 4f, 14.1f, 4f, 10.5f)
            curveTo(4f, 6.9f, 6.9f, 4f, 10.5f, 4f)
            curveTo(14.1f, 4f, 17f, 6.9f, 17f, 10.5f)
            close()
            moveTo(15.4f, 15.4f); lineTo(20f, 20f)
        }
    }.build()
}

val PlusIcon: ImageVector by lazy {
    ImageVector.Builder("guanyji:list-plus", 24.dp, 24.dp, 24f, 24f).apply {
        path(stroke = SolidColor(Color.Black), strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round) {
            moveTo(12f, 5.5f); verticalLineTo(18.5f)
            moveTo(5.5f, 12f); horizontalLineTo(18.5f)
        }
    }.build()
}
