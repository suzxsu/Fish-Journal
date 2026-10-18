package com.guanyji.app.core.designsystem

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import com.guanyji.app.data.local.Photo
import com.guanyji.app.data.photo.ManagedPhotoFileStore

/**
 * 详情页共用视觉语言：一页水境。
 *
 * 深水卡承载“现在怎么样”，玻璃卡承载“变化与历史”，记录坞承载“现在就能做”。
 * 所有颜色为固定值，避免系统深色模式把深水卡与白卡对比打乱。
 */
object DetailTokens {
    val PageBackground = Color(0xFFF2F7FB)
    val DeepTop = Color(0xFF104A70)
    val DeepMid = Color(0xFF0A3554)
    val DeepBottom = Color(0xFF072943)
    val DeepBorder = Color(0x571FBFE0)
    val GlassBorder = Color(0xFFE3EEF7)
    val TitleInk = Color(0xFF102F47)
    val BodyInk = Color(0xFF274C66)
    val SecondaryInk = Color(0xFF5F7F96)
    val TertiaryInk = Color(0xFF8AA3B6)
    val Primary = Color(0xFF1D5FB0)
    val PrimaryLight = Color(0xFF2C7FD4)
    val Aqua = Color(0xFF19B7A6)
    val AquaBright = Color(0xFF5FE0CD)
    val AquaTint = Color(0xFFE4F8F4)
    val Sun = Color(0xFFE7C27C)
    val SunTint = Color(0xFFFCF3E2)
    val SunInk = Color(0xFFB77E2B)
    val WaterEvent = Color(0xFF17809A)
    val WaterEventTint = Color(0xFFE6F7FA)
    val BlueEventTint = Color(0xFFE8F1FC)
    val Danger = Color(0xFFD9584C)
    val HeroInk = Color(0xFFF4FBFF)
    val HeroSecondary = Color(0xFFCBE4F2)
    val DockBackground = Color(0xFFFBFDFF)
    val DockBorder = Color(0xFFE0ECF5)

    val SectionShape = RoundedCornerShape(18.dp)
    val DeepShape = RoundedCornerShape(21.dp)
    val ButtonShape = RoundedCornerShape(14.dp)
    val PillShape = RoundedCornerShape(999.dp)
    val DockHeight = 84.dp
}

/** 头图照片：优先真实照片；缺失时给水族占位，不伪造内容。 */
@Composable
fun DetailHeroPhoto(photo: Photo?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val file = remember(context, photo?.id, photo?.localPath) {
        photo?.let { ManagedPhotoFileStore(context).fileFor(it, false) }
    }
    if (file == null) {
        Box(
            modifier.background(Brush.linearGradient(listOf(Color(0xFF0C3A63), Color(0xFF0A2036)))),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("暂无照片", color = Color(0xB8CFE6F7), fontSize = 13.sp)
                Text("点右上角补充", color = Color(0x66CFE6F7), fontSize = 11.sp)
            }
        }
    } else {
        SubcomposeAsyncImage(
            model = file,
            contentDescription = "详情照片",
            contentScale = ContentScale.Crop,
            modifier = modifier,
            loading = {
                Box(Modifier.fillMaxSize().background(Color(0xFF0C3A63)), contentAlignment = Alignment.Center) {
                    Text("正在读取照片", color = Color(0xB8CFE6F7), fontSize = 12.sp)
                }
            },
            error = {
                Box(Modifier.fillMaxSize().background(Color(0xFF0C3A63)), contentAlignment = Alignment.Center) {
                    Text("照片缺失或无法读取", color = Color(0xB8CFE6F7), fontSize = 12.sp)
                }
            },
            success = { SubcomposeAsyncImageContent() },
        )
    }
}

/** 头图暗角：顶部保证返回按钮可读，底部让身份信息与深水卡自然过渡。 */
@Composable
fun HeroScrim(modifier: Modifier = Modifier) {
    Box(
        modifier.background(
            Brush.verticalGradient(
                0.0f to Color(0xB3041626),
                0.30f to Color(0x4D041626),
                0.62f to Color(0x33041626),
                0.86f to Color(0xCC041626),
                1.0f to Color(0xF2072943),
            ),
        ),
    )
}

/** 焦散光与气泡：只用低透明度绘制，不做真实模糊。 */
@Composable
fun HeroWaterEffects(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        rotate(-11f, pivot = Offset(size.width * 0.55f, 0f)) {
            val band = 9.dp.toPx()
            val gap = 54.dp.toPx()
            var x = -size.width * 0.6f
            while (x < size.width * 1.8f) {
                drawRect(
                    color = Color.White.copy(alpha = 0.035f),
                    topLeft = Offset(x, -size.height * 0.25f),
                    size = Size(band, size.height * 1.6f),
                )
                x += gap
            }
        }
        val bubbles = listOf(
            Triple(0.72f, 0.20f, 7f),
            Triple(0.80f, 0.15f, 4f),
            Triple(0.88f, 0.30f, 10f),
            Triple(0.66f, 0.28f, 3f),
            Triple(0.20f, 0.36f, 5f),
            Triple(0.13f, 0.22f, 4f),
        )
        bubbles.forEach { (x, y, d) ->
            val center = Offset(size.width * x, size.height * y)
            val radius = d.dp.toPx() / 2f
            drawCircle(Color.White.copy(alpha = 0.055f), radius, center)
            drawCircle(Color.White.copy(alpha = 0.32f), radius, center, style = Stroke(1.dp.toPx()))
        }
    }
}

@Composable
fun HeroOrbButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
) {
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(Color(0x6B072032))
            .border(1.dp, Color(0x57D6F3FF), CircleShape)
            .clickable(onClick = onClick)
            .semantics {
                contentDescription = description
                role = Role.Button
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(size * 0.48f))
    }
}

@Composable
fun HeroStatusPill(text: String, dotColor: Color, background: Color, borderColor: Color) {
    Row(
        Modifier
            .clip(DetailTokens.PillShape)
            .background(background)
            .border(1.dp, borderColor, DetailTokens.PillShape)
            .padding(horizontal = 9.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Box(Modifier.size(5.dp).clip(CircleShape).background(dotColor))
        Text(text, color = Color(0xFFEAFBF6), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun HeroGhostPill(text: String) {
    Text(
        text,
        modifier = Modifier
            .clip(DetailTokens.PillShape)
            .background(Color(0x57072032))
            .border(1.dp, Color(0x4DD6F3FF), DetailTokens.PillShape)
            .padding(horizontal = 9.dp, vertical = 4.dp),
        color = Color(0xFFD7EDF8),
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
    )
}

/** 区块标题：左侧水位条 + 标题 + 计数胶囊 + 右侧动作。 */
@Composable
fun DetailSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    countLabel: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    mutedAction: Boolean = false,
) {
    Row(
        modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier
                .width(4.dp)
                .height(17.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Brush.verticalGradient(listOf(DetailTokens.AquaBright, DetailTokens.Primary))),
        )
        Text(title, color = DetailTokens.TitleInk, fontSize = 15.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.2.sp)
        countLabel?.let {
            Text(
                it,
                modifier = Modifier
                    .clip(DetailTokens.PillShape)
                    .background(Color(0xFFE9F2F8))
                    .padding(horizontal = 7.dp, vertical = 2.dp),
                color = Color(0xFF4C7E9C),
                fontSize = 10.5.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(Modifier.weight(1f))
        if (actionLabel != null) {
            val labelColor = if (mutedAction || onAction == null) DetailTokens.TertiaryInk else DetailTokens.Primary
            Row(
                Modifier
                    .clip(DetailTokens.PillShape)
                    .then(if (onAction != null) Modifier.clickable(role = Role.Button, onClick = onAction) else Modifier)
                    .padding(horizontal = 4.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                Text(
                    actionLabel,
                    color = labelColor,
                    fontSize = 11.5.sp,
                    fontWeight = if (mutedAction || onAction == null) FontWeight.Normal else FontWeight.SemiBold,
                )
                if (!mutedAction && onAction != null) {
                    Icon(
                        com.guanyji.app.feature.overview.FishIcons.ArrowRight,
                        contentDescription = null,
                        tint = DetailTokens.Primary,
                        modifier = Modifier.size(12.dp),
                    )
                }
            }
        }
    }
}

/** 深水卡：每页最多一张，承载“现在怎么样”。 */
@Composable
fun DeepWaterCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier
            .clip(DetailTokens.DeepShape)
            .background(
                Brush.linearGradient(
                    0.0f to DetailTokens.DeepTop,
                    0.48f to DetailTokens.DeepMid,
                    1.0f to DetailTokens.DeepBottom,
                ),
            )
            .border(
                1.dp,
                Brush.linearGradient(listOf(DetailTokens.DeepBorder, Color(0x33FFFFFF))),
                DetailTokens.DeepShape,
            ),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val ring = Color(0x1A5FE0CD)
            val base = Offset(size.width * 0.92f, -size.height * 0.10f)
            drawCircle(ring, size.minDimension * 0.62f, base, style = Stroke(1.dp.toPx()))
            drawCircle(ring.copy(alpha = 0.06f), size.minDimension * 0.84f, base, style = Stroke(1.dp.toPx()))
            drawCircle(ring.copy(alpha = 0.04f), size.minDimension * 1.06f, base, style = Stroke(1.dp.toPx()))
            drawCircle(Color(0x66DBF7FF), 2.dp.toPx(), Offset(size.width * 0.91f, size.height * 0.78f), style = Stroke(1.dp.toPx()))
        }
        content()
    }
}

/** 玻璃卡：白底 + 冷描边 + 顶部高光，替代 Material 默认 ElevatedCard。 */
@Composable
fun DetailGlassCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = DetailTokens.SectionShape,
        color = Color.White,
        border = androidx.compose.foundation.BorderStroke(1.dp, DetailTokens.GlassBorder),
        shadowElevation = 6.dp,
    ) {
        Box(
            Modifier.background(
                Brush.verticalGradient(listOf(Color.White, Color(0xFFFCFEFF))),
            ),
        ) {
            content()
        }
    }
}

/** 水况仪表：整圈渐变只作仪表框，不表示百分比或安全区间。 */
@Composable
fun WaterGauge(
    valueText: String,
    unit: String,
    label: String,
    modifier: Modifier = Modifier,
    size: Dp = 110.dp,
) {
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 7.dp.toPx()
            val radius = this.size.minDimension / 2f - stroke
            val center = Offset(this.size.width / 2f, this.size.height / 2f)
            drawCircle(Color(0x22FFFFFF), radius, center, style = Stroke(stroke))
            drawCircle(
                brush = Brush.sweepGradient(
                    listOf(DetailTokens.AquaBright, Color(0xFF2BB9C8), Color(0xFF2E86DB), DetailTokens.AquaBright),
                ),
                radius = radius,
                center = center,
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
            repeat(12) { index ->
                val angle = Math.toRadians((index * 30 - 90).toDouble())
                val inner = radius - 8.dp.toPx()
                val outer = radius - 4.dp.toPx()
                drawLine(
                    color = Color(0x61DBF7FF),
                    start = Offset(
                        center.x + (inner * Math.cos(angle)).toFloat(),
                        center.y + (inner * Math.sin(angle)).toFloat(),
                    ),
                    end = Offset(
                        center.x + (outer * Math.cos(angle)).toFloat(),
                        center.y + (outer * Math.sin(angle)).toFloat(),
                    ),
                    strokeWidth = 1.2.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(valueText, color = DetailTokens.HeroInk, fontSize = 24.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp)
                Text(unit, color = Color(0xFFA9CCDE), fontSize = 11.sp, modifier = Modifier.padding(bottom = 3.dp, start = 1.dp))
            }
            Text(label, color = Color(0xFF9BC3D7), fontSize = 10.sp, letterSpacing = 0.4.sp)
        }
    }
}

/** 时间线一行：事件色图标 + 主副文案 + 右侧时间/数值。 */
@Composable
fun DetailTimelineRow(
    icon: ImageVector,
    tint: Color,
    iconBackground: Color,
    title: String,
    subtitle: String,
    trailing: String?,
    trailingSub: String?,
    modifier: Modifier = Modifier,
    showDivider: Boolean = true,
) {
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                Modifier.size(31.dp).clip(RoundedCornerShape(10.dp)).background(iconBackground).border(1.dp, tint.copy(alpha = 0.16f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(title, color = Color(0xFF1B3B53), fontSize = 12.5.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, color = DetailTokens.TertiaryInk, fontSize = 10.5.sp, lineHeight = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (trailing != null || trailingSub != null) {
                Column(horizontalAlignment = Alignment.End) {
                    trailing?.let { Text(it, color = Color(0xFF34566F), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1) }
                    trailingSub?.let { Text(it, color = Color(0xFF9AB0C1), fontSize = 9.5.sp, maxLines = 1) }
                }
            }
        }
        if (showDivider) Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFFEFF5FA)))
    }
}

data class DockAction(val label: String, val icon: ImageVector, val onClick: () -> Unit)

/** 底部记录坞：1 个主按钮 + 2 个次级胶囊，每项都是一步直达。 */
@Composable
fun DetailRecordDock(
    primaryText: String,
    primaryIcon: ImageVector,
    onPrimary: () -> Unit,
    secondary: List<DockAction>,
    modifier: Modifier = Modifier,
    primaryEnabled: Boolean = true,
) {
    Surface(color = DetailTokens.DockBackground, modifier = modifier.fillMaxWidth(), shadowElevation = 10.dp) {
        Column(Modifier.navigationBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 9.dp).height(48.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(DetailTokens.ButtonShape)
                        .background(
                            if (primaryEnabled) Brush.verticalGradient(listOf(DetailTokens.PrimaryLight, DetailTokens.Primary))
                            else Brush.verticalGradient(listOf(Color(0xFFB9CBD9), Color(0xFF9FB4C4))),
                        )
                        .clickable(enabled = primaryEnabled, role = Role.Button, onClick = onPrimary),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Icon(primaryIcon, contentDescription = null, tint = Color.White, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(primaryText, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                }
                secondary.forEach { action ->
                    Row(
                        Modifier
                            .fillMaxHeight()
                            .clip(DetailTokens.ButtonShape)
                            .background(Color.White)
                            .border(1.dp, Color(0xFFD9E8F3), DetailTokens.ButtonShape)
                            .clickable(role = Role.Button, onClick = action.onClick)
                            .padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(action.icon, contentDescription = null, tint = Color(0xFF3B607C), modifier = Modifier.size(14.dp))
                        Text(action.label, color = Color(0xFF3B607C), fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    }
                }
            }
        }
    }
}

/** 滚动后的紧凑标题栏；返回键始终可达。 */
@Composable
fun CompactDetailTopBar(    title: String,
    statusText: String,
    statusColor: Color,
    onBack: () -> Unit,
    onMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(color = Color(0xFFFAFDFF), shadowElevation = 3.dp, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.statusBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    Modifier.size(36.dp).clip(CircleShape).background(Color(0xFFF1F7FC)).border(1.dp, Color(0xFFDCE9F3), CircleShape)
                        .clickable(role = Role.Button, onClick = onBack),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回", tint = Color(0xFF2A4F69), modifier = Modifier.size(18.dp))
                }
                Text(title, color = DetailTokens.TitleInk, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(Modifier.size(5.dp).clip(CircleShape).background(statusColor))
                    Text(statusText, color = statusColor, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier.size(36.dp).clip(CircleShape).background(Color(0xFFF1F7FC)).border(1.dp, Color(0xFFDCE9F3), CircleShape)
                        .clickable(role = Role.Button, onClick = onMore),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Outlined.MoreVert, contentDescription = "更多操作", tint = Color(0xFF2A4F69), modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

/** 深水卡上的一格数字。 */
@Composable
fun DeepMetric(label: String, value: String, unit: String?, modifier: Modifier = Modifier, valueColor: Color = DetailTokens.HeroInk) {
    Column(modifier) {
        Text(label, color = Color(0xFF8FB8CE), fontSize = 10.sp, lineHeight = 13.sp, letterSpacing = 0.3.sp)
        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 2.dp)) {
            Text(value, color = valueColor, fontSize = 15.sp, lineHeight = 19.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            unit?.let {
                Text(it, color = Color(0xFFA9CCDE), fontSize = 10.sp, modifier = Modifier.padding(start = 2.dp, bottom = 2.dp))
            }
        }
    }
}

@Composable
fun GlassMetric(label: String, value: String, unit: String?, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, color = Color(0xFF7C96AA), fontSize = 10.sp, lineHeight = 13.sp, maxLines = 1)
        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 2.dp)) {
            Text(value, color = Color(0xFF163B57), fontSize = 15.5.sp, lineHeight = 19.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            unit?.let {
                Text(it, color = Color(0xFF7C96AA), fontSize = 10.sp, modifier = Modifier.padding(start = 2.dp, bottom = 2.dp))
            }
        }
    }
}

/** 二级页浅色顶栏：返回 + 标题 + 上下文 + 可选动作。 */
@Composable
fun SubpageTopBar(
    title: String,
    context: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    actionIcon: ImageVector? = null,
    actionDescription: String = "操作",
    onAction: (() -> Unit)? = null,
    titleModifier: Modifier = Modifier,
) {
    Surface(color = Color(0xFFFAFDFF), shadowElevation = 2.dp, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.statusBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().height(54.dp).padding(start = 8.dp, end = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                Box(
                    Modifier.size(36.dp).clip(CircleShape).background(Color(0xFFF1F7FC)).border(1.dp, Color(0xFFDCE9F3), CircleShape)
                        .clickable(role = Role.Button, onClick = onBack),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回", tint = Color(0xFF2A4F69), modifier = Modifier.size(18.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(title, color = DetailTokens.TitleInk, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = titleModifier)
                    Text(context, color = DetailTokens.TertiaryInk, fontSize = 10.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (actionIcon != null && onAction != null) {
                    Box(
                        Modifier.size(36.dp).clip(CircleShape).background(Color(0xFFF1F7FC)).border(1.dp, Color(0xFFDCE9F3), CircleShape)
                            .clickable(role = Role.Button, onClick = onAction),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(actionIcon, contentDescription = actionDescription, tint = Color(0xFF2A4F69), modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

/** 二级页搜索框：44dp、白底、无浮动 label。 */
@Composable
fun SubpageSearch(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = androidx.compose.ui.text.TextStyle(color = DetailTokens.TitleInk, fontSize = 12.5.sp),
        cursorBrush = androidx.compose.ui.graphics.SolidColor(DetailTokens.Primary),
        modifier = modifier,
        decorationBox = { inner ->
            Row(
                Modifier.fillMaxWidth().height(44.dp).clip(DetailTokens.ButtonShape).background(Color.White)
                    .border(1.dp, DetailTokens.GlassBorder, DetailTokens.ButtonShape).padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(androidx.compose.material.icons.Icons.Outlined.Search, contentDescription = null, tint = DetailTokens.TertiaryInk, modifier = Modifier.size(17.dp))
                Box(Modifier.weight(1f)) {
                    if (value.isBlank()) Text(placeholder, color = Color(0xFF8AA3B6), fontSize = 12.sp)
                    inner()
                }
                if (value.isNotBlank()) {
                    Text(
                        "清除",
                        color = DetailTokens.Primary,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clip(DetailTokens.PillShape).clickable(role = Role.Button) { onValueChange("") }.padding(horizontal = 4.dp, vertical = 2.dp),
                    )
                }
            }
        },
    )
}

@Composable
fun FilterPill(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Text(
        text,
        modifier = modifier
            .clip(DetailTokens.PillShape)
            .background(if (selected) DetailTokens.Primary else Color.White)
            .border(1.dp, if (selected) DetailTokens.Primary else Color(0xFFDDE8F1), DetailTokens.PillShape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        color = if (selected) Color.White else DetailTokens.SecondaryInk,
        fontSize = 11.5.sp,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        maxLines = 1,
    )
}

@Composable
fun SubpageFab(text: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .height(50.dp)
            .clip(DetailTokens.PillShape)
            .background(Brush.verticalGradient(listOf(DetailTokens.PrimaryLight, DetailTokens.Primary)))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
        Text(text, color = Color.White, fontSize = 13.5.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

