package com.guanyji.app.feature.tank

import java.util.Locale
import kotlin.math.roundToInt

internal fun estimateTankCapacityLiters(lengthCm: Double, widthCm: Double, heightCm: Double): Double? =
    (lengthCm * widthCm * heightCm / 1000.0).takeIf { it.isFinite() && it > 0 }

fun calculateWaterChangeRatioPercent(volume: Double, volumeUnit: String, capacity: Double?, capacityUnit: String?): Double? {
    if (!volume.isFinite() || volume <= 0 || capacity == null || !capacity.isFinite() || capacity <= 0) return null
    val normalizedVolume = volume.toLitersOrNull(volumeUnit) ?: return null
    val normalizedCapacity = capacityUnit?.let { capacity.toLitersOrNull(it) } ?: return null
    return (normalizedVolume / normalizedCapacity * 100.0).takeIf { it.isFinite() && it in 0.0..100.0 }
}

fun formatWaterChangeRatio(value: Double): String {
    val rounded = (value * 10).roundToInt() / 10.0
    return if (rounded % 1.0 == 0.0) rounded.toInt().toString() else String.format(Locale.getDefault(), "%.1f", rounded)
}

private fun Double.toLitersOrNull(unit: String): Double? = when (unit.lowercase(Locale.ROOT)) {
    "l", "liter", "liters" -> this
    "gal", "gallon", "gallons" -> this * 3.785411784
    else -> null
}
