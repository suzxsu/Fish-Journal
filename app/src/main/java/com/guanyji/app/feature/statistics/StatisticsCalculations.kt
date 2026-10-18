package com.guanyji.app.feature.statistics

import com.guanyji.app.data.local.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class StatisticsInput(
    val tanks: List<Tank>,
    val fish: List<Fish>,
    val fishEvents: List<FishEvent>,
    val growth: List<GrowthRecord>,
    val feedings: List<FeedingEventWithTargets>,
    val waterTests: List<WaterTestWithValues>,
    val waterChanges: List<WaterChange>,
    val tasks: List<CareTask>,
    val careRecords: List<CareRecord>,
)

data class TrendPoint(val at: Long, val value: Double, val unit: String, val label: String)
data class StatisticsSummary(
    val activeFish: Int,
    val waterTestEvents: Int,
    val feedingEvents: Int,
    val feedingsPerWeek: Double,
    val waterChangeEvents: Int,
    val waterChangesPerWeek: Double,
    val waterChangeVolume: Double?,
    val waterChangeUnit: String?,
    val feedingTrend: List<TrendPoint>,
    val waterChangeTrend: List<TrendPoint>,
    val waterChangeVolumeTrend: List<TrendPoint>,
    val completedTasks: Int,
    val eligibleTasks: Int,
    val taskCompletionPercent: Int?,
    val waterParameters: Map<String, List<TrendPoint>>,
    val fishGrowth: Map<String, List<TrendPoint>>,
)

fun calculateStatistics(
    input: StatisticsInput,
    tankId: String?,
    from: LocalDate,
    through: LocalDate,
    zone: ZoneId,
): StatisticsSummary {
    require(!through.isBefore(from))
    val start = from.atStartOfDay(zone).toInstant().toEpochMilli()
    val end = through.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    val fishById = input.fish.associateBy { it.id }
    val fishAtEndRows = input.fish.filter { it.addedAt < end }.filter { fish -> tankAt(input, fish, end, tankId) }
    val selectedFishIds = fishAtEndRows.mapTo(mutableSetOf()) { it.id }
    val fishAtEnd = fishAtEndRows.size
    val inRangeFeedings = input.feedings.filter { row -> row.event.fedAt in start until end && (tankId == null || row.event.tankId == tankId) }
    val changes = input.waterChanges.filter { it.completedAt in start until end && (tankId == null || it.tankId == tankId) }
    val testsInRange = input.waterTests.filter { it.test.measuredAt in start until end && (tankId == null || it.test.tankId == tankId) }
    val normalizedChanges = changes.mapNotNull { change ->
        val pair = change.volumeValue?.let { value -> change.volumeUnit?.let { normalizeVolume(value, it) } }
        pair
    }
    val volumeUnit = normalizedChanges.firstOrNull()?.second?.let { normalizedChanges.all { p -> p.second == it }; it }
    val volume = if (volumeUnit == null) null else normalizedChanges.filter { it.second == volumeUnit }.sumOf { it.first }
    val weeks = ((through.toEpochDay() - from.toEpochDay() + 1) / 7.0).coerceAtLeast(1.0 / 7.0)
    val feedByDay = inRangeFeedings.groupBy { epochToDate(it.event.fedAt, zone) }
    val feedTrend = feedByDay.map { (date, rows) -> TrendPoint(date.atStartOfDay(zone).toInstant().toEpochMilli(), rows.size.toDouble(), "次", "喂食") }.sortedBy { it.at }
    val changesByDay = changes.groupBy { epochToDate(it.completedAt, zone) }
    val changeTrend = changesByDay.map { (date, rows) -> TrendPoint(date.atStartOfDay(zone).toInstant().toEpochMilli(), rows.size.toDouble(), "次", "换水") }.sortedBy { it.at }
    val volumeTrend = changesByDay.mapNotNull { (date, rows) ->
        val daily = rows.mapNotNull { row -> row.volumeValue?.let { value -> row.volumeUnit?.let { normalizeVolume(value, it) } } }
        if (daily.isEmpty()) null else TrendPoint(date.atStartOfDay(zone).toInstant().toEpochMilli(), daily.sumOf { it.first }, "L", "换水量")
    }.sortedBy { it.at }
    val tasks = input.tasks.filter { tankId == null || it.tankId == tankId || (it.tankId == null && it.fishId in selectedFishIds) }
    val completedIds = input.careRecords.asSequence().filter { it.completedAt in start until end }.mapNotNull { it.taskId }.toSet()
        .intersect(tasks.mapTo(mutableSetOf()) { it.id })
    val skippedIds = tasks.asSequence().filter { it.plannedAt in start until end && it.status == CareTaskStatus.skipped }.mapTo(mutableSetOf()) { it.id } - completedIds
    // Unrecorded planned dates are excluded; explicit skips and actual executions form the denominator.
    val completed = completedIds.size
    val explicitlyResolved = completedIds.size + skippedIds.size
    val parameters = input.waterTests.asSequence()
        .filter { tankId == null || it.test.tankId == tankId }
        .flatMap { test -> test.values.asSequence().filter { it.value?.isFinite() == true }.map { value -> (value.measuredAt ?: test.test.measuredAt) to value } }
        .filter { (at, _) -> at in start until end }
        .groupBy({ it.second.parameterCode }, { (at, value) -> TrendPoint(at, value.value!!, value.unit, value.parameterCode) })
        .mapValues { (_, points) -> points.sortedBy { it.at } }
    val growth = input.growth.asSequence()
        .filter { record ->
            val fish = fishById[record.fishId]
            fish != null && record.measuredAt in start until end && (tankId == null || tankAt(input, fish, record.measuredAt + 1, tankId))
        }
        .flatMap { record -> sequenceOf(
            record.lengthValue?.let { value -> record.lengthUnit?.let { normalizeLength(value, it)?.let { n -> "${record.fishId}_length" to TrendPoint(record.measuredAt, n.first, n.second, fishById[record.fishId]?.name ?: "鱼只") } } },
            record.weightValue?.let { value -> record.weightUnit?.let { normalizeWeight(value, it)?.let { n -> "${record.fishId}_weight" to TrendPoint(record.measuredAt, n.first, n.second, fishById[record.fishId]?.name ?: "鱼只") } } },
        ).filterNotNull() }
        .groupBy({ it.first }, { it.second })
        .mapValues { (_, points) -> points.sortedBy { it.at } }
    return StatisticsSummary(
        activeFish = fishAtEnd,
        waterTestEvents = testsInRange.size,
        feedingEvents = inRangeFeedings.size,
        feedingsPerWeek = inRangeFeedings.size / weeks,
        waterChangeEvents = changes.size,
        waterChangesPerWeek = changes.size / weeks,
        waterChangeVolume = volume,
        waterChangeUnit = volumeUnit,
        feedingTrend = feedTrend,
        waterChangeTrend = changeTrend,
        waterChangeVolumeTrend = volumeTrend,
        completedTasks = completed,
        eligibleTasks = explicitlyResolved,
        taskCompletionPercent = if (explicitlyResolved == 0) null else (completed * 100 / explicitlyResolved),
        waterParameters = parameters,
        fishGrowth = growth,
    )
}

private fun tankAt(input: StatisticsInput, fish: Fish, end: Long, selectedTank: String?): Boolean {
    val history = input.fishEvents.filter { it.fishId == fish.id && it.occurredAt < end }.sortedBy { it.occurredAt }
    var tank = history.firstOrNull { it.type == FishEventType.moved }?.tankId ?: fish.tankId
    var status = FishStatus.active
    history.forEach { event ->
        when (event.type) {
            FishEventType.moved -> if (event.targetTankId != null) tank = event.targetTankId
            FishEventType.statusChanged -> event.status?.let { status = it }
            FishEventType.archived -> status = FishStatus.archived
            FishEventType.created -> Unit
        }
    }
    if (history.none { it.type == FishEventType.statusChanged }) status = if (fish.statusChangedAt == null || fish.statusChangedAt < end) fish.status else FishStatus.active
    return (status == FishStatus.active || status == FishStatus.quarantine) && (selectedTank == null || tank == selectedTank)
}

fun normalizeLength(value: Double, unit: String): Pair<Double, String>? = when (unit.lowercase()) {
    "cm" -> value to "cm"
    "mm" -> value / 10.0 to "cm"
    "in", "inch" -> value * 2.54 to "cm"
    else -> null
}

fun normalizeVolume(value: Double, unit: String): Pair<Double, String>? = when (unit.lowercase()) {
    "l", "liter", "liters" -> value to "L"
    "ml" -> value / 1000.0 to "L"
    "gal", "gallon", "gallons" -> value * 3.785411784 to "L"
    else -> null
}

fun normalizeWeight(value: Double, unit: String): Pair<Double, String>? = when (unit.lowercase()) {
    "g" -> value to "g"
    "kg" -> value * 1000.0 to "g"
    "mg" -> value / 1000.0 to "g"
    else -> null
}

fun formatMeasurement(value: Double, unit: String, targetUnit: String? = null): Pair<Double, String> {
    val source = unit.lowercase()
    val target = targetUnit?.lowercase()
    return when {
        source in setOf("mm", "cm", "in", "inch") -> {
            val centimeters = when (source) { "mm" -> value / 10.0; "in", "inch" -> value * 2.54; else -> value }
            if (target in setOf("in", "inch")) centimeters / 2.54 to "in" else centimeters to "cm"
        }
        source in setOf("ml", "l", "liter", "liters", "gal", "gallon", "gallons") -> {
            val liters = when (source) { "ml" -> value / 1000.0; "gal", "gallon", "gallons" -> value * 3.785411784; else -> value }
            if (target in setOf("gal", "gallon", "gallons")) liters / 3.785411784 to "gal" else liters to "L"
        }
        source in setOf("c", "°c", "f", "°f") -> {
            val celsius = if (source in setOf("f", "°f")) (value - 32.0) * 5.0 / 9.0 else value
            if (target in setOf("f", "°f")) celsius * 9.0 / 5.0 + 32.0 to "°F" else celsius to "°C"
        }
        source in setOf("g", "kg", "mg") -> {
            val grams = when (source) { "kg" -> value * 1000.0; "mg" -> value / 1000.0; else -> value }
            if (target == "kg") grams / 1000.0 to "kg" else grams to "g"
        }
        else -> value to unit
    }
}

fun epochToDate(epochMillis: Long, zone: ZoneId): LocalDate = Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate()
