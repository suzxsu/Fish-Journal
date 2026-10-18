package com.guanyji.app.feature.tank

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TankCalculationsTest {
    @Test fun estimatesLitersFromCentimetersAndRejectsInvalidDimensions() {
        assertEquals(120.0, estimateTankCapacityLiters(100.0, 40.0, 30.0)!!, 0.0)
        assertNull(estimateTankCapacityLiters(0.0, 40.0, 30.0))
        assertNull(estimateTankCapacityLiters(Double.POSITIVE_INFINITY, 40.0, 30.0))
    }

    @Test fun waterChangeRatioNormalizesUnitsAndLeavesUnknownOrImpossibleRatiosEmpty() {
        assertEquals(10.0, calculateWaterChangeRatioPercent(10.0, "L", 100.0, "L")!!, 0.0001)
        assertEquals(10.0, calculateWaterChangeRatioPercent(1.0, "gal", 37.85411784, "L")!!, 0.0001)
        assertNull(calculateWaterChangeRatioPercent(10.0, "L", null, null))
        assertNull(calculateWaterChangeRatioPercent(101.0, "L", 100.0, "L"))
    }

    @Test fun waterChangeRatioDisplayRoundsToOneDecimalAndDropsZeroFraction() {
        assertEquals("4", formatWaterChangeRatio(4.0))
        assertEquals("13.9", formatWaterChangeRatio(13.888888))
        assertEquals("15", formatWaterChangeRatio(15.04))
    }
}
