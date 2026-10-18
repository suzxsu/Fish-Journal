package com.guanyji.app.feature.overview

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * 总览页统一设计规范（颜色 / 圆角 / 间距 / 主题图标）。
 * 只服务于总览页，不改动 MaterialTheme 中的既有配色系统。
 */
internal object DashboardTokens {
    // 页面与卡片
    val PageBackground = Color(0xFFF2F7FB)
    val SurfaceCard = Color(0xFFFFFFFF)
    val CardBorder = Color(0xFFE6EEF5)
    val AquariumPanel = Color(0xFFF7FAFD)
    val Track = Color(0xFFEDF2F7)

    // 文字层级
    val TitleInk = Color(0xFF12324A)
    val SecondaryInk = Color(0xFF5B7690)
    val TertiaryInk = Color(0xFF8CA3B8)
    val OnHero = Color(0xFFFFFFFF)

    // 主题强调色
    val DeepBlue = Color(0xFF1D5FB0)
    val OceanBlue = Color(0xFF2E7FD4)
    val OceanTint = Color(0xFFE7F1FC)
    val AquaGreen = Color(0xFF2E9E8F)
    val AquaTint = Color(0xFFE2F5F0)
    val SunsetOrange = Color(0xFFE4832F)
    val SunsetTint = Color(0xFFFDEEE0)
    val CoralPurple = Color(0xFF7B5BD6)
    val CoralTint = Color(0xFFEFE9FC)
    val AlertRed = Color(0xFFD9584C)

    // 底部导航
    val NavSelection = Color(0xFFE3EBFB)
    val NavIdle = Color(0xFF7F94AC)

    // 圆角 / 间距
    val CardShape = RoundedCornerShape(20.dp)
    val InnerShape = RoundedCornerShape(12.dp)
    val BadgeShape = RoundedCornerShape(999.dp)
    val ScreenPadding = 16.dp
    val SectionGap = 16.dp
}

/**
 * 仅补充本项目 material-icons-core 中没有的水族主题图标。
 * 路径基于标准的 24dp 网格并使用描边，保证线条粗细一致。
 */
internal object FishIcons {
    val ArrowRight: ImageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight

    private fun key(name: String) = "guanyji:$name"

    val Aquarium: ImageVector by lazy {
        ImageVector.Builder(key("aquarium"), 24.dp, 24.dp, 24f, 24f).apply {
            path(stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
                moveTo(3.5f, 6.5f); horizontalLineTo(20.5f)
                moveTo(5.5f, 8.5f); verticalLineTo(18.5f)
                moveTo(18.5f, 8.5f); verticalLineTo(18.5f)
                moveTo(5.5f, 18.5f); horizontalLineTo(18.5f)
                moveTo(5.5f, 14.5f); horizontalLineTo(18.5f)
                moveTo(9f, 16.6f); verticalLineTo(14.4f)
                moveTo(13f, 16.6f); verticalLineTo(14.4f)
                moveTo(8.5f, 4f); verticalLineTo(6.5f)
                moveTo(15.5f, 4f); verticalLineTo(6.5f)
            }
        }.build()
    }

    val Fish: ImageVector by lazy {
        ImageVector.Builder(key("fish"), 24.dp, 24.dp, 24f, 24f).apply {
            path(fill = SolidColor(Color.Black)) {
                // 鱼身
                moveTo(14.0f, 5.0f)
                curveTo(17.3f, 5.0f, 19.9f, 7.6f, 19.9f, 12.0f)
                curveTo(19.9f, 16.4f, 17.3f, 19.0f, 14.0f, 19.0f)
                curveTo(11.6f, 19.0f, 9.4f, 15.9f, 9.4f, 12.0f)
                curveTo(9.4f, 8.1f, 11.6f, 5.0f, 14.0f, 5.0f)
                close()
                // 尾鳍
                moveTo(9.4f, 12.0f)
                curveTo(7.2f, 9.4f, 4.8f, 8.0f, 2.6f, 8.0f)
                curveTo(3.3f, 10.4f, 3.3f, 13.6f, 2.6f, 16.0f)
                curveTo(4.8f, 16.0f, 7.2f, 14.6f, 9.4f, 12.0f)
                close()
            }
            path(fill = SolidColor(Color.White)) {
                // 眼睛
                moveTo(16.8f, 9.6f)
                curveTo(16.8f, 8.4f, 17.7f, 7.5f, 18.9f, 7.5f)
                curveTo(20.1f, 7.5f, 21.0f, 8.4f, 21.0f, 9.6f)
                curveTo(21.0f, 10.8f, 20.1f, 11.7f, 18.9f, 11.7f)
                curveTo(17.7f, 11.7f, 16.8f, 10.8f, 16.8f, 9.6f)
                close()
            }
        }.build()
    }

    val Calendar: ImageVector by lazy {
        ImageVector.Builder(key("calendar"), 24.dp, 24.dp, 24f, 24f).apply {
            path(stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
                moveTo(4f, 6f); horizontalLineTo(20f)
                moveTo(5.5f, 6f); verticalLineTo(19.5f)
                moveTo(18.5f, 6f); verticalLineTo(19.5f)
                moveTo(4f, 19.5f); horizontalLineTo(20f)
                moveTo(4f, 10f); horizontalLineTo(20f)
                moveTo(8.5f, 3.5f); verticalLineTo(6f)
                moveTo(15.5f, 3.5f); verticalLineTo(6f)
            }
        }.build()
    }

    val Clock: ImageVector by lazy {
        ImageVector.Builder(key("clock"), 24.dp, 24.dp, 24f, 24f).apply {
            path(stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
                moveTo(12f, 3.5f)
                curveTo(16.7f, 3.5f, 20.5f, 7.3f, 20.5f, 12f)
                curveTo(20.5f, 16.7f, 16.7f, 20.5f, 12f, 20.5f)
                curveTo(7.3f, 20.5f, 3.5f, 16.7f, 3.5f, 12f)
                curveTo(3.5f, 7.3f, 7.3f, 3.5f, 12f, 3.5f)
                close()
                moveTo(12f, 7.5f)
                verticalLineTo(12.2f)
                lineTo(15.7f, 14.4f)
            }
        }.build()
    }

    /** 异常状态提示图标：让「隔离 / 已转出 / 已故 / 已归档」不只靠颜色区分。 */
    val Alert: ImageVector by lazy {
        ImageVector.Builder(key("alert"), 24.dp, 24.dp, 24f, 24f).apply {
            path(stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
                moveTo(12f, 4.2f)
                lineTo(21f, 19.6f)
                lineTo(3f, 19.6f)
                close()
                moveTo(12f, 10.2f)
                verticalLineTo(14.6f)
                moveTo(12f, 16.9f)
                verticalLineTo(17f)
            }
        }.build()
    }

    val Droplet: ImageVector by lazy {
        ImageVector.Builder(key("droplet"), 24.dp, 24.dp, 24f, 24f).apply {
            path(stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
                moveTo(12f, 3.2f)
                curveTo(15.4f, 7.4f, 18.4f, 10.9f, 18.4f, 14.4f)
                curveTo(18.4f, 18.1f, 15.6f, 20.8f, 12f, 20.8f)
                curveTo(8.4f, 20.8f, 5.6f, 18.1f, 5.6f, 14.4f)
                curveTo(5.6f, 10.9f, 8.6f, 7.4f, 12f, 3.2f)
                close()
            }
            path(stroke = SolidColor(Color.Black), strokeLineWidth = 1.6f, strokeLineCap = StrokeCap.Round) {
                moveTo(12f, 17.2f); curveTo(11.1f, 17.2f, 10.4f, 16.5f, 10.4f, 15.6f)
            }
        }.build()
    }

    val Eye: ImageVector by lazy {
        ImageVector.Builder(key("eye"), 24.dp, 24.dp, 24f, 24f).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(12f, 4.5f)
                curveTo(6.9f, 4.5f, 2.7f, 7.8f, 1f, 12f)
                curveTo(2.7f, 16.2f, 6.9f, 19.5f, 12f, 19.5f)
                curveTo(17.1f, 19.5f, 21.3f, 16.2f, 23f, 12f)
                curveTo(21.3f, 7.8f, 17.1f, 4.5f, 12f, 4.5f)
                close()
                moveTo(12f, 17f)
                curveTo(9.2f, 17f, 7f, 14.8f, 7f, 12f)
                curveTo(7f, 9.2f, 9.2f, 7f, 12f, 7f)
                curveTo(14.8f, 7f, 17f, 9.2f, 17f, 12f)
                curveTo(17f, 14.8f, 14.8f, 17f, 12f, 17f)
                close()
                moveTo(12f, 14.5f)
                curveTo(10.6f, 14.5f, 9.5f, 13.4f, 9.5f, 12f)
                curveTo(9.5f, 10.6f, 10.6f, 9.5f, 12f, 9.5f)
                curveTo(13.4f, 9.5f, 14.5f, 10.6f, 14.5f, 12f)
                curveTo(14.5f, 13.4f, 13.4f, 14.5f, 12f, 14.5f)
                close()
            }
        }.build()
    }

    val Tag: ImageVector by lazy {
        ImageVector.Builder(key("tag"), 24.dp, 24.dp, 24f, 24f).apply {
            path(fill = SolidColor(Color.Black)) {
                // 标签主体（圆角菱形，顺时针旋转 45°）
                moveTo(11.3f, 2.7f)
                curveTo(11.7f, 2.3f, 12.3f, 2.3f, 12.7f, 2.7f)
                lineTo(21.3f, 11.3f)
                curveTo(21.7f, 11.7f, 21.7f, 12.3f, 21.3f, 12.7f)
                lineTo(12.7f, 21.3f)
                curveTo(12.3f, 21.7f, 11.7f, 21.7f, 11.3f, 21.3f)
                lineTo(2.7f, 12.7f)
                curveTo(2.3f, 12.3f, 2.3f, 11.7f, 2.7f, 11.3f)
                close()
            }
            path(fill = SolidColor(Color.White)) {
                // 标签孔
                moveTo(10.6f, 7.1f)
                curveTo(10.6f, 6.3f, 11.2f, 5.7f, 12.0f, 5.7f)
                curveTo(12.8f, 5.7f, 13.4f, 6.3f, 13.4f, 7.1f)
                curveTo(13.4f, 7.9f, 12.8f, 8.5f, 12.0f, 8.5f)
                curveTo(11.2f, 8.5f, 10.6f, 7.9f, 10.6f, 7.1f)
                close()
            }
        }.build()
    }

    /** 详情页「照片」快捷动作使用的相机图标。 */
    val Camera: ImageVector by lazy {
        ImageVector.Builder(key("camera"), 24.dp, 24.dp, 24f, 24f).apply {
            path(stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
                moveTo(3.6f, 8.2f); horizontalLineTo(6.7f)
                lineTo(8.2f, 6f); horizontalLineTo(15.8f)
                lineTo(17.3f, 8.2f); horizontalLineTo(20.4f)
                verticalLineTo(18.6f); horizontalLineTo(3.6f); close()
                moveTo(12f, 10.1f)
                curveTo(14.3f, 10.1f, 16.1f, 11.9f, 16.1f, 14.2f)
                curveTo(16.1f, 16.5f, 14.3f, 18.3f, 12f, 18.3f)
                curveTo(9.7f, 18.3f, 7.9f, 16.5f, 7.9f, 14.2f)
                curveTo(7.9f, 11.9f, 9.7f, 10.1f, 12f, 10.1f)
                close()
            }
        }.build()
    }

    val WaterLevel: ImageVector by lazy {
        ImageVector.Builder(key("waterLevel"), 24.dp, 24.dp, 24f, 24f).apply {
            path(stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
                moveTo(3f, 8.2f); curveTo(4.6f, 6.6f, 6.2f, 6.6f, 7.8f, 8.2f)
                curveTo(9.4f, 9.8f, 11f, 9.8f, 12.6f, 8.2f)
                curveTo(14.2f, 6.6f, 15.8f, 6.6f, 17.4f, 8.2f)
                curveTo(19f, 9.8f, 20.6f, 9.8f, 22.2f, 8.2f)
                moveTo(2f, 14.4f); curveTo(3.6f, 12.8f, 5.2f, 12.8f, 6.8f, 14.4f)
                curveTo(8.4f, 16f, 10f, 16f, 11.6f, 14.4f)
                curveTo(13.2f, 12.8f, 14.8f, 12.8f, 16.4f, 14.4f)
                curveTo(18f, 16f, 19.6f, 16f, 21.2f, 14.4f)
                moveTo(1f, 20.2f); curveTo(2.6f, 18.6f, 4.2f, 18.6f, 5.8f, 20.2f)
                curveTo(7.4f, 21.8f, 9f, 21.8f, 10.6f, 20.2f)
                curveTo(12.2f, 18.6f, 13.8f, 18.6f, 15.4f, 20.2f)
                curveTo(17f, 21.8f, 18.6f, 21.8f, 20.2f, 20.2f)
            }
        }.build()
    }
    /** 体长 / 长度记录使用的量尺图标（本项目 material-icons-core 中缺失）。 */
    val Ruler: ImageVector by lazy {
        ImageVector.Builder(key("ruler"), 24.dp, 24.dp, 24f, 24f).apply {
            path(stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
                moveTo(2.6f, 9.2f); horizontalLineTo(21.4f)
                moveTo(21.4f, 9.2f); verticalLineTo(14.8f)
                moveTo(21.4f, 14.8f); horizontalLineTo(2.6f)
                moveTo(2.6f, 14.8f); verticalLineTo(9.2f)
                moveTo(6.9f, 9.2f); verticalLineTo(11.9f)
                moveTo(11.4f, 9.2f); verticalLineTo(13.2f)
                moveTo(15.9f, 9.2f); verticalLineTo(11.9f)
            }
        }.build()
    }
}
