package com.guanyji.app.feature.statistics

import com.guanyji.app.data.local.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class StatisticsCalculationsTest {
    private val zone = ZoneOffset.UTC
    private val from = LocalDate.of(2025, 1, 1)
    private val to = LocalDate.of(2025, 1, 31)
    private val tank = Tank(id = "tank", name = "主缸", type = TankType.freshwater, photoId = null, capacityValue = 100.0, capacityUnit = "L", startedAt = null, notes = null, createdAt = 0, updatedAt = 0)
    private val fish = Fish(id = "fish", tankId = "tank", name = "小丑鱼", species = null, coverPhotoId = null, addedAt = 0, birthDate = null, status = FishStatus.active, statusChangedAt = null, notes = null, createdAt = 0, updatedAt = 0)

    @Test fun `counts unique local feeding events and converts water volume without modifying records`() {
        val time = from.atTime(10, 0).toInstant(zone).toEpochMilli()
        val feeding = FeedingEvent(id = "feed", tankId = "tank", fedAt = time, foodType = null, amountValue = null, amountUnit = null, result = null, notes = null)
        val change = WaterChange(id = "change", tankId = "tank", completedAt = time, volumeValue = 1.0, volumeUnit = "gal", ratio = null, nextPlannedAt = null, notes = null)
        val input = emptyInput().copy(
            tanks = listOf(tank), fish = listOf(fish),
            feedings = listOf(FeedingEventWithTargets(feeding, listOf("a", "b").map { FeedingTarget(feedingEventId = feeding.id, targetType = FeedingTargetType.fish, tankId = null, fishId = it, result = null) })),
            waterChanges = listOf(change),
        )
        val report = calculateStatistics(input, tank.id, from, to, zone)
        assertEquals(1, report.feedingEvents)
        assertEquals(3.785411784, report.waterChangeVolume!!, 0.000001)
        assertEquals("gal", change.volumeUnit)
        assertEquals(1, report.activeFish)
    }

    @Test fun `no planned task days do not become task failures`() {
        val report = calculateStatistics(emptyInput(), null, from, to, zone)
        assertEquals(0, report.eligibleTasks)
        assertNull(report.taskCompletionPercent)
    }

    @Test fun `task completion rate uses actual completion and explicit skips only`() {
        val start = from.atTime(9, 0).toInstant(zone).toEpochMilli()
        val completed = CareTask(id = "done", tankId = null, fishId = null, type = CareTaskType.custom, plannedAt = start, recurrenceRule = null, status = CareTaskStatus.completed, notes = null, createdAt = start, updatedAt = start)
        val skipped = completed.copy(id = "skip", plannedAt = start + 1000, status = CareTaskStatus.skipped)
        val pending = completed.copy(id = "pending", plannedAt = start + 2000, status = CareTaskStatus.pending)
        val records = listOf(CareRecord(id = "record", taskId = completed.id, tankId = null, fishId = null, type = CareRecordType.custom, completedAt = start + 10, payloadJson = null, notes = null))
        val report = calculateStatistics(emptyInput().copy(tasks = listOf(completed, skipped, pending), careRecords = records), null, from, to, zone)
        assertEquals(2, report.eligibleTasks)
        assertEquals(50, report.taskCompletionPercent)
    }

    @Test fun `water chart omits unknown values and historical edits recalculate`() {
        val measured = from.atTime(12, 0).toInstant(zone).toEpochMilli()
        val test = WaterTest(id = "water", tankId = tank.id, measuredAt = measured, method = null, notes = null)
        val recorded = WaterTestValue(id = "value", waterTestId = test.id, parameterCode = "temperature", value = 25.0, unit = "°C", targetMin = null, targetMax = null)
        val row = WaterTestWithValues(test, listOf(recorded, recorded.copy(id = "unknown", parameterCode = "pH", value = null)))
        val input = emptyInput().copy(tanks = listOf(tank), waterTests = listOf(row))
        val first = calculateStatistics(input, tank.id, from, to, zone)
        assertEquals(1, first.waterTestEvents)
        assertEquals(listOf(25.0), first.waterParameters.getValue("temperature").map { it.value })
        val edited = row.copy(values = listOf(recorded.copy(value = 27.5)))
        val updated = calculateStatistics(input.copy(waterTests = listOf(edited)), tank.id, from, to, zone)
        assertEquals(27.5, updated.waterParameters.getValue("temperature").single().value, 0.0)
    }

    @Test fun `water measurements use their own recorded date when present`() {
        val outside = from.minusDays(2).atStartOfDay(zone).toInstant().toEpochMilli()
        val current = from.atStartOfDay(zone).toInstant().toEpochMilli()
        val test = WaterTest(id = "water", tankId = tank.id, measuredAt = outside, method = null, notes = null)
        val value = WaterTestValue(id = "value", waterTestId = test.id, parameterCode = "pH", value = 8.0, unit = "pH", targetMin = null, targetMax = null, measuredAt = current)
        val report = calculateStatistics(emptyInput().copy(waterTests = listOf(WaterTestWithValues(test, listOf(value)))), tank.id, from, to, zone)
        assertEquals(current, report.waterParameters.getValue("pH").single().at)
    }

    @Test fun `unit conversion is display only and unknown unit remains explicit`() {
        assertEquals(1.0, normalizeLength(10.0, "mm")!!.first, 0.0)
        assertEquals(1.0, normalizeVolume(1000.0, "ml")!!.first, 0.0)
        assertEquals(1000.0, normalizeWeight(1.0, "kg")!!.first, 0.0)
        assertNull(normalizeLength(3.0, "furlong"))
        assertEquals(77.0, formatMeasurement(25.0, "°C", "°F").first, 0.000001)
        assertEquals(39.37007874, formatMeasurement(100.0, "cm", "in").first, 0.000001)
        assertEquals("kg", formatMeasurement(1000.0, "g", "kg").second)
    }

    private fun emptyInput() = StatisticsInput(emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
}
