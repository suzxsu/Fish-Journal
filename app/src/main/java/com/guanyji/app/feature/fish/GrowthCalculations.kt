package com.guanyji.app.feature.fish

import com.guanyji.app.data.local.GrowthRecord
import java.util.concurrent.TimeUnit

enum class GrowthRange(val label: String, val days: Long?) {
    Days30("30 天", 30), Days90("90 天", 90), All("全部", null),
}

data class GrowthPoint(val record: GrowthRecord, val value: Double, val unit: String)

fun growthPoints(records: List<GrowthRecord>, length: Boolean, range: GrowthRange, now: Long): List<GrowthPoint> {
    val start = range.days?.let { now - TimeUnit.DAYS.toMillis(it) }
    return records.asSequence()
        .filter { start == null || it.measuredAt >= start }
        .mapNotNull { record ->
            val value = if (length) record.lengthValue else record.weightValue
            val unit = if (length) record.lengthUnit else record.weightUnit
            if (value == null || unit == null || !value.isFinite()) null else normalizeGrowthUnit(value, unit, length)?.let { (normalized, normalizedUnit) -> GrowthPoint(record, normalized, normalizedUnit) }
        }
        .sortedBy { it.record.measuredAt }
        .toList()
}

fun normalizeGrowthUnit(value: Double, unit: String, length: Boolean): Pair<Double, String>? = when {
    length && unit.equals("cm", true) -> value to "cm"
    length && unit.equals("mm", true) -> value / 10.0 to "cm"
    length && (unit.equals("in", true) || unit.equals("inch", true)) -> value * 2.54 to "cm"
    !length && unit.equals("g", true) -> value to "g"
    !length && (unit.equals("kg", true)) -> value * 1000.0 to "g"
    !length && unit.equals("mg", true) -> value / 1000.0 to "g"
    else -> null
}

fun growthMeasurementSummary(record: GrowthRecord): String = listOfNotNull(
    record.lengthValue?.let { "体长 ${it.prettyGrowth()} ${record.lengthUnit.orEmpty()}" },
    record.weightValue?.let { "体重 ${it.prettyGrowth()} ${record.weightUnit.orEmpty()}" },
).joinToString(" · ")

private fun Double.prettyGrowth() = if (this % 1.0 == 0.0) toLong().toString() else "%.2f".format(java.util.Locale.getDefault(), this).trimEnd('0').trimEnd('.')
